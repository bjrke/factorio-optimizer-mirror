package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.FLUID_SOURCE_WIDTH
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.canPlacePipes
import de.bjrke.factorio.optimizer.common.layout.endName
import de.bjrke.factorio.optimizer.common.layout.entity.BeltLikeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.hasTunnel
import de.bjrke.factorio.optimizer.common.layout.markTunnel
import de.bjrke.factorio.optimizer.common.layout.placeEnd
import de.bjrke.factorio.optimizer.common.layout.placeFluidSource
import de.bjrke.factorio.optimizer.common.layout.placePipe
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.LineSide
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.south
import de.bjrke.factorio.optimizer.common.plan.FluidFeed
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import kotlin.math.abs

/** What placing a lane achieved: the ports it reaches, and the ports it had no room for. */
internal class PlacedLane(val ports: List<Coordinate>, val skipped: List<Coordinate>)

/**
 * Puts a [FluidLane] on the board and sources it: the junction pipes of the lane row, the
 * **horizontal** runs across the gaps between them, a **vertical** shaft down to every machine port
 * and the inline marker west of the lane head.
 *
 * The horizontal run across a two-tile gap is a pair of directly adjacent ends (`pUUp`): the player
 * walks over the run instead of around a surface pipe. A shaft's top end stands one row inside the
 * lane and takes its fluid out of the junction above it; its bottom end lands on the port tile just
 * outside the machine and gives it there. A shaft passes under whatever already stands in its
 * column — a belt, an inserter, plain ground — so the row between the belts and the machines needs
 * no free tile. A port whose column is taken is **left out**: the rest of the lane is still built,
 * and only that port falls back to the routed feed.
 */
internal class FluidLanePlacer(
    private val board: LayoutBoard,
    private val imports: ImportExtension,
    private val beltEnd: BeltEnd,
) {

    /**
     * Places [lane] for [material] and the marker that sources it. Returns null when the lane row
     * itself or the marker block is taken.
     */
    fun place(
        lane: FluidLane,
        material: Material,
        side: LineSide,
        sink: Boolean = false,
    ): PlacedLane? {
        val laneTiles = lane.surface + lane.runs.flatMap { (from, to) -> listOf(from, to) }
        if (!laneTiles.all { board.canPlacePipes(it, material) }) return null
        val source = sourceEnd(lane.head, material) ?: return null
        val buried =
            lane.shafts.filter { (top, bottom) -> shaftFits(top, bottom, material) }.toSet()

        for (tile in lane.surface) board.placePipe(tile, material)
        for ((from, to) in lane.runs) {
            val direction = if (to.x > from.x) EAST else WEST
            board.placeEnd(material, from, direction, INPUT)
            board.placeEnd(material, to, direction, OUTPUT)
            board.markTunnel(from, to, board.endName(material))
        }
        for ((top, bottom) in lane.shafts) {
            if ((top to bottom) !in buried) continue
            board.placeEnd(material, top, Direction.SOUTH, INPUT)
            board.placeEnd(material, bottom, Direction.SOUTH, OUTPUT)
            board.markTunnel(top, bottom, board.endName(material))
        }
        placeSource(source, material, sink)
        // the marker is placed inline for now; the import trimmer moves it to the solid marker
        // column and joins it to the head, so one west column serves every import
        imports.fluidFeeds +=
            FluidFeed(material, lane.laneRow, lane.head, side == LineSide.NORTH, source, sink)
        imports.markerRows += lane.laneRow
        // the machine port is the end that touches the machine: the bottom of a north lane, the top
        // of a south lane
        val reached =
            lane.shafts
                .filter { (top, bottom) -> (top to bottom) in buried }
                .map { (top, bottom) -> if (side == LineSide.NORTH) bottom else top }
        val skipped =
            lane.ports.filter { port -> reached.none { it.x == port.x && it.y == port.y } }
        return PlacedLane(reached, skipped)
    }

    /**
     * The eastmost tile of the marker block west of the lane [head], or null when the block does
     * not fit. The block is one tile for an infinity chest and three tiles otherwise, ending one
     * tile west of the head so the lane is fed straight east.
     */
    private fun sourceEnd(head: Coordinate, material: Material): Coordinate? {
        val width = if (beltEnd == BeltEnd.INFINITY_CHEST) 1 else FLUID_SOURCE_WIDTH
        val end = Coordinate(head.x - 1, head.y)
        val tiles = (end.x - width + 1..end.x).map { Coordinate(it, head.y) }
        return end.takeIf { tiles.all { tile -> board.canPlacePipes(tile, material) } }
    }

    /**
     * Places the marker block west of its lane, ending at [end]. A [sink] lane drains its fluid, so
     * its cheat pipe is unfiltered and deletes what reaches it.
     */
    private fun placeSource(end: Coordinate, material: Material, sink: Boolean) {
        board.placeFluidSource(beltEnd, end, material, sink)
        val westBound = if (beltEnd == BeltEnd.INFINITY_CHEST) end.x else end.x + 1
        // the lane head is where the import extension may not be compacted over
        imports.compactionWestBound = maxOf(imports.compactionWestBound, westBound)
    }

    /**
     * Whether a shaft from [top] to [bottom] can pass: the top end is free, the bottom end may take
     * a pipe of [material] (free and not touching another fluid), and every tile between is a belt
     * row, the inserter row or plain ground that no other run already occupies.
     */
    private fun shaftFits(top: Coordinate, bottom: Coordinate, material: Material): Boolean {
        if (!board.isFree(top) || !board.canPlacePipes(bottom, material)) return false
        val buried = board.endName(material)
        return (1 until abs(bottom.y - top.y)).all { offset ->
            val tile = top.south(offset)
            canBury(board.entityAt(tile)) && !board.hasTunnel(tile, Coordinate.south, buried)
        }
    }

    /**
     * A run passes under whatever already stands between the lane and the port: the input belts,
     * the inserters on the machine edge, a surface pipe and any ground. A belt carries its items on
     * top of the ground, so a pipe goes beneath it just as it goes beneath an inserter, and the run
     * moves nothing of the row. A pipe-to-ground end carries its own underground run along its
     * direction, so a vertical shaft may only cross one that runs on the other axis.
     */
    private fun canBury(entity: LayoutEntity?) =
        entity == null ||
            entity is InserterEntity ||
            entity is PipeEntity ||
            entity is BeltLikeEntity ||
            (entity is PipeToGroundEntity && (entity.direction == EAST || entity.direction == WEST))
}
