package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension

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
data class FluidPort(val tile: Coordinate, val flow: PortFlow)

/**
 * Where a machine's pipes connect, relative to its top-left tile.
 *
 * The values come from the `pipe_connections` of the machine prototypes: a connection's `position`
 * (in tile units around the machine's center) is the tile at the machine's edge, and its
 * `direction` is the side the pipe attaches on, so the pipe tile is the edge tile one step in that
 * direction. E.g. the electromagnetic plant's west connection sits at `{-1.5, 0.5}` facing west on
 * a 4x4 machine, giving tile `(-1, 2)` (third row). Machines without pipes return an empty list.
 */
fun blueprintMachineFluidPorts(identifier: String): List<FluidPort> =
    when (identifier) {
        "assembling-machine-2",
        "assembling-machine-3" ->
            listOf(
                FluidPort(Coordinate(1, -1), PortFlow.INPUT),
                FluidPort(Coordinate(1, 3), PortFlow.OUTPUT),
            )
        // the chemical plant and the biochamber have both fluids on the top corners and both
        // outputs on the bottom corners
        "chemical-plant",
        "biochamber" ->
            listOf(
                FluidPort(Coordinate(0, -1), PortFlow.INPUT),
                FluidPort(Coordinate(2, -1), PortFlow.INPUT),
                FluidPort(Coordinate(0, 3), PortFlow.OUTPUT),
                FluidPort(Coordinate(2, 3), PortFlow.OUTPUT),
            )
        // the oil refinery takes its fluids on the bottom and gives them on the top
        "oil-refinery" ->
            listOf(
                FluidPort(Coordinate(1, 5), PortFlow.INPUT),
                FluidPort(Coordinate(3, 5), PortFlow.INPUT),
                FluidPort(Coordinate(0, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(2, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(4, -1), PortFlow.OUTPUT),
            )
        "foundry" ->
            listOf(
                FluidPort(Coordinate(1, 5), PortFlow.INPUT),
                FluidPort(Coordinate(3, 5), PortFlow.INPUT),
                FluidPort(Coordinate(1, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(3, -1), PortFlow.OUTPUT),
            )
        // the cryogenic plant takes up to three fluids on the bottom and gives them on the top
        "cryogenic-plant" ->
            listOf(
                FluidPort(Coordinate(0, 5), PortFlow.INPUT),
                FluidPort(Coordinate(2, 5), PortFlow.INPUT),
                FluidPort(Coordinate(4, 5), PortFlow.INPUT),
                FluidPort(Coordinate(0, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(2, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(4, -1), PortFlow.OUTPUT),
            )
        // the electromagnetic plant can be fed and drained on every side
        "electromagnetic-plant" ->
            listOf(
                FluidPort(Coordinate(-1, 2), PortFlow.BOTH),
                FluidPort(Coordinate(4, 1), PortFlow.BOTH),
                FluidPort(Coordinate(2, 4), PortFlow.BOTH),
                FluidPort(Coordinate(1, -1), PortFlow.BOTH),
            )
        else -> emptyList()
    }
