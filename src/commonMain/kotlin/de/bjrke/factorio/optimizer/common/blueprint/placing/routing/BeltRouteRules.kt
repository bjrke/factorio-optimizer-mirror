package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.directionBetween
import de.bjrke.factorio.optimizer.common.core.model.neighbors

/**
 * What a belt route may do: stay off foreign belts, leave another feed's approach tile clear, avoid
 * [blocked] tiles (the splitter a branch starts from must not be routed over), and not take a tile
 * another belt already pours into. A route's belt faces the next route tile, which is free by
 * [routable], so a tile it may use never side-loads a foreign belt; judging that by the arrival
 * direction wrongly rejected corners
 * (`LayoutRouterTest.aCornerMayTurnOverAForeignBeltItDoesNotFeed`).
 *
 * A buried entrance dives instead of feeding the tile it faces, so it is not a side-load either;
 * `LayoutValidation*` uses the same rule. Its output end does surface and pour onto the next tile,
 * so only an input end is exempt.
 */
internal class BeltRouteRules(private val board: LayoutBoard) {

    fun allowed(
        material: Material,
        sink: Sink,
        blocked: Set<Coordinate> = emptySet(),
    ): (Coordinate, Direction) -> Boolean = { coordinate, heading ->
        val goal = coordinate == sink.feed
        val facing = if (goal) sink.heading ?: heading else heading
        // the feed may side-load its target (a fourth input does), but no earlier tile may
        (routable(coordinate, material) || goal) &&
            coordinate !in blocked &&
            !sideLoaded(coordinate) &&
            !facesReservedFeed(coordinate, facing, sink.feed, material)
    }

    /** True when a surface belt next to [coordinate] pours its load into that tile. */
    private fun sideLoaded(coordinate: Coordinate): Boolean =
        coordinate.neighbors.any { neighbor ->
            val belt = board.entityAt(neighbor) as? ConveyorEntity ?: return@any false
            // a buried entrance does not feed the tile above its run
            if (belt is UndergroundBeltEntity && belt.io == Io.INPUT) return@any false
            belt.direction == directionBetween(neighbor, coordinate)
        }

    /**
     * True when a belt at [coordinate] leaving in [direction] would run straight at a reserved feed
     * tile or at another feed's approach tile. That feed would later be entered from there, so the
     * belt would either block it or side-load a foreign material into the lane. Unlike the
     * side-load rule above this check keeps reading the arrival direction; the reported
     * advanced-circuit layout relies on that to tunnel its circuit feed
     * (`advancedCircuitUrlOnlyTunnelsForItsBandedFeeds`).
     */
    private fun facesReservedFeed(
        coordinate: Coordinate,
        direction: Direction,
        ownFeed: Coordinate,
        material: Material,
    ): Boolean {
        val ahead = coordinate + direction.step
        if (ahead == ownFeed) return false
        val reserved = board.reservedMaterialAt(ahead) ?: return false
        // the same material may join its own feed; a foreign one would merge or block it
        return reserved != material
    }

    private fun routable(coordinate: Coordinate, material: Material) =
        board.isFree(coordinate) &&
            !board.isReserved(coordinate) &&
            // another material's feed needs this tile as its approach
            (board.reservedMaterialAt(coordinate)?.let { it == material } ?: true)
}
