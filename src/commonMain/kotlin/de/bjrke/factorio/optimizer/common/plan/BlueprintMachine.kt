package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.prototype.Factory

/**
 * Footprint of [factory]. The width bounds how many inserters (and therefore direct-insertion
 * children) fit on one edge. Every machine the table does not name falls back to the assembling
 * machine's 3×3 size.
 */
fun blueprintMachineFootprint(factory: Factory): Dimension =
    when (factory) {
        Factory.STONE_FURNACE,
        Factory.STEEL_FURNACE -> Dimension(2, 2)
        Factory.RECYCLER -> Dimension(4, 2)
        Factory.ELECTROMAGNETIC_PLANT -> Dimension(4, 4)
        Factory.FOUNDRY,
        Factory.OIL_REFINERY,
        Factory.CRYOGENIC_PLANT -> Dimension(5, 5)
        Factory.ROCKET_SILO -> Dimension(9, 9)
        else -> Dimension(3, 3)
    }

/** Which way a machine's fluid port flows: it only takes fluid, only gives it, or does both. */
enum class PortFlow {
    INPUT,
    OUTPUT,
    BOTH,
}

/**
 * A fluid connection of a machine: the tile just outside it (relative to the machine's top-left
 * tile) where a pipe has to sit, and which way it flows.
 */
data class FluidPort(val tile: Coordinate, val flow: PortFlow) {
    /**
     * Shorthand for a port at tile (`x`, `y`), so the table below reads as a list of coordinates.
     */
    constructor(x: Int, y: Int, flow: PortFlow) : this(Coordinate(x, y), flow)
}

/**
 * The direction the blueprint places [factory] in. The row feeds machines from the north (or south)
 * lanes and drains them onto the belt below, so a machine is turned until its prototype's inputs
 * face a lane side and its outputs face the other:
 *
 * - the cryogenic plant, foundry and oil refinery take fluid on the south edge and give it on the
 *   north edge, so they are turned 180°;
 * - the electromagnetic plant's input boxes sit west/east and its output boxes north/south; 90°
 *   clockwise moves one input north and the other south, leaving both outputs on the side edges;
 * - the other machines already take on the north edge and give on the south edge.
 */
fun blueprintMachineDirection(factory: Factory): Direction =
    when (factory) {
        Factory.CRYOGENIC_PLANT,
        Factory.FOUNDRY,
        Factory.OIL_REFINERY -> Direction.SOUTH
        Factory.ELECTROMAGNETIC_PLANT -> Direction.EAST
        else -> Direction.NORTH
    }

/**
 * The machine's pipe connections, already in the orientation [blueprintMachineDirection] places it
 * in, so callers use the tiles as they are (the blueprint still turns the machine entity itself).
 *
 * The list order is load-bearing: the inputs come first, in the order the recipe's fluid
 * ingredients take them, then the outputs in the order of its fluid results. Machines whose
 * prototype facing differs from the blueprint facing therefore list a hand-checked order instead of
 * a rotated, coordinate-sorted one.
 *
 * The `data.raw` dump in `resources/prototypes` drops `pipe_connections` and `fluid_boxes` (see
 * `sanitizePrototypeDump`), so the curated table is the source of truth; it is verified against the
 * Wube prototype definitions, not the dump.
 */
fun prototypeFluidPorts(factory: Factory): List<FluidPort> =
    when (factory) {
        Factory.ASSEMBLING_MACHINE_2,
        Factory.ASSEMBLING_MACHINE_3 ->
            listOf(FluidPort(1, -1, PortFlow.INPUT), FluidPort(1, 3, PortFlow.OUTPUT))
        // the chemical plant and the biochamber have both fluids on the top corners and both
        // outputs on the bottom corners
        Factory.CHEMICAL_PLANT,
        Factory.BIOCHAMBER ->
            listOf(
                FluidPort(0, -1, PortFlow.INPUT),
                FluidPort(2, -1, PortFlow.INPUT),
                FluidPort(0, 3, PortFlow.OUTPUT),
                FluidPort(2, 3, PortFlow.OUTPUT),
            )
        // the oil refinery's prototype takes on the bottom edge and gives on the top edge; after
        // the 180° turn it takes on the north edge and gives on the south edge
        Factory.OIL_REFINERY ->
            listOf(
                FluidPort(3, -1, PortFlow.INPUT),
                FluidPort(1, -1, PortFlow.INPUT),
                FluidPort(4, 5, PortFlow.OUTPUT),
                FluidPort(2, 5, PortFlow.OUTPUT),
                FluidPort(0, 5, PortFlow.OUTPUT),
            )
        // the foundry's prototype takes on the bottom edge and gives on the top edge; after the
        // 180° turn it takes on the north edge and gives on the south edge
        Factory.FOUNDRY ->
            listOf(
                FluidPort(3, -1, PortFlow.INPUT),
                FluidPort(1, -1, PortFlow.INPUT),
                FluidPort(1, 5, PortFlow.OUTPUT),
                FluidPort(3, 5, PortFlow.OUTPUT),
            )
        // the cryogenic plant's prototype takes on the bottom edge and gives on the top edge;
        // after the 180° turn it takes on the north edge and gives on the south edge
        Factory.CRYOGENIC_PLANT ->
            listOf(
                FluidPort(2, -1, PortFlow.INPUT),
                FluidPort(0, -1, PortFlow.INPUT),
                FluidPort(4, -1, PortFlow.INPUT),
                FluidPort(0, 5, PortFlow.OUTPUT),
                FluidPort(2, 5, PortFlow.OUTPUT),
                FluidPort(4, 5, PortFlow.OUTPUT),
            )
        // the electromagnetic plant's input boxes sit west/east and its output boxes north/south;
        // after the 90° clockwise turn one input faces north and the other south, and both
        // outputs sit on the side edges
        Factory.ELECTROMAGNETIC_PLANT ->
            listOf(
                FluidPort(1, -1, PortFlow.INPUT),
                FluidPort(2, 4, PortFlow.INPUT),
                FluidPort(-1, 2, PortFlow.OUTPUT),
                FluidPort(4, 1, PortFlow.OUTPUT),
            )

        else -> emptyList()
    }

/**
 * The side of a machine that [port] sits on, as a [Direction], read from the footprints in
 * [blueprintMachineFootprint] rather than hand-listed: a port is a tile just outside the machine,
 * so its coordinate names the edge it is next to (`x == -1` west, `x == width` east, `y == -1`
 * north, `y == height` south).
 */
fun blueprintMachineEdge(factory: Factory, port: FluidPort): Direction {
    val (width, height) = blueprintMachineFootprint(factory)
    val (x, y) = port.tile
    return when (x) {
        -1 -> Direction.WEST
        width -> Direction.EAST
        else ->
            when (y) {
                -1 -> Direction.NORTH
                height -> Direction.SOUTH
                else -> error("${factory.identifier} has no port at ${port.tile}")
            }
    }
}

/**
 * The flow [port] carries on [edge], read from the prototype table. [edge] must be the side the
 * port's tile sits on; an edge the port does not touch keeps [PortFlow.BOTH], which is only the
 * total fallback for a caller that passes a wrong side.
 */
fun blueprintMachineEdgeFlow(factory: Factory, port: FluidPort, edge: Direction): PortFlow {
    val (x, y) = port.tile
    val (width, height) = blueprintMachineFootprint(factory)
    val onEdge =
        when (edge) {
            Direction.NORTH -> y == -1
            Direction.SOUTH -> y == height
            Direction.WEST -> x == -1
            Direction.EAST -> x == width
        }
    return if (onEdge) port.flow else PortFlow.BOTH
}

/**
 * The port a fluid enters [factory] through, if any: the first input port in [prototypeFluidPorts].
 * A machine may list more than one such pipe (the chemical plant's two top corners, the foundry's
 * two top pipes), but a row feeds it through one — the table order is the recipe's fluid order. A
 * further fluid moves on to the next port.
 */
fun blueprintMachineInputPort(factory: Factory): FluidPort? =
    prototypeFluidPorts(factory).firstOrNull { isInputPort(factory, it) }

/**
 * The port a fluid leaves [factory] through, if any: the first output port in
 * [prototypeFluidPorts], which is the recipe's first fluid result.
 */
fun blueprintMachineOutputPort(factory: Factory): FluidPort? =
    prototypeFluidPorts(factory).firstOrNull { isOutputPort(factory, it) }

/** Whether [port] is an input in [prototypeFluidPorts], by [blueprintMachineEdgeFlow]. */
internal fun isInputPort(factory: Factory, port: FluidPort) =
    blueprintMachineEdgeFlow(factory, port, blueprintMachineEdge(factory, port)) != PortFlow.OUTPUT

/** Whether [port] is an output in [prototypeFluidPorts], by [blueprintMachineEdgeFlow]. */
internal fun isOutputPort(factory: Factory, port: FluidPort) =
    blueprintMachineEdgeFlow(factory, port, blueprintMachineEdge(factory, port)) != PortFlow.INPUT
