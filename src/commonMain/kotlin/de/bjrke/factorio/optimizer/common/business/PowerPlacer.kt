package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.sqrt

/** Medium poles supply a 7x7 area (radius 3) and reach 9 tiles of wire. */
private const val SUPPLY_RADIUS = 3

private const val WIRE_REACH = 9
private const val WIRE_REACH_SQ = WIRE_REACH * WIRE_REACH

/**
 * Covers every powered entity with a medium pole and wires the poles into one network. Poles stay
 * east of [westBound], which keeps them out of the import extension.
 */
internal class PowerPlacer(private val board: LayoutBoard, private val westBound: Int) {

    fun place() {
        placePoles()
        wirePoles()
    }

    /** Covers every machine, inserter and loader with a pole and wires the poles together. */
    private fun placePoles() {
        val machineAreas =
            board.placed
                .filter { it.isPowered }
                .map { it.area }
                .sortedWith(compareBy({ it.y }, { it.x }))
        if (machineAreas.isEmpty()) return

        val covered = mutableSetOf<Coordinate>()
        val poles = mutableSetOf<Coordinate>()
        for (area in machineAreas) {
            if (!area.any(covered::contains)) {
                val spot = bestPoleSpot(area, covered) ?: continue
                poles += spot
                board.placePole(spot)
                covered.addAll(spot.area().grow(SUPPLY_RADIUS))
            }
        }
        connectPoles(poles)
    }

    /** The free tile near [tile] that powers the most not-yet-[covered] machines, if any. */
    private fun bestPoleSpot(tile: Area, covered: Set<Coordinate>) =
        tile
            .grow(SUPPLY_RADIUS)
            .filter { board.isFree(it) && it.x >= westBound }
            .maxByOrNull {
                it.area().grow(SUPPLY_RADIUS).sumOf { other ->
                    when {
                        other in covered -> 0
                        board.entityAt(other)?.isPowered == true -> 2
                        else -> 1
                    }
                }
            }

    /** Adds poles until every pole can reach another, so the whole set is one wire network. */
    private fun connectPoles(poles: MutableSet<Coordinate>) {
        val components = poleComponents(poles)
        repeat(40) {
            if (components.size <= 1) return

            var bestDistanceSq = Long.MAX_VALUE
            var from: Coordinate? = null
            var to: Coordinate? = null
            var fromIndex = -1
            var toIndex = -1

            for (i in components.indices) {
                for (j in i + 1 until components.size) {
                    for (a in components[i]) {
                        for (b in components[j]) {
                            val distanceSq = a.distanceSq(b)
                            if (distanceSq < bestDistanceSq) {
                                bestDistanceSq = distanceSq
                                from = a
                                to = b
                                fromIndex = i
                                toIndex = j
                            }
                        }
                    }
                }
            }
            if (from == null || to == null) return

            val steps =
                ceil(sqrt(bestDistanceSq.toDouble()) / (WIRE_REACH - 1)).toInt().coerceAtLeast(1)
            var added = false

            for (step in 1 until steps) {
                val fraction = step.toDouble() / steps
                val x = round(from.x + (to.x - from.x) * fraction).toInt()
                val y = round(from.y + (to.y - from.y) * fraction).toInt()
                val spot = freeNear(Coordinate(x, y)) ?: continue
                poles += spot
                board.placePole(spot)
                components[fromIndex] += spot
                added = true
            }
            if (!added) return

            components[fromIndex] += components[toIndex]
            components.removeAt(toIndex)
        }
    }

    /** The wire-connected groups of [poles], two poles being connected within the wire reach. */
    private fun poleComponents(poles: Set<Coordinate>): MutableList<MutableList<Coordinate>> {
        val seen = mutableSetOf<Coordinate>()
        val components = mutableListOf<MutableList<Coordinate>>()
        for (pole in poles) {
            if (!seen.add(pole)) continue
            val component = mutableListOf<Coordinate>()
            val queue = ArrayDeque<Coordinate>()
            var current: Coordinate? = pole
            while (current != null) {
                component += current
                // this is a job for a quadtree!
                for (other in poles) {
                    if (other !in seen) {
                        val reach = current.distanceSq(other)
                        if (reach <= WIRE_REACH_SQ) {
                            seen += other
                            queue += other
                        }
                    }
                }
                current = queue.removeFirstOrNull()
            }
            components += component
        }
        return components
    }

    /** The nearest free tile within one tile of ([c]), if any. */
    private fun freeNear(c: Coordinate): Coordinate? {
        for (radius in 0..1) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val toTest = Coordinate(c.x + dx, c.y + dy)
                    if (board.isFree(toTest) && toTest.x >= westBound) return toTest
                }
            }
        }
        return null
    }

    /**
     * Wires every pair of poles within reach. A blueprint does not rebuild pole wires on its own,
     * so they have to be part of it.
     */
    private fun wirePoles() {
        val poles = board.placed.withIndex().filter { it.value is PoleEntity }
        for (i in poles.indices) {
            for (j in i + 1 until poles.size) {
                val a = poles[i]
                val b = poles[j]
                if (a.value.area.topLeft.distanceSq(b.value.area.topLeft) <= WIRE_REACH_SQ) {
                    val first = board.placed[a.index] as PoleEntity
                    val second = board.placed[b.index] as PoleEntity
                    board.placed[a.index] =
                        first.copy(neighbours = first.neighbours + (b.index + 1))
                    board.placed[b.index] =
                        second.copy(neighbours = second.neighbours + (a.index + 1))
                }
            }
        }
    }
}
