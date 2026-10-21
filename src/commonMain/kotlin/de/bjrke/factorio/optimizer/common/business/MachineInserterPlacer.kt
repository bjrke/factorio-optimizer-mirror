package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Material
import kotlin.math.abs

/**
 * Places the input, product and third-lane inserters of every machine in a row. The fluid-port
 * columns and the two inserter ends are separate steps, so a machine edge can be read without
 * following the whole placement in one method.
 */
internal class MachineInserterPlacer(
    private val board: LayoutBoard,
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val planner: RowPlanner,
) {

    /**
     * The fluid-port columns of one machine: the top edge takes the lanes, the bottom the product.
     */
    private data class PortColumns(val top: Set<Int>, val bottom: Set<Int>)

    fun place(row: Row, internal: Set<Material>) {
        for (machine in row.machines) placeMachine(row, machine, internal)
    }

    private fun placeMachine(row: Row, machine: PlacedMachine, internal: Set<Material>) {
        val allocation = plan.recipes[machine.allocation]
        val ports = portColumns(row, machine)
        placeInputInserters(row, machine, allocation, ports.top)
        placeOutputInserters(row, machine, allocation, internal, ports.bottom)
    }

    /**
     * A fluid pipe sits on the machine edge just outside its port tile; keep those tiles free of
     * inserters so the fluid can actually connect. A recipe that uses no fluid keeps the original
     * inserter spread, ports and all.
     */
    private fun portColumns(row: Row, machine: PlacedMachine): PortColumns {
        val allocation = plan.recipes[machine.allocation]
        if (!usesFluid(allocation)) return PortColumns(emptySet(), emptySet())
        val ports = blueprintMachineFluidPorts(allocation.factory.identifier)
        return PortColumns(
            ports.filter { it.tile.y == -1 }.mapTo(mutableSetOf()) { it.tile.x },
            ports.filter { it.tile.y == row.height }.mapTo(mutableSetOf()) { it.tile.x },
        )
    }

    /**
     * A short inserter reaches the near lane one tile up, a long one the far lane two tiles up; a
     * lane whose rate needs more than one inserter gets several, spread over the edge.
     */
    private fun placeInputInserters(
        row: Row,
        machine: PlacedMachine,
        allocation: PlannedRecipe,
        topPorts: Set<Int>,
    ) {
        val recipe = allocation.recipe
        val near = row.solids.getOrNull(0)?.takeIf { it in recipe.ingredients }
        val far = row.solids.getOrNull(1)?.takeIf { it in recipe.ingredients }
        val pattern =
            planner.inserterPattern(
                near?.let { planner.insertersFor(allocation, listOf(it)) } ?: 0,
                far?.let { planner.insertersFor(allocation, listOf(it)) } ?: 0,
                row.width,
            )
        // the machine's last column is left free for a pole; a port there stays free for the pipe
        val insertColumns = (0 until maxOf(1, row.width - 1)).filterNot(topPorts::contains)
        insertColumns.zip(pattern).forEach { (column, long) ->
            board.placeInserter(
                Coordinate(machine.left + column, row.factoryTop - 1),
                NORTH,
                if (long) row.solids[1] else row.solids[0],
                long,
            )
        }
    }

    /** The product inserter and the extra lane's long inserter keep off the bottom fluid ports. */
    private fun placeOutputInserters(
        row: Row,
        machine: PlacedMachine,
        allocation: PlannedRecipe,
        internal: Set<Material>,
        bottomPorts: Set<Int>,
    ) {
        val recipe = allocation.recipe
        val freeBottomColumns = (0 until row.width).filterNot(bottomPorts::contains)
        val productColumn =
            freeBottomColumns.minByOrNull { abs(it - row.width / 2) } ?: (row.width / 2)
        val belowPreferred = if (row.width == 2) 0 else row.width - 1
        val belowColumn =
            freeBottomColumns
                .filter { it != productColumn }
                .minByOrNull { abs(it - belowPreferred) } ?: belowPreferred
        // the product is inserted onto the belt below the row
        externalProduct(allocation, internal, logistics)?.let {
            board.placeInserter(
                Coordinate(machine.left + productColumn, row.factoryBottom + 1),
                NORTH,
                it,
                false,
            )
        }
        // a long inserter reaches over the output belt to a third input belt below the row
        if (row.laneBelowRow != null && row.solids.getOrNull(2) in recipe.ingredients) {
            board.placeInserter(
                Coordinate(machine.left + belowColumn, row.factoryBottom + 1),
                SOUTH,
                row.solids[2],
                true,
            )
        }
    }

    private fun usesFluid(allocation: PlannedRecipe) =
        allocation.recipe.ingredients.keys.any { logistics.isFluid(it) } ||
            allocation.recipe.result.keys.any { logistics.isFluid(it) }
}

/**
 * The material a machine's product leaves the row with: its solid result, unless it is internal.
 */
internal fun externalProduct(
    allocation: PlannedRecipe,
    internal: Set<Material>,
    logistics: LogisticsCalculator,
): Material? =
    allocation.recipe.result.keys.firstOrNull { !logistics.isFluid(it) && it !in internal }
