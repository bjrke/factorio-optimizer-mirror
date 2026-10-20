package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction

/**
 * The tile costs of a route: one straight surface tile, a turn, and an underground run
 * ([tunnelEntry] plus [undergroundCost] per tile). A surface tile outside the start-to-goal
 * corridor pays [detourCost], and one next to a reserved feed or source pays [keepClearCost].
 */
internal data class RouteCosts(
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
) {
    /** The cheapest tile cost, so the A* heuristic stays admissible. */
    val cheapestTile: Int
        get() = minOf(surfaceCost, undergroundCost)
}

/**
 * What differs between a belt and a pipe tunnel: the reach and the underground entity, whether a
 * tunnel may start anywhere, whether the route may reverse, and the straight surface run a landing
 * needs before the next tunnel.
 */
internal data class TunnelRules(
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

/** The costs and tunnel rules that make belts and pipes route differently. */
internal data class RouteProfile(val costs: RouteCosts, val tunnels: TunnelRules)

/**
 * One routing request: the start and heading, the goal, the tile predicate and the profile.
 * Grouping them keeps the A* search from threading the same six values through every helper.
 */
internal data class RouteRequest(
    val from: Coordinate,
    val heading: Direction,
    val to: Coordinate,
    val allowed: (Coordinate, Direction) -> Boolean,
    val profile: RouteProfile,
    val strictStart: Boolean = false,
)
