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
 * Where a machine's pipes connect, relative to its top-left tile.
 *
 * The values follow the machine prototypes' `pipe_connections`: a connection's `position` (in tile
 * units around the machine's center) is the tile at the machine's edge, and its `direction` is the
 * side the pipe attaches on, so the pipe tile is the edge tile one step in that direction. E.g. the
 * electromagnetic plant's west connection sits at `{-1.5, 0.5}` facing west on a 4x4 machine,
 * giving tile `(-1, 2)` (third row). Machines without pipes return an empty list.
 *
 * Which pipes take and which give is not the prototype's: the prototype data does not say it, and
 * the `data.raw` dump in `resources/prototypes` drops `pipe_connections` anyway (see
 * `sanitizePrototypeDump`), so the dump cannot verify it. The flow follows the row instead: the
 * placer feeds every machine from the lanes above and drains it onto the bottom belt, so
 * [blueprintMachineEdgeFlow] reads a one-way port on the top edge as an input and one on the bottom
 * edge as an output. That is the single rule the inserter placer and the fluid feed read, instead
 * of each deriving `y == -1` / `y == height` for itself.
 */
fun blueprintMachineFluidPorts(identifier: String): List<FluidPort> =
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
        // the oil refinery has its pipes on the bottom and top edges, inputs bottom, outputs top
        "oil-refinery" ->
            listOf(
                FluidPort(1, 5, PortFlow.INPUT),
                FluidPort(3, 5, PortFlow.INPUT),
                FluidPort(0, -1, PortFlow.OUTPUT),
                FluidPort(2, -1, PortFlow.OUTPUT),
                FluidPort(4, -1, PortFlow.OUTPUT),
            )
        // the foundry has two pipes on the bottom edge and two on the top edge
        "foundry" ->
            listOf(
                FluidPort(1, 5, PortFlow.INPUT),
                FluidPort(3, 5, PortFlow.INPUT),
                FluidPort(1, -1, PortFlow.OUTPUT),
                FluidPort(3, -1, PortFlow.OUTPUT),
            )
        // the cryogenic plant has three pipes on the bottom edge and three on the top edge, so it
        // can take up to three fluids and give as many
        "cryogenic-plant" ->
            listOf(
                FluidPort(0, 5, PortFlow.INPUT),
                FluidPort(2, 5, PortFlow.INPUT),
                FluidPort(4, 5, PortFlow.INPUT),
                FluidPort(0, -1, PortFlow.OUTPUT),
                FluidPort(2, -1, PortFlow.OUTPUT),
                FluidPort(4, -1, PortFlow.OUTPUT),
            )
        // the electromagnetic plant can be fed and drained on every side
        "electromagnetic-plant" ->
            listOf(
                FluidPort(-1, 2, PortFlow.BOTH),
                FluidPort(4, 1, PortFlow.BOTH),
                FluidPort(2, 4, PortFlow.BOTH),
                FluidPort(1, -1, PortFlow.BOTH),
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
 * The flow [port] carries on [edge]. A port whose connection does not take and give at once runs
 * the way the row reads: a pipe on the top or west edge feeds the machine, the opposite side drains
 * it. That is the inserter placer's rule too, because its inputs come from the lanes above and its
 * product leaves below. A 1x1 machine would put the same port on every edge; no machine is that
 * small, so the check only keeps the function total.
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
    return when {
        !onEdge -> PortFlow.BOTH
        port.flow == PortFlow.BOTH -> PortFlow.BOTH
        edge == Direction.NORTH || edge == Direction.WEST -> PortFlow.INPUT
        else -> PortFlow.OUTPUT
    }
}

/**
 * The port a fluid enters [identifier] through, if any: the first port the edge flow reads as an
 * input, in table order. A machine may list more than one such pipe (the chemical plant's two top
 * corners, the foundry's two top pipes), but a row feeds it through one — the leftmost, which is
 * the order the table lists and the row reads. A further fluid moves on to the next port.
 */
fun blueprintMachineInputPort(identifier: String): FluidPort? =
    blueprintMachineFluidPorts(identifier).firstOrNull { isInputPort(identifier, it) }

/**
 * The port a fluid leaves [identifier] through, if any: the first port the machine gives fluid on.
 */
fun blueprintMachineOutputPort(identifier: String): FluidPort? =
    blueprintMachineFluidPorts(identifier).firstOrNull { isOutputPort(identifier, it) }

/** Whether [port] sits on an edge the row reads as input, by [blueprintMachineEdgeFlow]. */
internal fun isInputPort(identifier: String, port: FluidPort) =
    blueprintMachineEdgeFlow(identifier, port, blueprintMachineEdge(identifier, port)) !=
        PortFlow.OUTPUT

/** Whether [port] sits on an edge the row reads as output, by [blueprintMachineEdgeFlow]. */
internal fun isOutputPort(identifier: String, port: FluidPort) =
    blueprintMachineEdgeFlow(identifier, port, blueprintMachineEdge(identifier, port)) !=
        PortFlow.INPUT
