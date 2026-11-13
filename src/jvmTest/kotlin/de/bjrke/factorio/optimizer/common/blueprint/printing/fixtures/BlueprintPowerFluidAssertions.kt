package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area
import de.bjrke.factorio.optimizer.common.core.model.distanceSq
import de.bjrke.factorio.optimizer.common.core.model.neighbors
import de.bjrke.factorio.optimizer.common.core.model.west
import de.bjrke.factorio.optimizer.common.core.util.connectedComponents
import de.bjrke.factorio.optimizer.common.core.util.drain
import de.bjrke.test.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Invariants of the power and fluid networks: pole coverage and wiring, pipe connectivity. */
private const val PIPE_REACH = 10

/** Every machine tile must sit in a pole's supply area, and the poles must form one network. */
fun assertPolesCoverAndConnect(
    entities: List<BlueprintEntity>,
    pole: PowerPole = PowerPole.MEDIUM_ELECTRIC_POLE,
) {
    val poles =
        entities.asSequence().filter { it.name == pole.identifier }.map { it.area.topLeft }.toList()
    val coveredByPoles = poles.flatMapTo(mutableSetOf()) { it.area().grow(pole.supplyRadius) }
    val unpowered = entities.filter {
        it.isPowered() && (it.area intersect coveredByPoles).isEmpty()
    }
    val reachSq = pole.wireReach * pole.wireReach
    val components =
        connectedComponents(poles) { center -> poles.filter { it.distanceSq(center) <= reachSq } }

    assertAll(
        { assertEquals(emptyList(), unpowered) },
        { assertTrue(components.size <= 1, "${components.size} separate power networks") },
    )
}

/**
 * Every fluid's pipe tiles must be reachable from one of its sources. A pipe-to-ground only joins
 * on the side its surface pipe attaches on (plus its buried partner), so a run that turned on an
 * end would leave part of the fluid unreachable. A fluid may span more than one network — a plan
 * that feeds it to two distant rows lays a lane per row — but every network needs its own source.
 */
fun fluidNetworkProblems(entities: List<LayoutEntity>): List<String> {
    val plumbing = entities.filterIsInstance<Plumbing>().associateBy { it.area.topLeft }

    // only mutual links count: a pipe beside a pipe-to-ground's flank does not connect
    val adjacency =
        plumbing.keys.associateWith { tile ->
            pipeLinks(tile, plumbing).filter { pipeLinks(it, plumbing).contains(tile) }.toSet()
        }
    val components = connectedComponents(plumbing.keys) { adjacency.getValue(it) }
    val componentOf =
        components.withIndex().flatMap { (index, tiles) -> tiles.map { it to index } }.toMap()
    val sources = fluidSources(entities)

    return plumbing.values
        .groupBy { it.material }
        .flatMap { (material, pipes) ->
            val parts = pipes.map { componentOf.getValue(it.area.topLeft) }.distinct()
            val supplied = parts.count { part ->
                components[part].any { tile -> (tile to material) in sources }
            }
            if (supplied == parts.size) emptyList()
            else listOf("${material.name} has ${parts.size - supplied} network(s) without a source")
        }
}

/**
 * The tiles that supply a fluid: a cheat infinity pipe with settings, or the input end of the
 * source tunnel next to a fluid signal marker. A marker stands one tile west of its buried input
 * run; only that run's [Io.INPUT] end is a source, because the far end joins the lane.
 */
private fun fluidSources(entities: List<LayoutEntity>): Set<Pair<Coordinate, Material>> = buildSet {
    entities
        .filterIsInstance<InfinityPipeEntity>()
        .filter { it.settings != null }
        .forEach {
            add(it.area.topLeft to it.material)
        }
    val inputEnds =
        entities
            .filterIsInstance<PipeToGroundEntity>()
            .filter { it.io == Io.INPUT }
            .associateBy { it.area.topLeft }
    entities
        .filterIsInstance<MarkerEntity>()
        .filter { it.signal.fluid }
        .forEach { marker ->
            val source = inputEnds[marker.area.topLeft.west()] ?: return@forEach
            if (source.material == marker.signal.material) {
                add(source.area.topLeft to source.material)
            }
        }
}

private fun pipeLinks(tile: Coordinate, plumbing: Map<Coordinate, Plumbing>): Set<Coordinate> {
    val entity = plumbing[tile] ?: return emptySet()
    return when (entity) {
        is PipeEntity,
        is InfinityPipeEntity -> plainPipeLinks(tile, plumbing)
        is PipeToGroundEntity -> groundPipeLinks(entity, plumbing)
    }
}

private fun plainPipeLinks(tile: Coordinate, plumbing: Map<Coordinate, Plumbing>) =
    tile.neighbors.filterTo(mutableSetOf()) { it in plumbing }

private fun groundPipeLinks(
    pipe: PipeToGroundEntity,
    plumbing: Map<Coordinate, Plumbing>,
) = buildSet {
    val mouth = mouthOf(pipe)
    val front = pipe.area.topLeft + mouth.step
    val neighbour = plumbing[front]
    val connects =
        neighbour is PipeEntity ||
            neighbour is InfinityPipeEntity ||
            (neighbour is PipeToGroundEntity && mouthOf(neighbour) == mouth.opposite())
    if (connects) add(front)
    // a pipe-to-ground may surface in the next tile; the buried tile is the walkway
    for (distance in 1..PIPE_REACH) {
        val toward = if (pipe.io == Io.INPUT) pipe.direction.step else pipe.direction.step * -1
        val other = plumbing[pipe.area.topLeft + toward * distance]
        if (other !is PipeToGroundEntity) continue
        val opposite =
            other.material == pipe.material &&
                other.direction == pipe.direction &&
                other.io != pipe.io
        if (opposite) {
            add(other.area.topLeft)
            break
        }
    }
}

private fun mouthOf(pipe: PipeToGroundEntity) =
    if (pipe.io == Io.INPUT) pipe.direction.opposite() else pipe.direction

/** A blueprint does not rebuild pole wires, so each pole must list the poles it is wired to. */
fun assertPolesWired(
    entities: List<BlueprintEntity>,
    pole: PowerPole = PowerPole.MEDIUM_ELECTRIC_POLE,
) {
    val poles = entities.withIndex().filter { it.value.name == pole.identifier }
    val numbers = poles.map { it.index + 1 }.toSet()
    val seen = mutableSetOf<Int>()
    var components = 0
    for ((index) in poles) {
        val number = index + 1
        if (!seen.add(number)) continue
        components++

        ArrayDeque(listOf(number)).drain { current ->
            for (neighbour in entities[current - 1].neighbours) {
                if (neighbour in numbers && seen.add(neighbour)) addLast(neighbour)
            }
        }
    }
    assertAll(
        {
            if (poles.size >= 2) {
                assertEquals(
                    emptyList(),
                    poles.filter { it.value.neighbours.isEmpty() },
                    "unwired poles",
                )
            }
        },
        { assertTrue(components <= 1, "$components pole wire networks") },
    )
}
