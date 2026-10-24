package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.FluidRowLayout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.fluidRowPlan
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdgeFlow
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
    fun thePlacerReadsEachPortOnTheEdgeTheTablePutsIt() =
        assertTable(machines) { factory ->
            val identifier = factory.identifier
            val columns = inserterPortColumns(identifier)
            // the columns the port table itself implies, derived here independently of the helper
            fun expected(edge: Direction, feeding: PortFlow) =
                blueprintMachineFluidPorts(identifier)
                    .filter { port ->
                        val flow = blueprintMachineEdgeFlow(identifier, port, edge)
                        blueprintMachineEdge(identifier, port) == edge &&
                            (flow == feeding || flow == PortFlow.BOTH)
                    }
                    .mapTo(mutableSetOf()) { port -> port.tile.x }
            assertEquals(
                PortColumns(
                    expected(Direction.NORTH, PortFlow.INPUT),
                    expected(Direction.SOUTH, PortFlow.OUTPUT),
                ),
                columns,
                "$identifier keeps the wrong inserter columns",
            )
        }

    @Test
    fun theLayoutKeepsEveryTopFeedPortFreeOfInserters() =
        assertTable(machines) { factory ->
            val plan = fluidRowPlan(factory)
            val row = FluidRowLayout(plan)
            val machine = row.machines.single()
            val ports = inserterPortColumns(factory.identifier).top
            // the input inserters sit on the row one above the machine, on the lane columns
            val blocked = ports.map { Coordinate(machine.x + it, machine.y - 1) }
            assertEquals(
                emptyList(),
                blocked.filter { it in row.inserters },
                "${factory.identifier} puts an input inserter on its fluid port",
            )
        }
}
