package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.prototypeFluidPorts
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * One fluid lane a row needs on one edge: the machine ports of that flow, left to right. [io]
 * describes if the fluid is an input or an output. A fluid that is both an input and an output of
 * the same machine yields both a feed lane and a drain lane; the two lanes are joined after
 * placement.
 */
internal class RowFluid(
    val material: Material,
    val ports: List<Coordinate>,
    val io: Io,
)

/**
 * The fluid lane of one row: the shape that feeds (or drains) every machine of the row through a
 * port on one of the row's horizontal edges. The line runs on a row **beyond** the belts, so no
 * belt moves and no inserter is touched: the row between the belts and the machines is left alone,
 * and each port is reached by a **vertical** shaft that dives past the belts and the inserter row
 * and lands on the port tile just outside the machine.
 *
 * A port is assigned to the recipe's fluids in recipe order: the first fluid input takes the first
 * input port, the second the next one, and the same for the outputs. The side a lane runs on is
 * read from that assignment, not from a fixed "north feeds, south drains", so a machine whose
 * prototype feeds from another edge is still served. A row gets no lane when a machine has no port
 * for that material on the side. A machine that takes several fluids on the same edge gets one lane
 * per fluid, stacked outward so every lane can dive to its own port.
 */
internal class RowFluidLane(private val logistics: LogisticsCalculator) {

    /**
     * The fluid lanes [row] needs on [side], the leftmost port first. One lane per fluid and flow:
     * a fluid that is both an input and an output yields a feed lane and a drain lane.
     */
    fun fluids(row: Row, side: LineSide) =
        row.machines
            .asSequence()
            .flatMap { machine ->
                flowsOf(machine).mapNotNull { (material, io) ->
                    portFor(row, machine, material, side, io)?.let { port ->
                        RowFluid(material, listOf(port), io)
                    }
                }
            }
            .groupBy { it.material to it.io }
            .map { (key, lanes) ->
                RowFluid(key.first, lanes.flatMap { it.ports }.sortedBy { it.x }, key.second)
            }
            // a both-way port is one pipe: keep the feed lane instead of stacking a drain on it
            .distinctBy { it.material to it.ports }
            .sortedBy { it.ports.first().x }

    /**
     * The port tile of [machine] the lane of [side] carries [material] to (or from), in recipe
     * order, one per fluid. Null when the machine has no port of that flow on [side].
     */
    fun portFor(
        row: Row,
        machine: PlacedMachine,
        material: Material,
        side: LineSide,
        io: Io,
    ): Coordinate? {
        val port = assignedPort(row, machine, material, io)
        return port?.takeIf { sideOf(row, machine, it) == side }
    }

    /**
     * The row the lane runs on: two rows beyond the outermost belt the row already uses, so the row
     * between holds the shafts' lane-side ends. It is never between a belt and the machines. A
     * [depth] of n puts the lane 2n rows further out than the first lane of that side.
     *
     * A mixed input belt's merge and turn tiles stand one and two rows outside its lane, at the
     * row's edge. They are part of the row's outline too: a lane row chosen from the belts alone
     * would run through them, and the marker block west of the head would collide with the merge.
     */
    fun laneRow(row: Row, side: LineSide, depth: Int = 0): Int {
        val depthDist = 2 + 2 * depth
        val belts = sequence {
            for (belt in row.belts) {
                yield(belt.y)
                val merge = belt.merge ?: continue
                yield(merge.mergeRow(belt.y))
                yield(merge.turnRow(belt.y))
            }
            yield(row.factoryTop - 2)
            yield(row.factoryBottom + 2)
        }

        return when (side) {
            LineSide.NORTH -> belts.min() - depthDist
            LineSide.SOUTH -> belts.max() + depthDist
        }
    }

    /** The port [machine] takes (or gives) [material] through, whichever row edge it is on. */
    internal fun assignedPort(
        row: Row,
        machine: PlacedMachine,
        material: Material,
        io: Io,
    ): Coordinate? {
        val index =
            if (io == Io.OUTPUT) outputMaterials(machine).indexOf(material)
            else inputMaterials(machine).indexOf(material)
        return if (index < 0) null else portAt(row, machine, index, io)
    }

    private fun portAt(
        row: Row,
        machine: PlacedMachine,
        index: Int,
        io: Io,
    ): Coordinate? =
        prototypeFluidPorts(machine.production.factory)
            .filter { io in it.flow.io }
            .getOrNull(index)
            ?.let { Coordinate(machine.left + it.tile.x, row.factoryTop + it.tile.y) }

    /** The fluid flows of [machine], as (material, sink) pairs in recipe order. */
    private fun flowsOf(machine: PlacedMachine): List<Pair<Material, Io>> =
        inputMaterials(machine).map { it to Io.INPUT } +
            outputMaterials(machine).map { it to Io.OUTPUT }

    /** The fluid inputs of [machine]'s recipe, in recipe order. */
    private fun inputMaterials(machine: PlacedMachine): List<Material> =
        machine.production.recipe.ingredients.keys.filter { logistics.isFluid(it) }

    /** The fluid outputs of [machine]'s recipe, in recipe order. */
    private fun outputMaterials(machine: PlacedMachine): List<Material> =
        machine.production.recipe.result.keys.filter { logistics.isFluid(it) }

    /** The horizontal edge [tile] sits on for [machine], or null when it is a side edge. */
    private fun sideOf(row: Row, machine: PlacedMachine, tile: Coordinate): LineSide? {
        val factory = machine.production.factory
        val size = prototypeFluidPorts(factory)
        val relative = Coordinate(tile.x - machine.left, tile.y - row.factoryTop)
        val port = size.firstOrNull { it.tile == relative } ?: return null
        return when (blueprintMachineEdge(factory, port)) {
            Direction.NORTH -> LineSide.NORTH
            Direction.SOUTH -> LineSide.SOUTH
            else -> null
        }
    }
}
