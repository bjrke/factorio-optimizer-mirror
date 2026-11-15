package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.TunnelPlan
import de.bjrke.factorio.optimizer.common.blueprint.layout.canPlacePipes
import de.bjrke.factorio.optimizer.common.blueprint.layout.endName
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltLikeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.fluidSourceTiles
import de.bjrke.factorio.optimizer.common.blueprint.layout.hasTunnel
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeFluidSource
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeFluidSourceLink
import de.bjrke.factorio.optimizer.common.blueprint.layout.placePipe
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeTunnel
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeTunnelPlan
import de.bjrke.factorio.optimizer.common.blueprint.layout.tunnelPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.FluidFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.core.model.south
import de.bjrke.factorio.optimizer.common.core.model.west
import kotlin.math.abs

/**
 * A lane whose horizontal runs are planned once, so the fit check and the placement share the same
 * tiles, including the surface tiles a run longer than the pipe's reach splits into.
 */
internal class PlannedLane(
    val lane: FluidLane,
    val material: Material,
    val runs: List<TunnelPlan>,
) {

    /** Every tile the lane claims on the surface: its junctions and both ends of every run. */
    val tiles = lane.surface + runs.flatMap { it.tiles }
}

/**
 * Puts a [FluidLane] on the board and sources it: the junction pipes of the lane row, the
 * **horizontal** runs across the gaps between them, a **vertical** shaft down to every machine port
 * and the shared source block west of the lane head, joined by its own underground run.
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
     * Places [planned]'s lane and the shared source block ending at [sourceEnd], returning the
     * machine ports the lane reaches. Returns null when the lane row, the source block or its
     * connection run is taken.
     */
    fun place(
        planned: PlannedLane,
        side: LineSide,
        io: Io,
        sourceEnd: Coordinate,
    ): List<Coordinate>? {
        if (!fits(planned, sourceEnd)) return null
        val lane = planned.lane
        val buried =
            lane.shafts
                .filter { (top, bottom) -> shaftFits(top, bottom, planned.material) }
                .toList()

        for (tile in lane.surface) board.placePipe(tile, planned.material)
        for (run in planned.runs) board.placeTunnelPlan(run)

        for ((top, bottom) in buried) {
            board.placeTunnel(planned.material, top, bottom)
        }
        placeSource(lane.head, sourceEnd, planned.material, io)
        imports.fluidFeeds += FluidFeed(planned.material, lane.laneRow, lane.head, sourceEnd, io)
        imports.markerRows += lane.laneRow
        // the machine port is the end that touches the machine: the bottom of a north lane, the top
        // of a south lane
        return buried.map { (top, bottom) -> if (side == LineSide.NORTH) bottom else top }
    }

    /** Plans [lane]'s runs once, so every [fits] check of one lane reuses the same tiles. */
    fun plan(lane: FluidLane, material: Material) =
        PlannedLane(
            lane,
            material,
            lane.runs.map { (from, to) -> board.tunnelPlan(material, from, to) },
        )

    /**
     * Whether [planned]'s lane can take its source block ending at [sourceEnd]: the lane tiles, the
     * source block and the run from the block's east side to the head all fit. The run may pass
     * under a belt, so a mixed belt's U-turn does not block a source block placed west of it.
     */
    fun fits(planned: PlannedLane, sourceEnd: Coordinate): Boolean {
        val lane = planned.lane
        val material = planned.material
        // ALWAYS leave room for a pair of touching pipe-to-ground ends east of the source block: a
        // belt may stand between their buried runs, and two ends beat a short surface pipe.
        if (sourceEnd.x > lane.head.x - 3) return false
        if (!planned.tiles.all { board.canPlacePipes(it, material) }) {
            return false
        }
        if (!fluidSourceTiles(sourceEnd, beltEnd).all { board.canPlacePipes(it, material) }) {
            return false
        }
        val from = sourceEnd.east()
        val to = lane.head.west()
        if (from.x > to.x) return true
        val link = board.tunnelPlan(material, from, to)
        if (!link.tiles.all { board.canPlacePipes(it, material) }) return false
        val buried = board.endName(material)
        return link.runs.all { (start, end) ->
            val step = if (end.x > start.x) Coordinate.east else Coordinate.west
            (1 until abs(end.x - start.x)).all { offset ->
                val tile = start + step * offset
                canBury(board.entityAt(tile)) && !board.hasTunnel(tile, step, buried)
            }
        }
    }

    /**
     * Places the shared source block ending at [end] and the run from its east side to the lane
     * [head]. An [io] of [Io.OUTPUT] drains its fluid, so its cheat pipe is unfiltered and deletes
     * what reaches it.
     */
    private fun placeSource(head: Coordinate, end: Coordinate, material: Material, io: Io) {
        board.placeFluidSource(beltEnd, end, material, io)
        board.placeFluidSourceLink(material, end, head)
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
