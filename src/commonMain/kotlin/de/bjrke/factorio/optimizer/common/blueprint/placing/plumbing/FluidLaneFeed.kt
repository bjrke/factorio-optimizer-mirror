package de.bjrke.factorio.optimizer.common.blueprint.placing.plumbing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.FluidLane
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.FluidLanePlacer
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LANE_MARGIN
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.PlacedMachine
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.PlannedLane
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.Row
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowFluidLane
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * The east edge of the fluid marker column as the lane planner first places it, near the lane head
 * so the block exists before routing. [ImportTrimmer] later joins the block to the shared import
 * column; a fluid marker is a label, so it shares the solid marker's east edge and reaches less far
 * west. When a source block or its connection run does not fit at this column, [FluidLaneFeed]
 * reduces it west instead of dropping a lane, and the search also stops three tiles west of its
 * lane head so the connection is a pair of touching underground ends rather than a short surface
 * pipe.
 */
private const val IMPORT_MARKER_EAST = LANE_MARGIN - 2

/** A placed drain lane joined to the feed lane of the same fluid on the same row. */
internal class LaneConnection(
    val material: Material,
    /** The east end of the drain lane, where the link starts. */
    val drainTail: Coordinate,
    /** The east end of the feed lane, where the link ends. */
    val feedTail: Coordinate,
)

/**
 * The lane side of fluid feeding: which machines a row's lane serves and which port tile each of
 * them gets. A lane carries one fluid of a row over its whole width; every port it lands on is fed
 * by the lane, every other port of the row is left to the routed feed. A fluid that is both an
 * input and an output gets two lanes, one feeding and one draining; [connections] reports the pairs
 * the layout joins east of the row.
 *
 * Every lane's source block joins the shared import column of the solid belt markers: a fluid
 * marker is a label, so it shares the solid marker's east edge. The block is placed before routing
 * so it reserves its own tiles, and a pipe-to-ground run joins its east side to the lane head. The
 * run tunnels under a belt, so a mixed belt's U-turn west of the head no longer makes the lane
 * unplaceable.
 */
internal class FluidLaneFeed(
    board: LayoutBoard,
    private val imports: ImportExtension,
    beltEnd: BeltEnd,
    private val logistics: LogisticsCalculator,
    private val failures: MutableList<String>,
) {
    private val placer = FluidLanePlacer(board, imports, beltEnd)
    private val model = RowFluidLane(logistics)

    /** The port tiles the lanes carry a fluid to, and the fluid each of them carries. */
    private val laneFeeds = mutableMapOf<Coordinate, Material>()

    /** Every lane this feed placed, so a drain can be joined to its feed afterwards. */
    private val placedLanes = mutableListOf<PlacedLaneRecord>()

    private class PlacedLaneRecord(
        val row: Row,
        val material: Material,
        val io: Io,
        val tail: Coordinate,
    )

    private class LaneSpec(
        val row: Row,
        val io: Io,
        val side: LineSide,
        val planned: PlannedLane,
    )

    /**
     * Places a lane for every fluid of every row that gets one. A further fluid on the same edge
     * gets the next lane out, so a machine's second top/bottom port is fed like the first instead
     * of by a route.
     */
    fun place(rows: List<Row>) {
        val specs = buildList {
            for (side in LineSide.entries) {
                for (row in rows) {
                    for ((depth, fluid) in model.fluids(row, side).withIndex()) {
                        val laneRow = model.laneRow(row, side, depth)
                        val lane = FluidLane.of(laneRow, fluid.ports, side)
                        add(LaneSpec(row, fluid.io, side, placer.plan(lane, fluid.material)))
                    }
                }
            }
        }
        if (specs.isEmpty()) return

        val markerEast = sharedMarkerEast(specs)
        imports.fluidMarkerEast = markerEast
        for (spec in specs) {
            val end = Coordinate(markerEast, spec.planned.lane.laneRow)
            // a shared column keeps every import marker east aligned; a lane that still cannot
            // place reports a drain instead of silently losing its only outlet
            val placed =
                placer.place(spec.planned, spec.side, spec.io, end)
                    ?: run {
                        if (spec.io == Io.OUTPUT) {
                            failures +=
                                "${spec.planned.material.name} drain lane on row " +
                                    "${spec.planned.lane.laneRow} (no room for a source block)"
                        }
                        continue
                    }
            for (port in placed) laneFeeds[port] = spec.planned.material
            placedLanes +=
                PlacedLaneRecord(
                    spec.row,
                    spec.planned.material,
                    spec.io,
                    spec.planned.lane.surface.last(),
                )
        }
    }

    /**
     * The eastmost shared marker column where every lane's source block and connection run fit,
     * starting at [IMPORT_MARKER_EAST] and stepping west, because only west of a mixed belt's
     * U-turn can the connection run cross it. The search never steps west of [IMPORT_FEED], the
     * column every import and every route starts from, so a block with nothing better left stands
     * there uncompacted; the eastmost candidate is the fallback, so the search always yields a
     * column for [FluidLanePlacer.place] to check.
     */
    private fun sharedMarkerEast(specs: List<LaneSpec>): Int {
        val start = minOf(IMPORT_MARKER_EAST, specs.minOf { it.planned.lane.head.x - 3 })
        return (start downTo IMPORT_FEED).firstOrNull { east ->
            specs.all { spec ->
                placer.fits(spec.planned, Coordinate(east, spec.planned.lane.laneRow))
            }
        } ?: start
    }

    /**
     * The east ends of every drain lane and the feed lane of the same fluid on the same row, so the
     * caller can route the product back into the input.
     */
    fun connections() =
        placedLanes
            .groupBy { it.row to it.material }
            .flatMap { (_, lanes) ->
                val feeds = lanes.filter { it.io == Io.INPUT }
                val drains = lanes.filter { it.io == Io.OUTPUT }
                drains.flatMap { drain ->
                    feeds.map { feed -> LaneConnection(drain.material, drain.tail, feed.tail) }
                }
            }
            .distinctBy { it.material to it.drainTail to it.feedTail }

    /**
     * The port tile the lane of [row] feeds [material] to (or drains it from) for [machine], or
     * null when the machine's port of that flow is not reached by a lane: the port is taken, or the
     * lane of that fluid found no room.
     */
    fun portFor(
        row: Row,
        machine: PlacedMachine,
        material: Material,
        io: Io,
    ) = model.assignedPort(row, machine, material, io)?.takeIf { laneFeeds[it] == material }

    /** The fluids [machine] takes, in recipe order: the order the lane hands its ports out in. */
    fun liquidsOf(machine: PlacedMachine): List<Material> =
        machine.production.recipe.ingredients.keys.filter { logistics.isFluid(it) }
}
