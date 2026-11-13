package de.bjrke.factorio.optimizer.common.blueprint.placing.special

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placePole
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.area
import de.bjrke.factorio.optimizer.common.core.model.distanceSq
import de.bjrke.factorio.optimizer.common.core.util.connectedComponents
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.sqrt

/** Bound on the pole-connection rounds, so a gap that cannot be bridged terminates. */
private const val MAX_POLE_CONNECT_ATTEMPTS = 40

/**
 * Covers every powered entity with the configured [pole] and wires the poles into one network.
 * Poles stay east of [westBound], which keeps them out of the import extension; [PowerPole.NONE]
 * places nothing because a space platform powers its machines without poles.
 */
internal class PowerPlacer(
    private val board: LayoutBoard,
    private val westBound: Int,
    private val pole: PowerPole,
) {
    private val wireReachSq = pole.wireReach * pole.wireReach

    fun place() {
        if (pole == PowerPole.NONE) return
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
                val spot = bestPoleSpot(area, covered, poles) ?: continue
                poles += spot
                board.placePole(spot)
                covered.addAll(spot.area().grow(pole.supplyRadius))
            }
        }
        connectPoles(poles)
    }

    /** The free tile near [tile] that powers the most not-yet-[covered] machines, if any. */
    private fun bestPoleSpot(
        tile: Area,
        covered: Set<Coordinate>,
        poles: MutableSet<Coordinate>,
    ): Coordinate? {
        val tilesWhichWouldCover =
            tile.grow(pole.supplyRadius).filter { board.isFree(it) && it.x >= westBound }
        val tilesInReachOfOtherPoles = tilesWhichWouldCover.filter { pos ->
            poles.any { pos.distanceSq(it) <= wireReachSq }
        }
        val possibleTiles = tilesInReachOfOtherPoles.ifEmpty { tilesWhichWouldCover }

        return possibleTiles.maxByOrNull {
            it.area().grow(pole.supplyRadius).sumOf { other ->
                when {
                    other in covered -> 0
                    board.entityAt(other)?.isPowered == true -> 2
                    else -> 1
                }
            }
        }
    }

    /** Adds poles until every pole can reach another, so the whole set is one wire network. */
    private fun connectPoles(poles: MutableSet<Coordinate>) {
        val components =
            connectedComponents(poles) { center ->
                    poles.filter { it.distanceSq(center) <= wireReachSq }
                }
                .mapTo(mutableListOf()) { it.toMutableSet() }
        repeat(MAX_POLE_CONNECT_ATTEMPTS) {
            val bridge = closestBridge(components) ?: return
            if (!placeBridgeSteps(bridge, poles, components)) return
            components[bridge.fromIndex] += components[bridge.toIndex]
            components.removeAt(bridge.toIndex)
        }
    }

    /** The closest pair of poles in two different components, with the components' indices. */
    private fun closestBridge(components: List<Set<Coordinate>>): Bridge? {
        var best: Bridge? = null
        for (i in components.indices) {
            for (j in i + 1 until components.size) {
                val bridge = closestPair(components[i], components[j], i, j) ?: continue
                if (best == null || bridge.distanceSq < best.distanceSq) best = bridge
            }
        }
        return best
    }

    private fun closestPair(
        left: Set<Coordinate>,
        right: Set<Coordinate>,
        leftIndex: Int,
        rightIndex: Int,
    ): Bridge? =
        left
            .flatMap { a ->
                right.map { b -> Bridge(a, b, leftIndex, rightIndex) }
            }
            .minByOrNull { it.distanceSq }

    /**
     * Places the intermediate poles on the line between the bridge ends. Returns whether it placed
     * any, so a line with no free tile stops the connection rounds instead of looping.
     */
    private fun placeBridgeSteps(
        bridge: Bridge,
        poles: MutableSet<Coordinate>,
        components: List<MutableSet<Coordinate>>,
    ): Boolean {
        var added = false
        val steps =
            ceil(sqrt(bridge.distanceSq.toDouble()) / (pole.wireReach - 1)).toInt().coerceAtLeast(1)
        for (step in 1 until steps) {
            val fraction = step.toDouble() / steps
            val x = round(bridge.from.x + (bridge.to.x - bridge.from.x) * fraction).toInt()
            val y = round(bridge.from.y + (bridge.to.y - bridge.from.y) * fraction).toInt()
            val spot = freeNear(Coordinate(x, y)) ?: continue
            poles += spot
            board.placePole(spot)
            components[bridge.fromIndex] += spot
            added = true
        }
        return added
    }

    /** The two poles a bridge joins. */
    private data class Bridge(
        val from: Coordinate,
        val to: Coordinate,
        val fromIndex: Int,
        val toIndex: Int,
    ) {
        val distanceSq = from.distanceSq(to)
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
                if (a.value.area.topLeft.distanceSq(b.value.area.topLeft) <= wireReachSq) {
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
