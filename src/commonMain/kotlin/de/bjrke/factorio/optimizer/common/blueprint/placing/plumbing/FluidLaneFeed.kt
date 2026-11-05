package de.bjrke.factorio.optimizer.common.blueprint.placing.plumbing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.FluidLane
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.FluidLanePlacer
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.PlacedMachine
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.Row
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowFluidLane
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material

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
 */
internal class FluidLaneFeed(
    board: LayoutBoard,
    imports: ImportExtension,
    beltEnd: BeltEnd,
    private val logistics: LogisticsCalculator,
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

    /**
     * Places a lane for every fluid of every row that gets one for [side] and returns the ports the
     * lanes could not reach, left to the routed feed. A further fluid on the same edge gets the
     * next lane out, so a machine's second top/bottom port is fed like the first instead of by a
     * route.
     */
    fun place(side: LineSide, rows: List<Row>): Map<Coordinate, Material> {
        val unreached = mutableMapOf<Coordinate, Material>()
        for (row in rows) {
            for ((depth, fluid) in model.fluids(row, side).withIndex()) {
                val laneRow = model.laneRow(row, side, depth)
                val lane = FluidLane.of(laneRow, fluid.ports, side)
                val placed = placer.place(lane, fluid.material, side, fluid.io) ?: continue
                for (port in placed.ports) laneFeeds[port] = fluid.material
                for (skipped in placed.skipped) unreached[skipped] = fluid.material
                placedLanes += PlacedLaneRecord(row, fluid.material, fluid.io, lane.surface.last())
            }
        }
        return unreached
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
