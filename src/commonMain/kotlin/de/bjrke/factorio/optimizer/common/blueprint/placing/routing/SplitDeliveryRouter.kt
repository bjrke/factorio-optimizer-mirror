package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIssue
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.isSingleMaterial
import de.bjrke.factorio.optimizer.common.blueprint.layout.issue
import de.bjrke.factorio.optimizer.common.blueprint.placing.route.LayoutRouter
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.pathConflicts
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.placePath
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.adjacent
import de.bjrke.factorio.optimizer.common.core.model.manhattanDist

/**
 * How many of the nearest split points are routed before the cheapest one is chosen. The belt is
 * usually split near the sink, so this bounds the routing cost of the common case.
 */
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
    private val failures: MutableList<LayoutIssue>,
) {

    /** A place where a splitter can be inserted into a produced material's belt. */
    private class SplitCandidate(
        val belt: Coordinate,
        val side: Coordinate,
        /** The splitter's free output, where the branch belt starts. */
        val exit: Coordinate,
        val direction: Direction,
        /** The trunk's lanes, which the splitter and both of its outputs keep. */
        val load: LaneLoad,
    )

    fun deliver(material: Material, sink: Sink): Boolean {
        val candidates = splitCandidates(material).sortedBy { it.exit.manhattanDist(sink.feed) }
        // The belt is split near the sink when that works. The nearest points can all be walled off
        // by the rows between them and the sink while a farther one still reaches it, so a failed
        // first batch searches the rest instead of reporting the feed lost.
        val (candidate, segment) =
            bestSplit(candidates.take(SPLIT_CANDIDATES), material, sink)
                ?: bestSplit(candidates.drop(SPLIT_CANDIDATES), material, sink)
                ?: run {
                    failures += sink.feed.issue("${material.name} to ${sink.feed} (no split)")
                    return false
                }

        split(candidate, material)
        val nodes = listOf(candidate.exit) + segment
        placePath(board, material, nodes, sink.heading, candidate.load)
        networks.record(material, nodes, sink)
        return true
    }

    /**
     * The cheapest routable split of [candidates], or null when none of them reaches the [sink].
     */
    private fun bestSplit(
        candidates: List<SplitCandidate>,
        material: Material,
        sink: Sink,
    ): Pair<SplitCandidate, List<Coordinate>>? {
        var best: Pair<SplitCandidate, List<Coordinate>>? = null
        var bestScore = Int.MAX_VALUE
        for (candidate in candidates) {
            val splitterTiles = setOf(candidate.belt, candidate.side)
            // the branch starts on the splitter's exit, which the search itself never judges; a
            // foreign belt pouring into it would side-load the branch
            if (rules.sideLoadedByForeign(candidate.exit, material, splitterTiles)) continue
            val segment =
                router.findRoute(
                    candidate.exit,
                    candidate.direction,
                    sink.feed,
                    rules.allowed(material, sink, splitterTiles),
                    strictStart = true,
                ) ?: continue
            // a branch that reuses one of its own belt tiles would lose the belt it already placed
            val nodes = listOf(candidate.exit) + segment
            if (pathConflicts(material, nodes).isNotEmpty()) continue
            val score = routeScore(nodes)
            if (score < bestScore) {
                bestScore = score
                best = candidate to segment
            }
        }
        return best
    }

    /**
     * How good a split route is: its traveled length plus a penalty for every underground run. A
     * slightly longer surface route beats a shorter one that dives under the trunk.
     */
    private fun routeScore(nodes: List<Coordinate>): Int =
        nodes.zipWithNext().sumOf { (from, to) ->
            if (from.adjacent(to)) 1 else from.manhattanDist(to) + SPLIT_TUNNEL_PENALTY
        }

    /**
     * Every belt tile where a splitter fits: straight trunk, a free side, a free exit and no
     * foreign belt pouring into either splitter tile. A splitter takes items on both back tiles, so
     * a foreign feeder on the second one would merge its material into the split.
     */
    private fun splitCandidates(material: Material): List<SplitCandidate> {
        val tiles = networks[material]
        return tiles.flatMap { tile ->
            val belt = board.entityAt(tile) as? BeltEntity ?: return@flatMap emptyList()
            if (!belt.isSingleMaterial(material)) return@flatMap emptyList()
            val direction = belt.direction
            val next = board.entityAt(tile + direction.step) as? BeltEntity
            if (next == null || !next.isSingleMaterial(material) || next.direction != direction) {
                return@flatMap emptyList()
            }
            // a splitter takes items on its back only: on a turn the trunk would feed its side and
            // the branch would start without input
            if (!fedStraight(tile, direction, material)) return@flatMap emptyList()
            sides(direction).mapNotNull { sideDirection ->
                val side = tile + sideDirection.step
                val exit = side + direction.step
                if (!freeForSplit(side, material) || !freeForSplit(exit, material)) {
                    return@mapNotNull null
                }
                val area = splitterArea(tile, side, direction)
                if (!area.all { it == tile || board.isFree(it) }) return@mapNotNull null
                // only the trunk's own back tile is covered by fedStraight; a belt pouring into
                // the splitter's other tile would merge its material into the branch
                val splitterTiles = setOf(tile, side)
                if (splitterTiles.any { rules.sideLoadedByForeign(it, material, splitterTiles) }) {
                    return@mapNotNull null
                }
                SplitCandidate(tile, side, exit, direction, belt.load)
            }
        }
    }

    /**
     * A side or exit tile a splitter may take: free, not a reserved feed and not a foreign
     * approach.
     */
    private fun freeForSplit(tile: Coordinate, material: Material) =
        board.isFree(tile) &&
            !board.isReserved(tile) &&
            (board.reservedMaterialAt(tile)?.let { it == material } ?: true)

    /** Whether the tile behind [tile] feeds it straight on, the way a splitter's back expects. */
    private fun fedStraight(tile: Coordinate, direction: Direction, material: Material): Boolean {
        val feeder = board.entityAt(tile - direction.step)
        return when (feeder) {
            is UndergroundBeltEntity ->
                feeder.io == Io.OUTPUT &&
                    feeder.direction == direction &&
                    feeder.isSingleMaterial(material)
            is BeltEntity -> feeder.direction == direction && feeder.isSingleMaterial(material)
            is LoaderEntity ->
                feeder.io == Io.OUTPUT &&
                    feeder.direction == direction &&
                    feeder.material == material
            else -> false
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
            WEST -> Area(belt.x, minOf(belt.y, side.y), 1, 2)
            NORTH,
            SOUTH -> Area(minOf(belt.x, side.x), belt.y, 2, 1)
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
                candidate.load,
            ),
        )
    }
}
