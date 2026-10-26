package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.placeInserter
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRecipe
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts
import kotlin.math.abs

/**
 * Places the input, product and third-lane inserters of every machine in a row. The fluid-port
 * columns and the two inserter ends are separate steps, so a machine edge can be read without
 * following the whole placement in one method.
 */
internal class MachineInserterPlacer(
    private val board: LayoutBoard,
    private val logistics: LogisticsCalculator,
    private val planner: RowPlanner,
) {

    fun place(row: Row, internal: List<Material>) {
        for (machine in row.machines) placeMachine(row, machine, internal)
    }

    private fun placeMachine(row: Row, machine: PlacedMachine, internal: List<Material>) {
        val plannedRecipe = machine.plannedRecipe
        val ports = portColumns(machine)
        placeInputInserters(row, machine, plannedRecipe, ports.top)
        placeOutputInserters(row, machine, plannedRecipe, internal, ports.bottom)
    }

    /**
     * A fluid pipe sits on the machine edge just outside its port tile; keep those tiles free of
     * inserters so the fluid can actually connect. A recipe that uses no fluid keeps the original
     * inserter spread, ports and all. The columns are relative to the machine, so the row is not
     * needed: every copy of a row keeps the same ones.
     */
    private fun portColumns(machine: PlacedMachine): PortColumns {
        val plannedRecipe = machine.plannedRecipe
        if (!usesFluid(plannedRecipe)) return PortColumns(emptySet(), emptySet())
        val recipe = plannedRecipe.recipe
        return inserterPortColumns(
            plannedRecipe.factory.identifier,
            recipe.ingredients.keys.count { logistics.isFluid(it) },
            recipe.result.keys.count { logistics.isFluid(it) },
        )
    }

    /**
     * A short inserter reaches the near lane one tile up, a long one the far lane two tiles up; a
     * lane whose rate needs more than one inserter gets several, spread over the edge.
     */
    private fun placeInputInserters(
        row: Row,
        machine: PlacedMachine,
        plannedRecipe: PlannedRecipe,
        topPorts: Set<Int>,
    ) {
        val recipe = plannedRecipe.recipe
        val near = row.solids.getOrNull(0)?.takeIf { it in recipe.ingredients }
        val far = row.solids.getOrNull(1)?.takeIf { it in recipe.ingredients }
        val pattern =
            planner.inserterPattern(
                near?.let { planner.insertersFor(plannedRecipe, listOf(it)) } ?: 0,
                far?.let { planner.insertersFor(plannedRecipe, listOf(it)) } ?: 0,
                row.width,
            )
        // the machine's last column is left free for a pole; a fluid port may take one of the
        // remaining columns, and the long inserter must not fall off the edge with it: use the
        // pole column as the spare slot before dropping an inserter
        val preferred = (0 until maxOf(1, row.width - 1)).filterNot(topPorts::contains)
        val spare = (0 until row.width).filterNot(topPorts::contains).filterNot(preferred::contains)
        val insertColumns = (preferred + spare).take(pattern.size)
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
        plannedRecipe: PlannedRecipe,
        internal: List<Material>,
        bottomPorts: Set<Int>,
    ) {
        val recipe = plannedRecipe.recipe
        val freeBottomColumns = (0 until row.width).filterNot(bottomPorts::contains)
        val productColumn =
            freeBottomColumns.minByOrNull { abs(it - row.width / 2) } ?: (row.width / 2)
        val belowPreferred = if (row.width == 2) 0 else row.width - 1
        val belowColumn =
            freeBottomColumns
                .filter { it != productColumn }
                .minByOrNull { abs(it - belowPreferred) } ?: belowPreferred
        // the product is inserted onto the belt below the row
        externalProduct(plannedRecipe, internal, logistics)?.let {
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

    private fun usesFluid(plannedRecipe: PlannedRecipe) =
        plannedRecipe.recipe.ingredients.keys.any { logistics.isFluid(it) } ||
            plannedRecipe.recipe.result.keys.any { logistics.isFluid(it) }
}

/**
 * The fluid-port columns of one machine: [PortColumns.top] keeps the ports on the north edge free,
 * [PortColumns.bottom] those on the south edge. Only the ports the recipe's fluid counts use are
 * reserved: the chemical plant's unused top corner stays available for a long inserter. The ports
 * come from the same turned table the fluid feed reads, so the two stages cannot drift apart; a
 * side port is routed and does not take an inserter column here.
 */
internal fun inserterPortColumns(
    identifier: String,
    inputFluids: Int,
    outputFluids: Int,
): PortColumns {
    val horizontal =
        blueprintMachineFluidPorts(identifier).filter {
            blueprintMachineEdge(identifier, it) in listOf(NORTH, SOUTH)
        }
    // the recipe assigns its fluids to the horizontal ports in x order, one port per fluid
    val used =
        horizontal.filter { it.flow != PortFlow.OUTPUT }.take(inputFluids) +
            horizontal.filter { it.flow != PortFlow.INPUT }.take(outputFluids)
    fun columns(edge: Direction) =
        used
            .filter { blueprintMachineEdge(identifier, it) == edge }
            .mapTo(mutableSetOf()) {
                it.tile.x
            }
    return PortColumns(columns(NORTH), columns(SOUTH))
}

/** The inserter columns to keep free of a machine: the top feed and the bottom drain ports. */
internal data class PortColumns(val top: Set<Int>, val bottom: Set<Int>)

/**
 * The material a machine's product leaves the row with: its solid result, unless it is internal.
 */
internal fun externalProduct(
    plannedRecipe: PlannedRecipe,
    internal: List<Material>,
    logistics: LogisticsCalculator,
): Material? =
    plannedRecipe.recipe.result.keys.firstOrNull { !logistics.isFluid(it) && it !in internal }
