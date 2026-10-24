package de.bjrke.factorio.optimizer.common.plumbing

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdgeFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts
import de.bjrke.factorio.optimizer.common.planning.FluidLanePlacer
import de.bjrke.factorio.optimizer.common.planning.LineSide
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

    /** The port tiles the lanes carry a fluid to, and the fluid each of them carries. */
    private val laneFeeds = mutableMapOf<Coordinate, Material>()

    /**
     * Places the lane of every row that gets one for [side], for the fluid it carries, and returns
     * the ports the lanes could not reach, left to the routed feed.
     */
    fun place(lanes: RowFluidLane, side: LineSide, rows: List<Row>): Map<Coordinate, Material> {
        val unreached = mutableMapOf<Coordinate, Material>()
        for (row in rows) {
            val material = lanes.fluids(row, side).firstOrNull() ?: continue
            val lane = lanes.lane(row, side, material) ?: continue
            // the marker may not run into the import extension west of the row
            val placed = placer.place(lane, material, row.left, side)
            if (placed == null) continue
            for (port in placed.ports) laneFeeds[port] = material
            for (skipped in placed.skipped) unreached[skipped] = material
        }
        return unreached
    }

    /**
     * The port tile the lane of [row] feeds [material] to for [machine], or null when the machine's
     * port of that fluid is not reached by a lane: the port is taken, or it is the machine's second
     * port of the same edge, which the one lane of the row does not serve.
     */
    fun portFor(row: Row, machine: PlacedMachine, material: Material): Coordinate? {
        val fluids = liquidsOf(machine)
        val index = fluids.indexOf(material)
        if (index < 0) return null
        val port = inletPorts(row, machine).getOrNull(index) ?: return null
        return port.takeIf { laneFeeds[it] == material }
    }

    /** The fluids [machine] takes, by name: the order the lane hands its ports out in. */
    fun liquidsOf(machine: PlacedMachine): List<Material> =
        machine.plannedRecipe.recipe.ingredients.keys
            .filter { logistics.isFluid(it) }
            .sortedBy { it.name }

    /** The machine's inlet ports on the edge the row's lanes feed, in the port table's order. */
    private fun inletPorts(row: Row, machine: PlacedMachine): List<Coordinate> {
        val identifier = machine.plannedRecipe.factory.identifier
        return blueprintMachineFluidPorts(identifier)
            .filter { port -> port.tile.y < 0 }
            .filter {
                blueprintMachineEdgeFlow(
                    identifier,
                    it,
                    blueprintMachineEdge(identifier, it),
                ) != PortFlow.OUTPUT
            }
            .map { port -> Coordinate(machine.left + port.tile.x, row.factoryTop + port.tile.y) }
    }
}
