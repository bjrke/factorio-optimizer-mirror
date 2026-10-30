package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.placeInserter
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.LineSide
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

    fun place(row: Row) {
        for (machine in row.machines) placeMachine(row, machine)
    }

    private fun placeMachine(row: Row, machine: PlacedMachine) {
        val ports = portColumns(machine)
        placeBeltInserters(row, machine, LineSide.NORTH, ports)
        placeBeltInserters(row, machine, LineSide.SOUTH, ports)
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
    private fun placeBeltInserters(
        row: Row,
        machine: PlacedMachine,
        side: LineSide,
        ports: PortColumns,
    ) {
        val production = machine.production
        val ingredients = production.recipe.ingredients
        val results = production.recipe.result
        val sideDirection =
            when (side) {
                LineSide.NORTH -> NORTH
                LineSide.SOUTH -> SOUTH
            }

        // Inserters take precedence over poles: fill columns 1 until width first and keep column 0
        // as the spare, so the edge only gives up an inserter when it is truly full. A fluid port
        // may claim one of the preferred columns; the pattern is sized to the columns that remain.
        val freeColumns = (1 until row.width).plus(0).minus(ports.getSide(side))

        val inserterRow = row.inserterRow(side)

        val amounts =
            row.belts
                .filter { it.side == side }
                .associateWith { belt ->
                    val recipesMaterials =
                        when (belt.io) {
                            Io.INPUT -> ingredients
                            Io.OUTPUT -> results
                        }
                    planner.insertersFor(
                        production,
                        belt.io,
                        recipesMaterials[belt.material] ?: 0.0,
                    )
                }

        val pattern = planner.inserterPattern(amounts, freeColumns.size)

        freeColumns.asSequence().zip(pattern).forEach { (column, belt) ->
            val long = abs(inserterRow - belt.y) > 1
            val coordinate = Coordinate(machine.left + column, inserterRow)
            val ioDirection =
                when (belt.io) {
                    Io.OUTPUT -> sideDirection.opposite()
                    Io.INPUT -> sideDirection
                }

            board.placeInserter(
                coordinate,
                ioDirection,
                belt.material,
                long,
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
internal data class PortColumns(val top: Set<Int>, val bottom: Set<Int>) {
    fun getSide(side: LineSide) =
        when (side) {
            LineSide.NORTH -> top
            LineSide.SOUTH -> bottom
        }
}
