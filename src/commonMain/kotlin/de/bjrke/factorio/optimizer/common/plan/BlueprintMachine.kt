package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Direction

/**
 * Footprint of the machine that runs a recipe, keyed by its prototype name. The width bounds how
 * many inserters (and therefore direct-insertion children) fit on one edge. Unknown machines fall
 * back to the assembling-machine size.
 */
fun blueprintMachineFootprint(identifier: String): Dimension =
    when (identifier) {
        "stone-furnace",
        "steel-furnace" -> Dimension(2, 2)
        "recycler" -> Dimension(4, 2)
        "electromagnetic-plant" -> Dimension(4, 4)
        "foundry",
        "oil-refinery",
        "cryogenic-plant" -> Dimension(5, 5)
        "rocket-silo" -> Dimension(9, 9)
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
 * The direction the blueprint places [identifier] in. The row feeds machines from the north (or
 * south) lanes and drains them onto the belt below, so a machine is turned until its prototype's
 * inputs face a lane side and its outputs face the other:
 *
 * - the cryogenic plant, foundry and oil refinery take fluid on the south edge and give it on the
 *   north edge, so they are turned 180°;
 * - the electromagnetic plant's input boxes sit west/east and its output boxes north/south; 90°
 *   clockwise moves one input north and the other south, leaving both outputs on the side edges;
 * - the other machines already take on the north edge and give on the south edge.
 */
fun blueprintMachineDirection(identifier: String): Direction =
    when (identifier) {
        "cryogenic-plant",
        "foundry",
        "oil-refinery" -> Direction.SOUTH
        "electromagnetic-plant" -> Direction.EAST
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
fun prototypeFluidPorts(identifier: String): List<FluidPort> =
    when (identifier) {
        "assembling-machine-2",
        "assembling-machine-3" ->
            listOf(FluidPort(1, -1, PortFlow.INPUT), FluidPort(1, 3, PortFlow.OUTPUT))
        // the chemical plant and the biochamber have both fluids on the top corners and both
        // outputs on the bottom corners
        "chemical-plant",
        "biochamber" ->
            listOf(
                FluidPort(0, -1, PortFlow.INPUT),
                FluidPort(2, -1, PortFlow.INPUT),
                FluidPort(0, 3, PortFlow.OUTPUT),
                FluidPort(2, 3, PortFlow.OUTPUT),
            )
        // the oil refinery's prototype takes on the bottom edge and gives on the top edge; after
        // the 180° turn it takes on the north edge and gives on the south edge
        "oil-refinery" ->
            listOf(
                FluidPort(3, -1, PortFlow.INPUT),
                FluidPort(1, -1, PortFlow.INPUT),
                FluidPort(4, 5, PortFlow.OUTPUT),
                FluidPort(2, 5, PortFlow.OUTPUT),
                FluidPort(0, 5, PortFlow.OUTPUT),
            )
        // the foundry's prototype takes on the bottom edge and gives on the top edge; after the
        // 180° turn it takes on the north edge and gives on the south edge
        "foundry" ->
            listOf(
                FluidPort(3, -1, PortFlow.INPUT),
                FluidPort(1, -1, PortFlow.INPUT),
                FluidPort(1, 5, PortFlow.OUTPUT),
                FluidPort(3, 5, PortFlow.OUTPUT),
            )
        // the cryogenic plant's prototype takes on the bottom edge and gives on the top edge;
        // after the 180° turn it takes on the north edge and gives on the south edge
        "cryogenic-plant" ->
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
        "electromagnetic-plant" ->
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
fun blueprintMachineEdge(identifier: String, port: FluidPort): Direction {
    val (width, height) = blueprintMachineFootprint(identifier)
    val (x, y) = port.tile
    return when (x) {
        -1 -> Direction.WEST
        width -> Direction.EAST
        else ->
            when (y) {
                -1 -> Direction.NORTH
                height -> Direction.SOUTH
                else -> error("$identifier has no port at ${port.tile}")
            }
    }
}

/**
 * The flow [port] carries on [edge], read from the prototype table. [edge] must be the side the
 * port's tile sits on; an edge the port does not touch keeps [PortFlow.BOTH], which is only the
 * total fallback for a caller that passes a wrong side.
 */
fun blueprintMachineEdgeFlow(identifier: String, port: FluidPort, edge: Direction): PortFlow {
    val (x, y) = port.tile
    val (width, height) = blueprintMachineFootprint(identifier)
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
 * The port a fluid enters [identifier] through, if any: the first input port in
 * [prototypeFluidPorts]. A machine may list more than one such pipe (the chemical plant's two top
 * corners, the foundry's two top pipes), but a row feeds it through one — the table order is the
 * recipe's fluid order. A further fluid moves on to the next port.
 */
fun blueprintMachineInputPort(identifier: String): FluidPort? =
    prototypeFluidPorts(identifier).firstOrNull { isInputPort(identifier, it) }

/**
 * The port a fluid leaves [identifier] through, if any: the first output port in
 * [prototypeFluidPorts], which is the recipe's first fluid result.
 */
fun blueprintMachineOutputPort(identifier: String): FluidPort? =
    prototypeFluidPorts(identifier).firstOrNull { isOutputPort(identifier, it) }

/** Whether [port] is an input in [prototypeFluidPorts], by [blueprintMachineEdgeFlow]. */
internal fun isInputPort(identifier: String, port: FluidPort) =
    blueprintMachineEdgeFlow(identifier, port, blueprintMachineEdge(identifier, port)) !=
        PortFlow.OUTPUT

/** Whether [port] is an output in [prototypeFluidPorts], by [blueprintMachineEdgeFlow]. */
internal fun isOutputPort(identifier: String, port: FluidPort) =
    blueprintMachineEdgeFlow(identifier, port, blueprintMachineEdge(identifier, port)) !=
        PortFlow.INPUT
