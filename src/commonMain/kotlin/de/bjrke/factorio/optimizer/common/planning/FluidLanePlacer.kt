package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.canPlacePipes
import de.bjrke.factorio.optimizer.common.layout.endName
import de.bjrke.factorio.optimizer.common.layout.entity.BeltLikeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.layout.hasTunnel
import de.bjrke.factorio.optimizer.common.layout.markTunnel
import de.bjrke.factorio.optimizer.common.layout.placeEnd
import de.bjrke.factorio.optimizer.common.layout.placeInfinityPipe
import de.bjrke.factorio.optimizer.common.layout.placeInfinityPipeSink
import de.bjrke.factorio.optimizer.common.layout.placeMarker
import de.bjrke.factorio.optimizer.common.layout.placePipe
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.south
import de.bjrke.factorio.optimizer.common.plan.FluidFeed
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import kotlin.math.abs

/** The tiles of the inline marker west of a lane head: input end, marker and output end. */
private const val MARKER_WIDTH = 3

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
     * Places [lane] for [material] and the marker that sources it, never west of [westOf]. Returns
     * null when the lane row itself or the marker stretch is taken.
     */
    fun place(
        lane: FluidLane,
        material: Material,
        westOf: Int,
        side: LineSide,
        sink: Boolean = false,
    ): PlacedLane? {
        val laneTiles = lane.surface + lane.runs.flatMap { (from, to) -> listOf(from, to) }
        if (!laneTiles.all { board.canPlacePipes(it, material) }) return null
        val source = sourceTiles(lane.head, material, westOf) ?: return null
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
            FluidFeed(material, lane.laneRow, lane.head, side == LineSide.NORTH, source)
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
     * The marker tiles west of the lane [head], or null when the stretch is taken. The block sits
     * inline on the lane's own row, so the lane is fed straight east with no turn: an input end, a
     * marker and an output end for a constant combinator, a single infinity pipe otherwise. It
     * never moves west of [westOf], where the import lanes run.
     */
    private fun sourceTiles(head: Coordinate, material: Material, westOf: Int): List<Coordinate>? {
        val end = head.x - 1
        val start = if (beltEnd == BeltEnd.INFINITY_CHEST) end else end - MARKER_WIDTH + 1
        val column =
            (start downTo minOf(westOf, head.x - MARKER_WIDTH)).firstOrNull { candidate ->
                (candidate until head.x).all {
                    board.canPlacePipes(Coordinate(it, head.y), material)
                }
            } ?: return null
        return (column until head.x).map { Coordinate(it, head.y) }
    }

    /**
     * Places the marker block west of its lane: either the `oCo` pair or one infinity pipe. A
     * [sink] lane drains its fluid, so its cheat pipe is unfiltered and deletes what reaches it.
     */
    private fun placeSource(tiles: List<Coordinate>, material: Material, sink: Boolean) {
        if (beltEnd == BeltEnd.INFINITY_CHEST) {
            val source = tiles.single()
            if (sink) board.placeInfinityPipeSink(source, material)
            else board.placeInfinityPipe(source, material)
            imports.compactionWestBound = maxOf(imports.compactionWestBound, source.x)
            return
        }
        val input = tiles.first()
        val output = tiles.last()
        board.placeEnd(material, input, EAST, INPUT)
        board.placeMarker(tiles[1], Signal(material, fluid = true))
        board.placeEnd(material, output, EAST, OUTPUT)
        board.markTunnel(input, output, board.endName(material))
        // the lane head is where the import extension may not be compacted over
        imports.compactionWestBound = maxOf(imports.compactionWestBound, output.x + 1)
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
     * the inserters on the machine edge and any ground. A belt carries its items on top of the
     * ground, so a pipe goes beneath it just as it goes beneath an inserter, and the run moves
     * nothing of the row.
     */
    private fun canBury(entity: LayoutEntity?) =
        entity == null ||
            entity is InserterEntity ||
            entity is PipeEntity ||
            entity is BeltLikeEntity
}
