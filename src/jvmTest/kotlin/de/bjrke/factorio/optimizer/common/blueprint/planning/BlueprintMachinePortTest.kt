package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineDirection
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdgeFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineInputPort
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineOutputPort
import de.bjrke.factorio.optimizer.common.plan.prototypeFluidPorts
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
 * The prototype dump in `resources/prototypes` cannot verify the ports: `sanitizePrototypeDump`
 * keeps only curated fields and none of them is a `pipe_connections` or `fluid_boxes`, so the dump
 * carries no pipe-connection data at all. The table therefore rests on the machines' real prototype
 * definitions: [prototypeFluidPorts] lists the prototype's tiles and flows already turned into the
 * orientation [blueprintMachineDirection] places the machine in. Every machine with a port table is
 * covered here, including the 180° placement of the cryogenic plant, foundry and oil refinery and
 * the 90° placement of the electromagnetic plant.
 */
class BlueprintMachinePortTest {

    /** A port with the side it sits on and the flow that side gives it. */
    private data class EdgePort(val tile: Coordinate, val edge: Direction, val flow: PortFlow)

    private val machines =
        Factory.entries.filter { prototypeFluidPorts(it.identifier).isNotEmpty() }

    private fun edgePorts(identifier: String) =
        prototypeFluidPorts(identifier).map {
            val edge = blueprintMachineEdge(identifier, it)
            EdgePort(it.tile, edge, blueprintMachineEdgeFlow(identifier, it, edge))
        }

    @Test
    fun everyInputPortFacesAHorizontalLaneSide() =
        assertTable(machines) { factory ->
            val ports = edgePorts(factory.identifier)
            assertAll(
                // an input the row cannot reach is a fluid the machine never gets
                {
                    assertEquals(
                        emptyList(),
                        ports.filter {
                            it.flow == PortFlow.INPUT &&
                                it.edge != Direction.NORTH &&
                                it.edge != Direction.SOUTH
                        },
                        "${factory.identifier} has an input port no lane can reach",
                    )
                },
                {
                    // a machine that gives fluid must offer a drain port
                    assertEquals(
                        true,
                        ports.any { it.flow != PortFlow.INPUT },
                        "${factory.identifier} has no port to drain it",
                    )
                },
            )
        }

    @Test
    fun eachMachineIsTurnedUntilItsInputsFaceALaneSide() =
        assertTableOf(
            "cryogenic-plant" to Direction.SOUTH,
            "foundry" to Direction.SOUTH,
            "oil-refinery" to Direction.SOUTH,
            "electromagnetic-plant" to Direction.EAST,
        ) { (identifier, direction) ->
            assertEquals(direction, blueprintMachineDirection(identifier))
        }

    @Test
    fun theOtherMachinesKeepThePrototypeFacing() =
        assertTableOf(
            "assembling-machine-2",
            "assembling-machine-3",
            "chemical-plant",
            "biochamber",
        ) { identifier ->
            assertEquals(Direction.NORTH, blueprintMachineDirection(identifier))
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
    fun theTurnedMachinesTakeOnTheLaneSideAndGiveOnTheOther() =
        assertTableOf(
            "cryogenic-plant" to
                listOf(
                    Coordinate(0, -1) to PortFlow.INPUT,
                    Coordinate(2, -1) to PortFlow.INPUT,
                    Coordinate(4, -1) to PortFlow.INPUT,
                    Coordinate(0, 5) to PortFlow.OUTPUT,
                    Coordinate(2, 5) to PortFlow.OUTPUT,
                    Coordinate(4, 5) to PortFlow.OUTPUT,
                ),
            "foundry" to
                listOf(
                    Coordinate(1, -1) to PortFlow.INPUT,
                    Coordinate(3, -1) to PortFlow.INPUT,
                    Coordinate(1, 5) to PortFlow.OUTPUT,
                    Coordinate(3, 5) to PortFlow.OUTPUT,
                ),
            "oil-refinery" to
                listOf(
                    Coordinate(1, -1) to PortFlow.INPUT,
                    Coordinate(3, -1) to PortFlow.INPUT,
                    Coordinate(0, 5) to PortFlow.OUTPUT,
                    Coordinate(2, 5) to PortFlow.OUTPUT,
                    Coordinate(4, 5) to PortFlow.OUTPUT,
                ),
            "electromagnetic-plant" to
                listOf(
                    Coordinate(1, -1) to PortFlow.INPUT,
                    Coordinate(2, 4) to PortFlow.INPUT,
                    Coordinate(-1, 2) to PortFlow.OUTPUT,
                    Coordinate(4, 1) to PortFlow.OUTPUT,
                ),
        ) { (identifier, expected) ->
            assertEquals(
                expected.toSet(),
                prototypeFluidPorts(identifier).map { it.tile to it.flow }.toSet(),
            )
        }

    /**
     * The feed and the placer each pick one port per direction: the feed's input port feeds and the
     * placer's output port drains, and both are ports the table really lists.
     */
    @Test
    fun theChosenPortsAreOnePerDirectionAndComeFromTheTable() =
        assertTable(machines) { factory ->
            val identifier = factory.identifier
            val ports = prototypeFluidPorts(identifier)
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
