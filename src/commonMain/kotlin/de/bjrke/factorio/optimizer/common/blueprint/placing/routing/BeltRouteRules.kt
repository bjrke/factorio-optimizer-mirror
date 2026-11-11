package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.directionBetween
import de.bjrke.factorio.optimizer.common.core.model.neighbors

/**
 * What a belt route may do: stay off foreign belts, leave every other sink's feed clear, and avoid
 * [blocked] tiles (the splitter a branch starts from must not be routed over).
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
        (routable(coordinate) || goal) &&
            coordinate !in blocked &&
            !sideLoaded(coordinate, material) &&
            (goal || !poursIntoSide(coordinate, facing, material)) &&
            !facesReservedFeed(coordinate, facing, sink.feed, material)
    }

    /** True when a belt next to [coordinate] pours a different load into that tile. */
    private fun sideLoaded(coordinate: Coordinate, material: Material): Boolean =
        coordinate.neighbors.any { neighbor ->
            val belt = board.entityAt(neighbor) as? ConveyorEntity
            belt != null &&
                belt.direction == directionBetween(neighbor, coordinate) &&
                !belt.isRouteMaterial(material)
        }

    /** True when a belt at [coordinate] leaving in [direction] would side-load a foreign belt. */
    private fun poursIntoSide(
        coordinate: Coordinate,
        direction: Direction,
        material: Material,
    ): Boolean {
        val belt = board.entityAt(coordinate + direction.step) as? ConveyorEntity ?: return false
        return !belt.isRouteMaterial(material) &&
            belt.direction != direction &&
            belt.direction != direction.opposite()
    }

    /** A route carries one material on both lanes; a mixed or half belt would merge a lane. */
    private fun ConveyorEntity.isRouteMaterial(material: Material) =
        materials == setOf(material) && (this as? BeltEntity)?.load?.isFull != false

    /**
     * True when a belt at [coordinate] leaving in [direction] would run straight at a reserved feed
     * tile. That feed would later be entered from another side, so the belt would either block it
     * or side-load a foreign material into the lane.
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

    private fun routable(coordinate: Coordinate) =
        board.isFree(coordinate) && !board.isReserved(coordinate)
}
