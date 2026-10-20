package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import kotlin.test.*

/** Invariants of the power and fluid networks: pole coverage and wiring, pipe connectivity. */

/** Every machine tile must sit in a pole's supply area, and the poles must form one network. */
fun assertPolesCoverAndConnect(entities: List<BlueprintEntity>) {
    val poles =
        entities.asSequence().filter { it.name == "medium-electric-pole" }.map { it.area.topLeft }

    val coveredByPoles =
        poles.flatMapTo(mutableSetOf()) { p ->
            p.area().grow(3)
        }

    val unpowered = entities.filter {
        it.isPowered() && (it.area intersect coveredByPoles).isEmpty()
    }

    val seen = mutableSetOf<Coordinate>()
    var components = 0
    for (pole in poles) {
        if (!seen.add(pole)) continue
        components++
        val queue = ArrayDeque<Coordinate>()
        var current: Coordinate? = pole
        while (current != null) {
            for (other in poles) {
                if (other !in seen) {
                    if (current.distanceSq(other) <= 9 * 9) {
                        seen += other
                        queue += other
                    }
                }
            }
            current = queue.removeFirstOrNull()
        }
    }

    assertAll(
        { assertEquals(emptyList(), unpowered) },
        { assertTrue(components <= 1, "$components separate power networks") },
    )
}

/**
 * Every fluid's pipe tiles must form one network. A pipe-to-ground only joins on the side its
 * surface pipe attaches on (plus its buried partner), so a run that turned on an end would leave
 * part of the fluid unreachable.
 */
fun fluidNetworkProblems(entities: List<LayoutEntity>): List<String> {
    val plumbing = entities.filterIsInstance<Plumbing>().associateBy { it.area.topLeft }
    val reach = 10

    fun mouthOf(pipe: PipeToGroundEntity) =
        if (pipe.io == Io.INPUT) pipe.direction.opposite() else pipe.direction

    fun links(tile: Coordinate): Set<Coordinate> {
        val entity = plumbing[tile] ?: return emptySet()
        val result = mutableSetOf<Coordinate>()
        when (entity) {
            is PipeEntity,
            is InfinityPipeEntity ->
                Direction.entries.forEach { direction ->
                    val next = tile + direction.step
                    if (next in plumbing) result += next
                }
            is PipeToGroundEntity -> {
                val mouth = mouthOf(entity)
                val front = tile + mouth.step
                val neighbour = plumbing[front]
                if (
                    neighbour is PipeEntity ||
                        neighbour is InfinityPipeEntity ||
                        (neighbour is PipeToGroundEntity && mouthOf(neighbour) == mouth.opposite())
                ) {
                    result += front
                }
                for (distance in 2..reach) {
                    val toward =
                        if (entity.io == Io.INPUT) entity.direction.step
                        else entity.direction.step * -1
                    val other = plumbing[tile + toward * distance]
                    if (
                        other is PipeToGroundEntity &&
                            other.material == entity.material &&
                            other.direction == entity.direction &&
                            other.io != entity.io
                    ) {
                        result += other.area.topLeft
                        break
                    }
                }
            }
        }
        return result
    }

    // only mutual links count: a pipe beside a pipe-to-ground's flank does not connect
    val adjacency =
        plumbing.keys.associateWith { tile ->
            links(tile).filter { links(it).contains(tile) }.toSet()
        }
    val seen = mutableSetOf<Coordinate>()
    val component = mutableMapOf<Coordinate, Int>()
    var components = 0
    for (tile in plumbing.keys) {
        if (!seen.add(tile)) continue
        val queue = ArrayDeque(listOf(tile))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            component[current] = components
            for (next in adjacency.getValue(current)) if (seen.add(next)) queue += next
        }
        components++
    }

    return plumbing.values
        .groupBy { it.material }
        .flatMap { (material, pipes) ->
            val parts = pipes.map { component.getValue(it.area.topLeft) }.distinct()
            if (parts.size <= 1) emptyList()
            else listOf("${material.name} is split over ${parts.size} networks")
        }
}

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
