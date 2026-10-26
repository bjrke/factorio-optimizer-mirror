package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts

/**
 * The fluid lane of one row: the shape that feeds (or drains) every machine of the row through a
 * port on one of the row's horizontal edges. The line runs on a row **beyond** the belts, so no
 * belt moves and no inserter is touched: the row between the belts and the machines is left alone,
 * and each port is reached by a **vertical** shaft that dives past the belts and the inserter row
 * and lands on the port tile just outside the machine.
 *
 * A port is assigned to the recipe's fluids in recipe order: the first fluid input takes the first
 * input port, the second the next one, and the same for the outputs. The side a lane runs on is
 * read from that assignment, not from a fixed "north feeds, south drains", so a machine whose
 * prototype feeds from the south (after the blueprint turns it) is still served. A row gets no lane
 * when a machine has no port for that material on the side. A machine that takes two fluids on the
 * same edge gets one lane for the first and keeps its second port for the route.
 */
internal class RowFluidLane(private val logistics: LogisticsCalculator) {

    /** The materials [row] can take or give through a port on [side], in recipe order. */
    fun fluids(row: Row, side: LineSide): List<Material> {
        if (row.interfaces.isNotEmpty()) return emptyList()
        val first = row.machines.firstOrNull() ?: return emptyList()
        val candidates = inputMaterials(first) + outputMaterials(first)
        return candidates.filter { material ->
            row.machines.all { machine -> portFor(row, machine, material, side) != null }
        }
    }

    /**
     * The port tile of [machine] the lane of [side] carries [material] to: the machine's ports on
     * that edge in recipe order, one per fluid. Null when that port is not on [side].
     */
    fun portFor(
        row: Row,
        machine: PlacedMachine,
        material: Material,
        side: LineSide,
    ): Coordinate? {
        val port = assignedPort(row, machine, material) ?: return null
        return port.takeIf { sideOf(row, machine, it) == side }
    }

    /** The lane that serves [row] on [side] for [material], or null when the row gets no lane. */
    fun lane(row: Row, side: LineSide, material: Material): FluidLane? {
        if (material !in fluids(row, side)) return null
        val laneRow = laneRow(row, side) ?: return null
        val ports = row.machines.mapNotNull { machine -> portFor(row, machine, material, side) }
        if (ports.size != row.machines.size) return null
        return FluidLane.of(laneRow, ports.sortedBy { it.x }, side)
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

    /** The port the recipe assigns [material] to, whichever row edge that port is on. */
    internal fun assignedPort(row: Row, machine: PlacedMachine, material: Material): Coordinate? {
        val identifier = machine.plannedRecipe.factory.identifier
        val ports = blueprintMachineFluidPorts(identifier)
        val inputIndex = inputMaterials(machine).indexOf(material)
        if (inputIndex >= 0) {
            return ports
                .filter { isInput(it.flow) }
                .getOrNull(inputIndex)
                ?.let { Coordinate(machine.left + it.tile.x, row.factoryTop + it.tile.y) }
        }
        val outputIndex = outputMaterials(machine).indexOf(material)
        if (outputIndex < 0) return null
        return ports
            .filter { isOutput(it.flow) }
            .getOrNull(outputIndex)
            ?.let { Coordinate(machine.left + it.tile.x, row.factoryTop + it.tile.y) }
    }

    /** Whether [material] is an output of [row]'s recipe, so its lane drains instead of feeds. */
    fun isOutput(row: Row, material: Material): Boolean =
        row.machines.firstOrNull()?.let { material in outputMaterials(it) } == true

    /** The fluid inputs of [machine]'s recipe, in recipe order. */
    private fun inputMaterials(machine: PlacedMachine): List<Material> =
        machine.plannedRecipe.recipe.ingredients.keys.filter { logistics.isFluid(it) }

    /** The fluid outputs of [machine]'s recipe, in recipe order. */
    private fun outputMaterials(machine: PlacedMachine): List<Material> =
        machine.plannedRecipe.recipe.result.keys.filter { logistics.isFluid(it) }

    private fun isInput(flow: PortFlow) = flow != PortFlow.OUTPUT

    private fun isOutput(flow: PortFlow) = flow != PortFlow.INPUT

    /** The horizontal edge [tile] sits on for [machine], or null when it is a side edge. */
    private fun sideOf(row: Row, machine: PlacedMachine, tile: Coordinate): LineSide? {
        val identifier = machine.plannedRecipe.factory.identifier
        val size = blueprintMachineFluidPorts(identifier)
        val relative = Coordinate(tile.x - machine.left, tile.y - row.factoryTop)
        val port = size.firstOrNull { it.tile == relative } ?: return null
        return when (blueprintMachineEdge(identifier, port)) {
            Direction.NORTH -> LineSide.NORTH
            Direction.SOUTH -> LineSide.SOUTH
            else -> null
        }
    }
}
