package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.core.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area
import de.bjrke.factorio.optimizer.common.core.model.directionBetween
import de.bjrke.factorio.optimizer.common.core.model.manhattanDist

// The run vocabulary: a surface tile, a single tunnel, and the plan that joins two tiles with the
// longest tunnels the material's tier allows. A link either rides in pipes (a fluid) or on belts
// (a solid), so every helper here asks the material instead of assuming a fluid.

/** Places a belt or a pipe, whichever [material] rides: a fluid goes through a pipe. */
internal fun LayoutBoard.placeSurface(
    material: Material,
    coordinate: Coordinate,
    direction: Direction,
) {
    if (isFluid(material)) placePipe(coordinate, material)
    else placeBelt(coordinate, material, direction)
}

/** Places an underground belt or pipe-to-ground end (always one tile) for [material]. */
internal fun LayoutBoard.placeEnd(
    material: Material,
    coordinate: Coordinate,
    direction: Direction,
    io: Io,
) {
    place(
        if (isFluid(material))
            PipeToGroundEntity(coordinate.area(), names.pipeToGround, material, direction, io)
        else
            UndergroundBeltEntity(
                coordinate.area(),
                names.undergroundBelt,
                material,
                direction,
                io,
            ),
    )
}

/** Places one tunnel for [material]: an input end at [from] and an output end at [to]. */
internal fun LayoutBoard.placeTunnel(
    material: Material,
    from: Coordinate,
    to: Coordinate,
    direction: Direction = directionBetween(from, to),
) {
    placeEnd(material, from, direction, INPUT)
    placeEnd(material, to, direction, OUTPUT)
    markTunnel(from, to, endName(material))
}

/**
 * A link between two tiles, as it will be placed: the plain surface tiles and the tunnels joining
 * them. [direction] is the link's travel direction, so every tile faces along it.
 */
internal class TunnelPlan(
    val material: Material,
    val direction: Direction,
    /** The plain surface tiles of the link, in travel order. */
    val surface: List<Coordinate>,
    /** The tunnels of the link, input end first, in travel order. */
    val runs: List<Pair<Coordinate, Coordinate>>,
) {

    /**
     * Every tile the link claims on the surface: its plain tiles and both ends of its runs. The
     * tiles a run passes under are not included, since anything may stand above them.
     */
    val tiles
        get() = surface + runs.flatMap { (from, to) -> listOf(from, to) }
}

/**
 * Plans a link between [from] and [to] with the longest runs [material]'s tier allows. Neighbouring
 * runs meet end to end: a pipe-to-ground may surface into the next entrance, and so may an
 * underground belt as long as the output faces the input. A one-tile link is a single surface tile.
 *
 * Both tiles must share a row or a column, and [to] must lie in the travel direction from [from]; a
 * further tile is unresolvable because the runs only step along one axis.
 */
internal fun LayoutBoard.tunnelPlan(
    material: Material,
    from: Coordinate,
    to: Coordinate,
): TunnelPlan {
    require(from.x == to.x || from.y == to.y) {
        "a tunnel link runs along one row or column: $from to $to"
    }
    val direction = directionBetween(from, to)
    val step = direction.step
    val reach = range(material)
    val surface = mutableListOf<Coordinate>()
    val runs = mutableListOf<Pair<Coordinate, Coordinate>>()
    when (val dist = to.manhattanDist(from)) {
        0 -> surface += from
        in 1..reach -> runs += from to to
        else -> {
            val segments = (dist + reach - 1) / reach
            var current = 0
            for (i in 1..segments) {
                // Distribute remainder evenly across the first few segments
                val tunnelEnd = dist * i / segments
                runs += from + step * current to from + step * tunnelEnd
                current = tunnelEnd + 1
            }
        }
    }

    return TunnelPlan(material, direction, surface, runs)
}

/** Places [plan]: its plain tiles first, then every tunnel of it. */
internal fun LayoutBoard.placeTunnelPlan(plan: TunnelPlan) {
    for (tile in plan.surface) placeSurface(plan.material, tile, plan.direction)
    for ((from, to) in plan.runs) placeTunnel(plan.material, from, to, plan.direction)
}

/** Plans and places the link from [from] to [to]; see [tunnelPlan]. */
internal fun LayoutBoard.placeTunnels(material: Material, from: Coordinate, to: Coordinate) {
    placeTunnelPlan(tunnelPlan(material, from, to))
}
