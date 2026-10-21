package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.distanceSq
import de.bjrke.factorio.optimizer.common.util.connectedComponents
import de.bjrke.test.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Invariants of the power and fluid networks: pole coverage and wiring, pipe connectivity. */
private const val SUPPLY_RADIUS = 3
private const val WIRE_REACH = 9
private const val WIRE_REACH_SQ = WIRE_REACH * WIRE_REACH
private const val PIPE_REACH = 10

/** Every machine tile must sit in a pole's supply area, and the poles must form one network. */
fun assertPolesCoverAndConnect(entities: List<BlueprintEntity>) {
    val poles =
        entities
            .asSequence()
            .filter { it.name == "medium-electric-pole" }
            .map { it.area.topLeft }
            .toList()
    val coveredByPoles = poles.flatMapTo(mutableSetOf()) { it.area().grow(SUPPLY_RADIUS) }
    val unpowered = entities.filter {
        it.isPowered() && (it.area intersect coveredByPoles).isEmpty()
    }
    val components =
        connectedComponents(poles) { pole -> poles.filter { it.distanceSq(pole) <= WIRE_REACH_SQ } }

    assertAll(
        { assertEquals(emptyList(), unpowered) },
        { assertTrue(components.size <= 1, "${components.size} separate power networks") },
    )
}

/**
 * Every fluid's pipe tiles must form one network. A pipe-to-ground only joins on the side its
 * surface pipe attaches on (plus its buried partner), so a run that turned on an end would leave
 * part of the fluid unreachable.
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

    return plumbing.values
        .groupBy { it.material }
        .flatMap { (material, pipes) ->
            val parts = pipes.map { componentOf.getValue(it.area.topLeft) }.distinct()
            if (parts.size <= 1) emptyList()
            else listOf("${material.name} is split over ${parts.size} networks")
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

private fun plainPipeLinks(tile: Coordinate, plumbing: Map<Coordinate, Plumbing>): Set<Coordinate> =
    Direction.entries.mapNotNullTo(mutableSetOf()) { direction ->
        (tile + direction.step).takeIf { it in plumbing }
    }

private fun groundPipeLinks(
    pipe: PipeToGroundEntity,
    plumbing: Map<Coordinate, Plumbing>,
): Set<Coordinate> {
    val result = mutableSetOf<Coordinate>()
    val mouth = mouthOf(pipe)
    val front = pipe.area.topLeft + mouth.step
    val neighbour = plumbing[front]
    val connects =
        neighbour is PipeEntity ||
            neighbour is InfinityPipeEntity ||
            (neighbour is PipeToGroundEntity && mouthOf(neighbour) == mouth.opposite())
    if (connects) result += front
    for (distance in 2..PIPE_REACH) {
        val toward = if (pipe.io == Io.INPUT) pipe.direction.step else pipe.direction.step * -1
        val other = plumbing[pipe.area.topLeft + toward * distance]
        if (other !is PipeToGroundEntity) continue
        val opposite =
            other.material == pipe.material &&
                other.direction == pipe.direction &&
                other.io != pipe.io
        if (!opposite) continue
        result += other.area.topLeft
        break
    }
    return result
}

private fun mouthOf(pipe: PipeToGroundEntity) =
    if (pipe.io == Io.INPUT) pipe.direction.opposite() else pipe.direction

/** A blueprint does not rebuild pole wires, so each pole must list the poles it is wired to. */
fun assertPolesWired(entities: List<BlueprintEntity>) {
    val poles = entities.withIndex().filter { it.value.name == "medium-electric-pole" }
    val numbers = poles.map { it.index + 1 }.toSet()
    val seen = mutableSetOf<Int>()
    var components = 0
    for ((index) in poles) {
        val number = index + 1
        if (!seen.add(number)) continue
        components++
        val queue = ArrayDeque(listOf(number))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (neighbour in entities[current - 1].neighbours) {
                if (neighbour in numbers && seen.add(neighbour)) queue += neighbour
            }
        }
    }
    assertAll(
        {
            assertTrue(
                poles.size < 2 || poles.all { it.value.neighbours.isNotEmpty() },
                "unwired poles",
            )
        },
        { assertTrue(components <= 1, "$components pole wire networks") },
    )
}
