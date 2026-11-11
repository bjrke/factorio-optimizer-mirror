package de.bjrke.factorio.optimizer.common.blueprint.placing.route

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.hasTunnel
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.manhattanDist
import de.bjrke.factorio.optimizer.common.core.model.neighbors
import de.bjrke.factorio.optimizer.common.core.util.MinHeap

/** West edge no feed route may cross, so a route cannot run around the import lanes. */
private const val ROUTE_WEST_BOUND = 2

/**
 * How far past everything placed a route may still search on the north, south and east side: one
 * ring lane to travel along the outside, one to turn back in, and one spare. The west side keeps
 * [ROUTE_WEST_BOUND] instead, because the import connectors stand there.
 */
internal const val ROUTE_MARGIN = 3

/**
 * The fallback box: a route that only exists past [ROUTE_MARGIN] is still found, so a blueprint
 * stays usable, but [LayoutRouter] reports it — a silent widen would hide a layout that needs the
 * room. Every layout test asserts the failures are empty, so this turns the widen into a red suite.
 */
internal const val WIDE_ROUTE_MARGIN = 24

/** Safety cap on A* expansions; a route that needs more is reported as unreachable. */
private const val MAX_ROUTE_EXPANSIONS = 1_000_000

/**
 * A search node: a tile, the direction the route entered it from, and how many more tiles it must
 * run straight before it may dive again. A landing keeps that straight run so a belt exit is not
 * immediately followed by the next entrance.
 */
private data class RouteNode(val tile: Coordinate, val heading: Direction, val straight: Int)

/** One queued A* entry; ties break on `g` then insertion order, so routes are reproducible. */
private class RouteEntry(
    val f: Int,
    val g: Int,
    val seq: Int,
    val node: RouteNode,
) : Comparable<RouteEntry> {
    override fun compareTo(other: RouteEntry): Int {
        if (f != other.f) return f.compareTo(other.f)
        if (g != other.g) return g.compareTo(other.g)
        return seq.compareTo(other.seq)
    }
}

/**
 * One weighted A* search. It only asks the [board] what a tile holds and where the layout currently
 * ends, so route costs stay independent of placement code. The search keeps its own frontier and
 * scores; [LayoutRouter] owns the belt/pipe profiles and exposes the public entry points.
 */
internal class RouteSearch(
    private val board: LayoutBoard,
    request: RouteRequest,
    margin: Int = ROUTE_MARGIN,
) {

    private val from = request.from
    private val to = request.to
    private val allowed = request.allowed
    private val profile = request.profile
    private val strictStart = request.strictStart
    private val cheapestTile = profile.costs.cheapestTile
    private val start = RouteNode(from, request.heading, straight = 0)

    // the board does not change while one search runs, so the region is fixed up front
    private val eastBound = board.maxX + margin
    private val northBound = board.minY - margin
    private val southBound = board.maxY + margin

    private val gScore = mutableMapOf(start to 0)
    private val previous = mutableMapOf<RouteNode, RouteNode>()
    private val heap = MinHeap<RouteEntry>()
    private var seq = 0
    private var popped = 0

    /**
     * Runs the search and returns the tiles after [RouteRequest.from] up to the goal, or `null`
     * when the goal is out of the region or no route stays within [MAX_ROUTE_EXPANSIONS].
     */
    fun find(): List<Coordinate>? {
        if (!inRegion(to)) return null
        heap.add(RouteEntry(cheapestTile * from.manhattanDist(to), 0, seq++, start))

        while (!heap.isEmpty() && popped++ < MAX_ROUTE_EXPANSIONS) {
            val entry = heap.removeFirst()
            val current = entry.node
            if (entry.g > (gScore[current] ?: Int.MAX_VALUE)) continue
            if (current.tile == to) return reconstructRoute(current)

            // after surfacing, a run must stay on the surface for a while before it may dive again
            val directions =
                if (current.straight > 0) listOf(current.heading) else Direction.entries
            for (direction in directions) {
                expandSurface(current, direction, entry.g)
                expandTunnel(current, direction, entry.g)
            }
        }
        return null
    }

    /** Offers the surface step from [current] in [direction], if the profile allows it. */
    private fun expandSurface(current: RouteNode, direction: Direction, g: Int) {
        val surface = current.tile + direction.step
        // a belt cannot turn back into the tile it just came from
        if (profile.tunnels.forbidReverse && direction == current.heading.opposite()) return
        // never double back onto the tile a tunnel just surfaced on: that tile is one entity and
        // cannot also become this route's next entrance
        if (direction == current.heading.opposite() && (previous[current]?.straight ?: 0) > 0) {
            return
        }
        // a split route must not step back onto its start: that would leave a dead end
        if (strictStart && surface == from) return
        // the belt next to the goal has to face the goal, so judge that tile by the direction it
        // will leave in, not by the one it arrived from
        val towardGoal = towardGoal(surface)
        if (!inRegion(surface) || !allowed(surface, towardGoal ?: direction)) return

        val turn = if (current.heading != direction) profile.costs.turnCost else 0
        val detour = if (inCorridor(surface)) 0 else profile.costs.detourCost
        val clear = if (surface.neighbors.any(board::isReserved)) profile.costs.keepClearCost else 0
        relax(
            current,
            RouteNode(surface, direction, straight = maxOf(0, current.straight - 1)),
            profile.costs.surfaceCost + turn + detour + clear,
            g,
        )
    }

    /** Offers every underground run from [current] in [direction], if the profile allows one. */
    private fun expandTunnel(current: RouteNode, direction: Direction, g: Int) {
        if (!canTunnel(current, direction)) return
        // a belt tunnels only to cross an obstacle: if the tile ahead is free, step there
        if (
            profile.tunnels.tunnelOnlyWhenBlocked &&
                allowed(current.tile + direction.step, direction)
        ) {
            return
        }
        for (reach in 2..profile.tunnels.range) {
            val target = current.tile + direction.step * reach
            if (!tunnelTargetAllowed(current, target, direction, reach)) continue
            relax(
                current,
                RouteNode(target, direction, straight = profile.tunnels.landingStraight),
                profile.costs.tunnelEntry + reach * profile.costs.undergroundCost,
                g,
            )
        }
    }

    /**
     * A tunnel is only entered straight, never right after surfacing; a splitter branch may not
     * start with one, because the splitter would feed its side.
     */
    private fun canTunnel(current: RouteNode, direction: Direction) =
        !(strictStart && current == start) && current.straight == 0 && current.heading == direction

    /** A tunnel may surface on a free, allowed tile that is not the goal or the split's start. */
    private fun tunnelTargetAllowed(
        current: RouteNode,
        target: Coordinate,
        direction: Direction,
        reach: Int,
    ) =
        target != to &&
            !(strictStart && target == from) &&
            inRegion(target) &&
            allowed(target, direction) &&
            tunnelFree(current.tile, direction.step, reach)

    /** Records [next] when it improves the best known cost to it. */
    private fun relax(current: RouteNode, next: RouteNode, cost: Int, g: Int) {
        val tentative = g + cost
        if (tentative >= (gScore[next] ?: Int.MAX_VALUE)) return
        gScore[next] = tentative
        previous[next] = current
        heap.add(
            RouteEntry(
                tentative + cheapestTile * next.tile.manhattanDist(to),
                tentative,
                seq++,
                next,
            ),
        )
    }

    private fun reconstructRoute(goal: RouteNode) = buildList {
        var current: RouteNode? = goal
        while (current != null && current != start) {
            add(current.tile)
            current = previous[current]
        }
    }
        .asReversed()

    /** Within the start-to-goal corridor, where a surface tile costs no extra detour penalty. */
    private fun inCorridor(tile: Coordinate) =
        tile.x in minOf(from.x, to.x)..maxOf(from.x, to.x) &&
            tile.y in minOf(from.y, to.y)..maxOf(from.y, to.y)

    /**
     * The direction from [surface] into [to], or null when the tile does not touch the goal.
     * Reading the two coordinate deltas keeps the per-expansion check allocation free.
     */
    private fun towardGoal(surface: Coordinate) =
        when {
            to.y == surface.y ->
                when (to.x - surface.x) {
                    1 -> Direction.EAST
                    -1 -> Direction.WEST
                    else -> null
                }
            to.x == surface.x ->
                when (to.y - surface.y) {
                    1 -> Direction.SOUTH
                    -1 -> Direction.NORTH
                    else -> null
                }
            else -> null
        }

    /**
     * Whether a route may use [coordinate]: east of [ROUTE_WEST_BOUND], and within [ROUTE_MARGIN]
     * past everything placed on the other three sides, so the search stays near the blueprint.
     */
    private fun inRegion(coordinate: Coordinate) =
        coordinate.x >= ROUTE_WEST_BOUND &&
            coordinate.y >= northBound &&
            coordinate.x <= eastBound &&
            coordinate.y <= southBound

    /**
     * True when a tunnel of [reach] tiles in [direction] clashes with a tunnel already there. Two
     * runs may share a tile on different axes, or with a different underground type.
     */
    private fun tunnelFree(from: Coordinate, direction: Coordinate, reach: Int): Boolean =
        (1 until reach).none {
            board.hasTunnel(from + direction * it, direction, profile.tunnels.undergroundType)
        }
}
