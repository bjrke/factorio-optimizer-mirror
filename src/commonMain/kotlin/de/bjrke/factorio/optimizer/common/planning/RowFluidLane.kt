package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdgeFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts

/**
 * The fluid lane of one row: the shape that feeds (or drains) every machine of the row through
 * ports the table puts on the row's north (or south) edge. The line runs on a row **beyond** the
 * belts, so no belt moves and no inserter is touched: the row between the belts and the machines is
 * left alone, and each port is reached by a **vertical** shaft that dives past the belts and the
 * inserter row and lands on the port tile just outside the machine.
 *
 * A row gets no lane when a machine touches no port on that edge. Those machines keep the routed
 * feed. A machine that takes two fluids on the same edge gets one lane for the first and keeps its
 * second port for the route.
 */
internal class RowFluidLane(private val logistics: LogisticsCalculator) {

    /** The fluids [row] takes (north) or gives (south) through a port on that edge, by name. */
    fun fluids(row: Row, side: LineSide): List<Material> {
        if (row.interfaces.isNotEmpty()) return emptyList()
        if (row.machines.any { portsOf(row, it, side).isEmpty() }) return emptyList()
        return row.machines
            .flatMap { machine -> materialsOf(machine, side) }
            .distinct()
            .sortedBy { it.name }
    }

    /**
     * The port tile of [machine] the lane of [side] carries [material] to: the machine's ports on
     * that edge in table order, one per fluid.
     */
    fun portFor(
        row: Row,
        machine: PlacedMachine,
        material: Material,
        side: LineSide,
    ): Coordinate? {
        val index = materialsOf(machine, side).indexOf(material)
        if (index < 0) return null
        return portsOf(row, machine, side).getOrNull(index)
    }

    /** The lane that serves [row] on [side] for [material], or null when the row gets no lane. */
    fun lane(row: Row, side: LineSide, material: Material): FluidLane? {
        if (material !in fluids(row, side)) return null
        val laneRow = laneRow(row, side) ?: return null
        val ports = row.machines.mapNotNull { machine -> portFor(row, machine, material, side) }
        if (ports.size != row.machines.size) return null
        return FluidLane.of(laneRow, ports, side)
    }

    /**
     * The row the lane runs on: two rows beyond the outermost belt the row already uses, so the row
     * between holds the shafts' lane-side ends. It is never between a belt and the machines.
     */
    fun laneRow(row: Row, side: LineSide): Int? {
        val above =
            listOfNotNull(row.laneNearRow, row.laneFarRow).minOrNull() ?: (row.factoryTop - 2)
        // the product belt always sits under the machines, even when it carries no material
        val below = row.beltRows.plus(row.outputLaneRow).max()
        return when (side) {
            LineSide.NORTH -> above - 2
            LineSide.SOUTH -> below + 2
        }
    }

    /** The machine port tiles of [machine] on [side], in the order the port table lists them. */
    private fun portsOf(row: Row, machine: PlacedMachine, side: LineSide): List<Coordinate> {
        val identifier = machine.plannedRecipe.factory.identifier
        val edge = if (side == LineSide.NORTH) Direction.NORTH else Direction.SOUTH
        val sideFlow = if (side == LineSide.NORTH) PortFlow.INPUT else PortFlow.OUTPUT
        return blueprintMachineFluidPorts(identifier)
            .filter { port ->
                val flow = blueprintMachineEdgeFlow(identifier, port, edge)
                blueprintMachineEdge(identifier, port) == edge &&
                    (flow == sideFlow || flow == PortFlow.BOTH)
            }
            .map { port -> Coordinate(machine.left + port.tile.x, row.factoryTop + port.tile.y) }
    }

    /** The fluids [machine] takes (north) or gives (south), by name: the lane's port order. */
    private fun materialsOf(machine: PlacedMachine, side: LineSide): List<Material> {
        val plannedRecipe = machine.plannedRecipe
        val materials =
            if (side == LineSide.NORTH) plannedRecipe.recipe.ingredients.keys
            else plannedRecipe.recipe.result.keys
        return materials.filter { logistics.isFluid(it) }.sortedBy { it.name }
    }
}
