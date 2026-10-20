package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.util.MinHeap

/** West edge no feed route may cross, so a route cannot run around the import lanes. */
private const val ROUTE_WEST_BOUND = 2

/** How far past everything placed a route may still search, so A* stays near the blueprint. */
private const val ROUTE_MARGIN = 24

/** Safety cap on A* expansions; a route that needs more is reported as unreachable. */
private const val MAX_ROUTE_EXPANSIONS = 1_000_000

/**
 * A search node: a tile, the direction the route entered it from, and how many more tiles it must
 * run straight before it may dive again. A landing keeps that straight run so a belt exit is not
 * immediately followed by the next entrance.
 */
private data class RouteNode(val tile: Coordinate, val heading: Direction, val straight: Int)

/**
 * What makes belts and pipes route differently: the tile costs, the tunnel range and whether a
 * tunnel may only be started over an obstacle (belts) or anywhere (pipes).
 */
internal class RouteProfile(
    /** Cost of one straight surface tile. */
    val surfaceCost: Int = 4,
    /** Added when a surface route changes direction, so belts prefer to run straight. */
    val turnCost: Int = 1,
    /** Per-tile cost of an underground tile; it differs between belts and pipes. */
    val undergroundCost: Int,
    /** Fixed cost of entering an underground run; it differs between belts and pipes. */
    val tunnelEntry: Int,
    /**
     * Added to a surface tile outside the start-to-goal corridor, to prefer a tunnel over a detour.
     */
    val detourCost: Int = 2,
    /** Added next to a reserved feed or source tile, so a route cannot box it in. */
    val keepClearCost: Int = 6,
    /** Maximum length of a run, matching the prototype's `max_distance`. */
    val range: Int,
    /** The underground entity this profile places. */
    val undergroundType: String,
    /** Whether a tunnel may only be started over an obstacle (belts) or anywhere (pipes). */
    val tunnelOnlyWhenBlocked: Boolean,
    /** Whether the route may immediately reverse into the tile it came from. */
    val forbidReverse: Boolean = true,
    /**
     * How many straight surface tiles a tunnel landing must run before the next tunnel may start. A
     * belt exit only feeds a surface tile, so a belt needs one tile there plus the next entrance; a
     * pipe-to-ground exit joins its neighbor directly.
     */
    val landingStraight: Int = 1,
)

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
 * The weighted A* router shared by belts and pipes. It only asks the [board] what a tile holds and
 * where the layout currently ends, so route costs stay independent of placement code.
 */
internal class LayoutRouter(private val board: LayoutBoard) {

    /**
     * Belts tunnel only over an obstacle, so surface stays their first choice; a slight per-tile
     * discount keeps a blocked tile tunneled rather than detoured, while the stretcher decides how
     * long the run becomes.
     */
    val beltProfile =
        RouteProfile(
            undergroundCost = 3,
            tunnelEntry = 1,
            detourCost = 0,
            range = board.undergroundRange,
            undergroundType = board.names.undergroundBelt,
            tunnelOnlyWhenBlocked = true,
            // a belt exit feeds a surface belt, which then feeds the next entrance
            landingStraight = 2,
        )

    /** Pipes may tunnel anywhere and prefer to: the player walks over a buried pipe, not a pipe. */
    val pipeProfile =
        RouteProfile(
            undergroundCost = 2,
            tunnelEntry = 2,
            range = PIPE_UNDERGROUND_RANGE,
            undergroundType = board.names.pipeToGround,
            tunnelOnlyWhenBlocked = false,
            forbidReverse = false,
        )

    /**
     * A weighted A* route for belts: a straight surface run is cheapest, a turn costs a little, and
     * a straight underground run under an obstacle is preferred over routing around it. The route
     * never steps west of [ROUTE_WEST_BOUND], and surface tiles outside the start-to-goal corridor
     * pay [RouteProfile.detourCost].
     */
    fun findRoute(
        from: Coordinate,
        fromDirection: Direction,
        to: Coordinate,
        allowed: (Coordinate, Direction) -> Boolean,
        strictStart: Boolean = false,
    ) = route(from, fromDirection, to, allowed, beltProfile, strictStart)

    /**
     * A weighted A* route. A tile costs [RouteProfile.surfaceCost], a turn adds
     * [RouteProfile.turnCost], and a straight underground run costs [RouteProfile.tunnelEntry] plus
     * [RouteProfile.undergroundCost] per tile, so a started tunnel runs long. A tunnel is entered
     * only straight, and (for belts) only over an obstacle so surface stays the first choice. The
     * goal is always reached on the surface; the caller decides which way the goal entity faces.
     * Manhattan times the cheapest tile is the (admissible) heuristic.
     */
    fun route(
        from: Coordinate,
        heading: Direction,
        to: Coordinate,
        allowed: (Coordinate, Direction) -> Boolean,
        profile: RouteProfile,
        strictStart: Boolean = false,
    ): List<Coordinate>? {
        if (from == to) return emptyList()
        if (!inRegion(to)) return null

        val start = RouteNode(from, heading, straight = 0)
        val gScore = mutableMapOf(start to 0)
        val previous = mutableMapOf<RouteNode, RouteNode>()
        val heap = MinHeap<RouteEntry>()
        var seq = 0
        heap.add(RouteEntry(heuristic(from, to, profile), 0, seq++, start))

        fun relax(current: RouteNode, next: RouteNode, cost: Int, g: Int) {
            val tentative = g + cost
            if (tentative >= (gScore[next] ?: Int.MAX_VALUE)) return
            gScore[next] = tentative
            previous[next] = current
            heap.add(
                RouteEntry(tentative + heuristic(next.tile, to, profile), tentative, seq++, next),
            )
        }

        var popped = 0
        while (!heap.isEmpty() && popped++ < MAX_ROUTE_EXPANSIONS) {
            val entry = heap.poll()
            val current = entry.node
            if (entry.g > (gScore[current] ?: Int.MAX_VALUE)) continue
            if (current.tile == to) return reconstructRoute(previous, current, start)

            // after surfacing, a run must stay on the surface for a while before it may dive again
            val directions =
                if (current.straight > 0) listOf(current.heading) else Direction.entries
            for (direction in directions) {
                val directionStep = direction.step
                val surface = current.tile + directionStep
                // a belt cannot turn back into the tile it just came from
                if (profile.forbidReverse && direction == current.heading.opposite()) continue
                // a split route must not step back onto its start: that would leave a dead end
                if (strictStart && surface == from) continue
                if (inRegion(surface) && allowed(surface, direction)) {
                    val turn = if (current.heading != direction) profile.turnCost else 0
                    val detour = if (inCorridor(surface, from, to)) 0 else profile.detourCost
                    val clear =
                        if (Direction.entries.any { board.isReserved(surface + it.step) })
                            profile.keepClearCost
                        else 0
                    relax(
                        current,
                        RouteNode(surface, direction, straight = maxOf(0, current.straight - 1)),
                        profile.surfaceCost + turn + detour + clear,
                        entry.g,
                    )
                }
                // a tunnel is only entered straight, never right after surfacing; a splitter
                // branch may not start with one, because the splitter would feed its side
                val cannotTunnel =
                    (strictStart && current == start) ||
                        current.straight > 0 ||
                        current.heading != direction
                if (cannotTunnel) continue
                // a belt tunnels only to cross an obstacle: if the tile ahead is free, step there
                if (
                    profile.tunnelOnlyWhenBlocked &&
                        allowed(current.tile + directionStep, direction)
                )
                    continue
                for (reach in 2..profile.range) {
                    val target = current.tile + directionStep * reach
                    // a feed must reach its sink on the surface, so its last belt faces the lane
                    if (target == to || (strictStart && target == from)) continue
                    if (!inRegion(target) || !allowed(target, direction)) continue
                    if (!tunnelFree(current.tile, directionStep, reach, profile.undergroundType)) {
                        continue
                    }
                    relax(
                        current,
                        RouteNode(target, direction, straight = profile.landingStraight),
                        profile.tunnelEntry + reach * profile.undergroundCost,
                        entry.g,
                    )
                }
            }
        }
        return null
    }

    private fun reconstructRoute(
        previous: Map<RouteNode, RouteNode>,
        goal: RouteNode,
        start: RouteNode,
    ) = buildList {
        var current: RouteNode? = goal
        while (current != null && current != start) {
            add(current.tile)
            current = previous[current]
        }
    }
        .asReversed()

    private fun heuristic(tile: Coordinate, to: Coordinate, profile: RouteProfile) =
        minOf(profile.surfaceCost, profile.undergroundCost) * tile.manhattanDist(to)

    /** Within the start-to-goal corridor, where a surface tile costs no extra detour penalty. */
    private fun inCorridor(tile: Coordinate, from: Coordinate, to: Coordinate) =
        tile.x in minOf(from.x, to.x)..maxOf(from.x, to.x) &&
            tile.y in minOf(from.y, to.y)..maxOf(from.y, to.y)

    /**
     * Whether a route may use [coordinate]: east of [ROUTE_WEST_BOUND], and within [ROUTE_MARGIN]
     * of everything placed, so the search stays near the blueprint instead of roaming forever.
     */
    private fun inRegion(coordinate: Coordinate) =
        coordinate.x >= ROUTE_WEST_BOUND &&
            coordinate.x <= board.maxX + ROUTE_MARGIN &&
            coordinate.y >= board.minY - ROUTE_MARGIN &&
            coordinate.y <= board.maxY + ROUTE_MARGIN

    /**
     * True when a tunnel of [reach] tiles in [direction] clashes with a tunnel already there. Two
     * runs may share a tile on different axes, or with a different underground type.
     */
    private fun tunnelFree(
        from: Coordinate,
        direction: Coordinate,
        reach: Int,
        type: String,
    ): Boolean = (1 until reach).none { board.hasTunnel(from + direction * it, direction, type) }
}
