package de.bjrke.factorio.optimizer.common.plumbing

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.LineSide
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.planning.FluidLanePlacer
import de.bjrke.factorio.optimizer.common.planning.PlacedMachine
import de.bjrke.factorio.optimizer.common.planning.Row
import de.bjrke.factorio.optimizer.common.planning.RowFluidLane

/**
 * The lane side of fluid feeding: which machines a row's lane serves and which port tile each of
 * them gets. A lane carries one fluid of a row over its whole width; every port it lands on is fed
 * by the lane, every other port of the row is left to the routed feed.
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

    /**
     * Places a lane for every fluid of every row that gets one for [side] and returns the ports the
     * lanes could not reach, left to the routed feed. A further fluid on the same edge gets the
     * next lane out, so a machine's second top/bottom port is fed like the first instead of by a
     * route.
     */
    fun place(side: LineSide, rows: List<Row>): Map<Coordinate, Material> {
        val unreached = mutableMapOf<Coordinate, Material>()
        for (row in rows) {
            for ((depth, material) in model.fluids(row, side).withIndex()) {
                val lane = model.lane(row, side, material, depth) ?: continue
                val placed =
                    placer.place(lane, material, side, sink = model.isOutput(row, material))
                        ?: continue
                for (port in placed.ports) laneFeeds[port] = material
                for (skipped in placed.skipped) unreached[skipped] = material
            }
        }
        return unreached
    }

    /**
     * The port tile the lane of [row] feeds [material] to for [machine], or null when the machine's
     * port of that fluid is not reached by a lane: the port is taken, or the lane of that fluid
     * found no room.
     */
    fun portFor(row: Row, machine: PlacedMachine, material: Material): Coordinate? =
        model.assignedPort(row, machine, material)?.takeIf { laneFeeds[it] == material }

    /** The fluids [machine] takes, in recipe order: the order the lane hands its ports out in. */
    fun liquidsOf(machine: PlacedMachine): List<Material> =
        machine.production.recipe.ingredients.keys.filter { logistics.isFluid(it) }
}
