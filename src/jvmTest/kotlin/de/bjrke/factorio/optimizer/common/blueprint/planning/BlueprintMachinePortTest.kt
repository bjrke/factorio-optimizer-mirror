package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.plan.FluidPort
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdgeFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineInputPort
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineOutputPort
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * The machine fluid-port table and its edges.
 *
 * The prototype dump in `resources/prototypes` cannot verify the edges: `sanitizePrototypeDump`
 * keeps only curated fields and none of them is a `pipe_connections` or `fluid_boxes`, so the dump
 * carries no pipe-connection data at all. The table therefore rests on two things: the port tiles
 * match the machines' known pipe layout, and the flows follow the row geometry — the inserter
 * placer feeds from the lanes above (the top edge) and drains onto the product belt below (the
 * bottom edge), so a one-way port on the top or west edge is an input and one on the bottom or east
 * edge an output. Every machine with a port table is covered here.
 *
 * [PortFlow.BOTH] is its own input *and* output side: the electromagnetic plant's pipes stand on
 * every side, so a row can feed or drain it from whichever side reaches it.
 */
class BlueprintMachinePortTest {

    /** A port with the side it sits on and the flow that side gives it. */
    private data class EdgePort(val tile: Coordinate, val edge: Direction, val flow: PortFlow)

    private val machines =
        Factory.entries.filter { blueprintMachineFluidPorts(it.identifier).isNotEmpty() }

    private fun edgePorts(identifier: String) =
        blueprintMachineFluidPorts(identifier).map {
            val edge = blueprintMachineEdge(identifier, it)
            EdgePort(it.tile, edge, blueprintMachineEdgeFlow(identifier, it, edge))
        }

    @Test
    fun everyInputPortFacesTheLanesAboveAndEveryOutputItsBeltBelow() =
        assertTable(machines) { factory ->
            val ports = edgePorts(factory.identifier)
            assertAll(
                // an input the row cannot reach is a fluid the machine never gets
                {
                    assertEquals(
                        emptyList(),
                        ports.filter { it.flow == PortFlow.INPUT && it.edge != Direction.NORTH },
                        "${factory.identifier} has an input port the row's lanes cannot reach",
                    )
                },
                {
                    assertEquals(
                        emptyList(),
                        ports.filter {
                            it.flow == PortFlow.OUTPUT && it.edge != Direction.SOUTH
                        },
                        "${factory.identifier} has an output port the product belt cannot reach",
                    )
                },
                {
                    // a both-way machine has no one-way port, so check it offers at least one
                    assertEquals(
                        true,
                        ports.any { it.flow != PortFlow.OUTPUT },
                        "${factory.identifier} has no port to feed it",
                    )
                },
            )
        }

    @Test
    fun everyPortSitsOnTheEdgeOfItsFootprint() =
        assertTable(machines) { factory ->
            val identifier = factory.identifier
            val size: Dimension = blueprintMachineFootprint(identifier)
            assertTable(edgePorts(identifier)) { port ->
                val onEdge =
                    when (port.edge) {
                        Direction.NORTH -> port.tile.y == -1 && port.tile.x in 0 until size.width
                        Direction.SOUTH ->
                            port.tile.y == size.height && port.tile.x in 0 until size.width
                        Direction.WEST -> port.tile.x == -1 && port.tile.y in 0 until size.height
                        Direction.EAST ->
                            port.tile.x == size.width && port.tile.y in 0 until size.height
                    }
                assertEquals(true, onEdge, "$identifier has a $port.edge port at ${port.tile}")
            }
        }

    @Test
    fun aOneWayFlowFollowsTheRowReading() =
        assertTable(machines) { factory ->
            assertTable(edgePorts(factory.identifier)) { port ->
                if (port.flow == PortFlow.BOTH) return@assertTable
                val expected =
                    if (port.edge == Direction.NORTH || port.edge == Direction.WEST) PortFlow.INPUT
                    else PortFlow.OUTPUT
                assertEquals(
                    expected,
                    port.flow,
                    "${factory.identifier} ${port.tile} on ${port.edge}",
                )
            }
        }

    @Test
    fun twoPortsPerFlowKeepBothPipesInTheTable() =
        assertTableOf(
            "chemical-plant" to
                listOf(Coordinate(0, -1), Coordinate(2, -1), Coordinate(0, 3), Coordinate(2, 3)),
            "cryogenic-plant" to
                listOf(
                    Coordinate(0, 5),
                    Coordinate(2, 5),
                    Coordinate(4, 5),
                    Coordinate(0, -1),
                    Coordinate(2, -1),
                    Coordinate(4, -1),
                ),
            "oil-refinery" to
                listOf(
                    Coordinate(1, 5),
                    Coordinate(3, 5),
                    Coordinate(0, -1),
                    Coordinate(2, -1),
                    Coordinate(4, -1),
                ),
        ) { (identifier, tiles) ->
            assertEquals(tiles, blueprintMachineFluidPorts(identifier).map(FluidPort::tile))
        }

    /**
     * The feed and the placer each pick one port per direction: the feed's input port feeds and the
     * placer's output port drains, and both are ports the table really lists.
     */
    @Test
    fun theChosenPortsAreOnePerDirectionAndComeFromTheTable() =
        assertTable(machines) { factory ->
            val identifier = factory.identifier
            val ports = blueprintMachineFluidPorts(identifier)
            val input = blueprintMachineInputPort(identifier)
            val output = blueprintMachineOutputPort(identifier)
            assertAll(
                { assertContains(ports, input) },
                {
                    val feed = assertNotNull(input, "$identifier has no port to feed it")
                    val flow =
                        blueprintMachineEdgeFlow(
                            identifier,
                            feed,
                            blueprintMachineEdge(identifier, feed),
                        )
                    assertNotEquals(PortFlow.OUTPUT, flow, "$identifier's feed port is an output")
                },
                {
                    // a machine that gives fluid must have a drain port that the table lists
                    if (ports.any { it.flow != PortFlow.INPUT }) {
                        assertNotNull(output, "$identifier gives fluid but has no output port")
                        assertContains(ports, output)
                    }
                },
            )
        }
}
