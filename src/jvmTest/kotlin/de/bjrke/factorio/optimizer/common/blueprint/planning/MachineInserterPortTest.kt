package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.FluidRowLayout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.fluidRowPlan
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts
import de.bjrke.factorio.optimizer.common.planning.PortColumns
import de.bjrke.factorio.optimizer.common.planning.inserterPortColumns
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The inserter placer must read the fluid-port table the way the row feeds and drains: no inserter
 * on a top port the machine takes fluid through, and none on a bottom port it gives fluid through.
 * The placer derives its columns from [inserterPortColumns], so this suite pins that derivation
 * against the table itself and against the inserters the layout really places.
 */
class MachineInserterPortTest {

    /** Every machine with a port table; a machine without one has no fluid columns to keep. */
    private val machines =
        Factory.entries.filter { blueprintMachineFluidPorts(it.identifier).isNotEmpty() }

    @Test
    fun thePlacerKeepsOnlyThePortsTheRecipeReallyUses() =
        assertTable(machines) { factory ->
            val identifier = factory.identifier
            val columns = inserterPortColumns(identifier, inputFluids = 1, outputFluids = 0)
            // the single water input takes the first horizontal input port, in x order
            val used =
                blueprintMachineFluidPorts(identifier)
                    .filter {
                        blueprintMachineEdge(identifier, it) in
                            listOf(Direction.NORTH, Direction.SOUTH)
                    }
                    .filter { it.flow != PortFlow.OUTPUT }
                    .take(1)
            fun expected(edge: Direction) =
                used
                    .filter { blueprintMachineEdge(identifier, it) == edge }
                    .mapTo(mutableSetOf()) {
                        it.tile.x
                    }
            assertEquals(
                PortColumns(expected(Direction.NORTH), expected(Direction.SOUTH)),
                columns,
                "$identifier keeps the wrong inserter columns",
            )
        }

    @Test
    fun theLayoutKeepsEveryUsedFeedPortFreeOfInserters() =
        assertTable(machines) { factory ->
            val plan = fluidRowPlan(factory)
            val row = FluidRowLayout(plan)
            val machine = row.machines.single()
            val columns = inserterPortColumns(factory.identifier, inputFluids = 1, outputFluids = 0)
            val blocked =
                (columns.top.map { Coordinate(machine.x + it, machine.y - 1) } +
                    columns.bottom.map { Coordinate(machine.x + it, machine.y + machine.height) })
            assertEquals(
                emptyList(),
                blocked.filter { it in row.inserters },
                "${factory.identifier} puts an inserter on a used fluid port",
            )
        }
}
