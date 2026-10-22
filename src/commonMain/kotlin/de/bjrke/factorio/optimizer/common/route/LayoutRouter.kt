package de.bjrke.factorio.optimizer.common.route

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.PIPE_UNDERGROUND_RANGE
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction

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
            costs =
                RouteCosts(
                    undergroundCost = 3,
                    tunnelEntry = 1,
                    detourCost = 0,
                ),
            tunnels =
                TunnelRules(
                    range = board.undergroundRange,
                    undergroundType = board.names.undergroundBelt,
                    tunnelOnlyWhenBlocked = true,
                    // a belt exit feeds a surface belt, which then feeds the next entrance
                    landingStraight = 2,
                ),
        )

    /** Pipes may tunnel anywhere and prefer to: the player walks over a buried pipe, not a pipe. */
    val pipeProfile =
        RouteProfile(
            costs = RouteCosts(undergroundCost = 2, tunnelEntry = 2),
            tunnels =
                TunnelRules(
                    range = PIPE_UNDERGROUND_RANGE,
                    undergroundType = board.names.pipeToGround,
                    tunnelOnlyWhenBlocked = false,
                    forbidReverse = false,
                ),
        )

    /**
     * A weighted A* route for belts: a straight surface run is cheapest, a turn costs a little, and
     * a straight underground run under an obstacle is preferred over routing around it. The route
     * never steps west of the import lanes, and surface tiles outside the start-to-goal corridor
     * pay [RouteCosts.detourCost].
     */
    fun findRoute(
        from: Coordinate,
        fromDirection: Direction,
        to: Coordinate,
        allowed: (Coordinate, Direction) -> Boolean,
        strictStart: Boolean = false,
    ) = route(RouteRequest(from, fromDirection, to, allowed, beltProfile, strictStart))

    /**
     * A weighted A* route. A tile costs [RouteCosts.surfaceCost], a turn adds
     * [RouteCosts.turnCost], and a straight underground run costs [RouteCosts.tunnelEntry] plus
     * [RouteCosts.undergroundCost] per tile, so a started tunnel runs long. A tunnel is entered
     * only straight, and (for belts) only over an obstacle so surface stays the first choice. The
     * goal is always reached on the surface; the caller decides which way the goal entity faces.
     * Manhattan times the cheapest tile is the (admissible) heuristic.
     */
    fun route(request: RouteRequest): List<Coordinate>? {
        if (request.from == request.to) return emptyList()
        return RouteSearch(board, request).find()
    }
}
