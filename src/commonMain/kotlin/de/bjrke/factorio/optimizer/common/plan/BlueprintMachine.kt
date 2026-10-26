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
 * Which pipe takes and which gives comes from the prototype's `fluid_boxes` `production_type`. The
 * `data.raw` dump in `resources/prototypes` drops `pipe_connections` and `fluid_boxes` (see
 * `sanitizePrototypeDump`), so the curated table is the source of truth; it is verified against the
 * Wube prototype definitions, not the dump.
 *
 * The result is the table **after** the blueprint has turned the machine:
 * [blueprintMachineDirection] picks the rotation that puts its inputs on a horizontal lane side,
 * then the tiles are rotated with it. Ports come back left to right (x, then y), which is the order
 * the recipe's fluids take them in.
 */
fun blueprintMachineFluidPorts(identifier: String): List<FluidPort> {
    val direction = blueprintMachineDirection(identifier)
    val size = blueprintMachineFootprint(identifier)
    return prototypeFluidPorts(identifier)
        .map { port ->
            FluidPort(
                rotate(port.tile, size, direction),
                port.flow,
            )
        }
        .sortedWith(compareBy({ it.tile.x }, { it.tile.y }))
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

/** The prototype's pipe connections before the blueprint turns the machine. */
private fun prototypeFluidPorts(identifier: String): List<FluidPort> =
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
        // the oil refinery takes on the bottom edge and gives on the top edge
        "oil-refinery" ->
            listOf(
                FluidPort(1, 5, PortFlow.INPUT),
                FluidPort(3, 5, PortFlow.INPUT),
                FluidPort(0, -1, PortFlow.OUTPUT),
                FluidPort(2, -1, PortFlow.OUTPUT),
                FluidPort(4, -1, PortFlow.OUTPUT),
            )
        // the foundry takes on the bottom edge and gives on the top edge
        "foundry" ->
            listOf(
                FluidPort(1, 5, PortFlow.INPUT),
                FluidPort(3, 5, PortFlow.INPUT),
                FluidPort(1, -1, PortFlow.OUTPUT),
                FluidPort(3, -1, PortFlow.OUTPUT),
            )
        // the cryogenic plant takes on the bottom edge and gives on the top edge
        "cryogenic-plant" ->
            listOf(
                FluidPort(0, 5, PortFlow.INPUT),
                FluidPort(2, 5, PortFlow.INPUT),
                FluidPort(4, 5, PortFlow.INPUT),
                FluidPort(0, -1, PortFlow.OUTPUT),
                FluidPort(2, -1, PortFlow.OUTPUT),
                FluidPort(4, -1, PortFlow.OUTPUT),
            )
        // the electromagnetic plant's input boxes sit west/east and its output boxes north/south
        "electromagnetic-plant" ->
            listOf(
                FluidPort(-1, 2, PortFlow.INPUT),
                FluidPort(4, 1, PortFlow.INPUT),
                FluidPort(2, 4, PortFlow.OUTPUT),
                FluidPort(1, -1, PortFlow.OUTPUT),
            )

        else -> emptyList()
    }

/** Rotates [tile] around the center of a [size] footprint by [direction]. */
private fun rotate(tile: Coordinate, size: Dimension, direction: Direction): Coordinate {
    var (x, y) = tile
    repeat(direction.ordinal) {
        // the tile centers sit half a tile inside their index, so rotate centers, then floor again
        val dx = x + 0.5 - size.width / 2.0
        val dy = y + 0.5 - size.height / 2.0
        val turnedX = size.width / 2.0 - dy
        val turnedY = size.height / 2.0 + dx
        x = kotlin.math.floor(turnedX - 0.5).toInt()
        y = kotlin.math.floor(turnedY - 0.5).toInt()
    }
    return Coordinate(x, y)
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
 * The port a fluid enters [identifier] through, if any: the leftmost input port in the turned
 * table. A machine may list more than one such pipe (the chemical plant's two top corners, the
 * foundry's two top pipes), but a row feeds it through one — the first in recipe order. A further
 * fluid moves on to the next port.
 */
fun blueprintMachineInputPort(identifier: String): FluidPort? =
    blueprintMachineFluidPorts(identifier).firstOrNull { isInputPort(identifier, it) }

/**
 * The port a fluid leaves [identifier] through, if any: the leftmost output port in the turned
 * table.
 */
fun blueprintMachineOutputPort(identifier: String): FluidPort? =
    blueprintMachineFluidPorts(identifier).firstOrNull { isOutputPort(identifier, it) }

/** Whether [port] is an input in the turned table, by [blueprintMachineEdgeFlow]. */
internal fun isInputPort(identifier: String, port: FluidPort) =
    blueprintMachineEdgeFlow(identifier, port, blueprintMachineEdge(identifier, port)) !=
        PortFlow.OUTPUT

/** Whether [port] is an output in the turned table, by [blueprintMachineEdgeFlow]. */
internal fun isOutputPort(identifier: String, port: FluidPort) =
    blueprintMachineEdgeFlow(identifier, port, blueprintMachineEdge(identifier, port)) !=
        PortFlow.INPUT
