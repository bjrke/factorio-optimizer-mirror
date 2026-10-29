package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.placeInserter
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.prototypeFluidPorts
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
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
        val production = machine.production
        val ports = portColumns(machine)
        placeInputInserters(row, machine, production, ports.top)
        placeOutputInserters(row, machine, production, internal, ports.bottom)
    }

    /**
     * A fluid pipe sits on the machine edge just outside its port tile; keep those tiles free of
     * inserters so the fluid can actually connect. A recipe that uses no fluid keeps the original
     * inserter spread, ports and all. The columns are relative to the machine, so the row is not
     * needed: every copy of a row keeps the same ones.
     */
    private fun portColumns(machine: PlacedMachine): PortColumns {
        val production = machine.production
        if (!usesFluid(production)) return PortColumns(emptySet(), emptySet())
        val recipe = production.recipe
        return inserterPortColumns(
            production.factory,
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
        production: FactoryProduction,
        topPorts: Set<Int>,
    ) {
        val ingredients = production.recipe.ingredients
        val near = row.solids.getOrNull(0)?.takeIf { it in ingredients }
        val far = row.solids.getOrNull(1)?.takeIf { it in ingredients }
        val width = row.width

        // Inserters take precedence over poles: fill columns 1..width-1 first and keep column 0 as
        // the spare, so the edge only gives up an inserter when it is truly full. A fluid port may
        // claim one of the preferred columns; the pattern is sized to the columns that remain.
        val freeColumns = (1 until width).plus(0).minus(topPorts)

        val pattern =
            planner.inserterPattern(
                near?.let { planner.insertersFor(production, listOf(it)) } ?: 0,
                far?.let { planner.insertersFor(production, listOf(it)) } ?: 0,
                freeColumns.size,
            )

        val insertColumns = freeColumns.take(pattern.size)
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
        production: FactoryProduction,
        internal: List<Material>,
        bottomPorts: Set<Int>,
    ) {
        val recipe = production.recipe
        val freeBottomColumns = (0 until row.width).filterNot(bottomPorts::contains)
        val productColumn =
            freeBottomColumns.minByOrNull { abs(it - row.width / 2) } ?: (row.width / 2)
        val belowPreferred = if (row.width == 2) 0 else row.width - 1
        val belowColumn =
            freeBottomColumns
                .filter { it != productColumn }
                .minByOrNull { abs(it - belowPreferred) } ?: belowPreferred
        // the product is inserted onto the belt below the row
        externalProduct(production, internal, logistics)?.let {
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

    private fun usesFluid(production: FactoryProduction) =
        production.recipe.ingredients.keys.any { logistics.isFluid(it) } ||
            production.recipe.result.keys.any { logistics.isFluid(it) }
}

/**
 * The fluid-port columns of one machine: [PortColumns.top] keeps the ports on the north edge free,
 * [PortColumns.bottom] those on the south edge. Only the ports the recipe's fluid counts use are
 * reserved: the chemical plant's unused top corner stays available for a long inserter. The ports
 * come from the same [prototypeFluidPorts] table the fluid feed reads, so the two stages cannot
 * drift apart; a side port is routed and does not take an inserter column here.
 */
internal fun inserterPortColumns(
    factory: Factory,
    inputFluids: Int,
    outputFluids: Int,
): PortColumns {
    val horizontal =
        prototypeFluidPorts(factory).filter {
            blueprintMachineEdge(factory, it) in listOf(NORTH, SOUTH)
        }
    // the recipe assigns its fluids to the horizontal ports in table order, one port per fluid
    val used =
        horizontal.filter { it.flow != PortFlow.OUTPUT }.take(inputFluids) +
            horizontal.filter { it.flow != PortFlow.INPUT }.take(outputFluids)
    fun columns(edge: Direction) =
        used
            .filter { blueprintMachineEdge(factory, it) == edge }
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
    production: FactoryProduction,
    internal: List<Material>,
    logistics: LogisticsCalculator,
) = production.recipe.result.keys.firstOrNull { !logistics.isFluid(it) && it !in internal }
