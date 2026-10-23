package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.adjacent
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.direction
import de.bjrke.factorio.optimizer.common.model.manhattanDist
import de.bjrke.factorio.optimizer.common.route.LayoutRouter
import de.bjrke.factorio.optimizer.common.special.placePath

/** How many split points are routed before the cheapest one is chosen. */
private const val SPLIT_CANDIDATES = 12

/** Extra cost of an underground run when comparing two split points, so surface wins when close. */
private const val SPLIT_TUNNEL_PENALTY = 12

/**
 * Feeds one more consumer by inserting a splitter into a produced material's existing belt. Both
 * sides of the belt are tried, and the routable split with the best surface-biased score wins.
 */
internal class SplitDeliveryRouter(
    private val board: LayoutBoard,
    private val router: LayoutRouter,
    private val networks: DeliveryNetworks,
    private val rules: BeltRouteRules,
    private val failures: MutableList<String>,
) {

    /** A place where a splitter can be inserted into a produced material's belt. */
    private class SplitCandidate(
        val belt: Coordinate,
        val side: Coordinate,
        /** The splitter's free output, where the branch belt starts. */
        val exit: Coordinate,
        val direction: Direction,
    )

    fun deliver(material: Material, sink: Sink): Boolean {
        val candidates = splitCandidates(material).sortedBy { it.exit.manhattanDist(sink.feed) }
        var best: Pair<SplitCandidate, List<Coordinate>>? = null
        var bestScore = Int.MAX_VALUE
        for (candidate in candidates.take(SPLIT_CANDIDATES)) {
            val splitterTiles = setOf(candidate.belt, candidate.side)
            val segment =
                router.findRoute(
                    candidate.exit,
                    candidate.direction,
                    sink.feed,
                    rules.allowed(material, sink, splitterTiles),
                    strictStart = true,
                ) ?: continue
            val score = routeScore(listOf(candidate.exit) + segment)
            if (score < bestScore) {
                bestScore = score
                best = candidate to segment
            }
        }
        val (candidate, segment) =
            best
                ?: run {
                    failures += "${material.name} to ${sink.feed} (no split)"
                    return false
                }

        split(candidate, material)
        val nodes = listOf(candidate.exit) + segment
        placePath(board, material, nodes, sink.heading)
        networks.record(material, nodes, sink)
        return true
    }

    /**
     * How good a split route is: its traveled length plus a penalty for every underground run. A
     * slightly longer surface route beats a shorter one that dives under the trunk.
     */
    private fun routeScore(nodes: List<Coordinate>): Int =
        nodes.zipWithNext().sumOf { (from, to) ->
            if (from.adjacent(to)) 1 else from.manhattanDist(to) + SPLIT_TUNNEL_PENALTY
        }

    /** Every belt tile where a splitter fits: straight trunk, a free side and a free exit. */
    private fun splitCandidates(material: Material): List<SplitCandidate> {
        val tiles = networks[material]
        return tiles.flatMap { tile ->
            val belt = board.entityAt(tile) as? BeltEntity ?: return@flatMap emptyList()
            if (belt.material != material) return@flatMap emptyList()
            val direction = belt.direction
            val next = board.entityAt(tile + direction.step) as? BeltEntity
            if (next == null || next.material != material || next.direction != direction) {
                return@flatMap emptyList()
            }
            sides(direction).mapNotNull { sideDirection ->
                val side = tile + sideDirection.step
                if (!board.isFree(side) || board.isReserved(side)) return@mapNotNull null
                val exit = side + direction.step
                if (!board.isFree(exit) || board.isReserved(exit)) return@mapNotNull null
                val area = splitterArea(tile, side, direction)
                if (!area.all { it == tile || board.isFree(it) }) return@mapNotNull null
                SplitCandidate(tile, side, exit, direction)
            }
        }
    }

    /** The two tiles across [direction] from tile that a splitter can use. */
    private fun sides(direction: Direction) =
        when (direction) {
            NORTH,
            SOUTH -> listOf(EAST, WEST)
            EAST,
            WEST -> listOf(NORTH, SOUTH)
        }

    /** The two-by-one area a splitter covers when [belt] is its input side. */
    private fun splitterArea(belt: Coordinate, side: Coordinate, direction: Direction) =
        when (direction) {
            EAST,
            WEST -> Coordinate(belt.x, minOf(belt.y, side.y)).area(1, 2)
            NORTH,
            SOUTH -> Coordinate(minOf(belt.x, side.x), belt.y).area(2, 1)
        }

    /** Replaces the trunk belt at [candidate] with a splitter; its continuations stay in place. */
    private fun split(candidate: SplitCandidate, material: Material) {
        board.removeAt(candidate.belt)
        board.place(
            SplitterEntity(
                splitterArea(candidate.belt, candidate.side, candidate.direction),
                board.names.splitter,
                material,
                candidate.direction,
            ),
        )
    }
}
