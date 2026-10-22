package de.bjrke.factorio.optimizer.common.compaction

import de.bjrke.factorio.optimizer.common.layout.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.Plumbing
import de.bjrke.factorio.optimizer.common.layout.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.layout.UndergroundEntity
import de.bjrke.factorio.optimizer.common.layout.endName
import de.bjrke.factorio.optimizer.common.layout.hasTunnel
import de.bjrke.factorio.optimizer.common.layout.markTunnel
import de.bjrke.factorio.optimizer.common.layout.placeEnd
import de.bjrke.factorio.optimizer.common.layout.range
import de.bjrke.factorio.optimizer.common.layout.undergroundOutputOf
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.direction
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.manhattanDist

/**
 * Grows underground runs along their own straight surface run, up to the tier's reach. It runs
 * after all feeding and before electricity, so it may rewrite belts and pipes; a run only grows by
 * swallowing the plain belts/pipes of its own material that already continue it.
 */
internal class UndergroundStretcher(
    private val board: LayoutBoard,
    /** The machine-port tiles a fluid must reach; a run never swallows one. */
    private val fluidPorts: Set<Coordinate>,
) {

    fun stretchAll() {
        for (input in
            board.placed.filterIsInstance<UndergroundEntity>().filter { it.io == INPUT }) {
            // the import connector is a fixed marker or chest, not a route to stretch
            if (isConnectorTunnel(input)) continue
            stretchRun(input)
        }
    }

    /**
     * Whether [input] is the buried run of a fixed belt connector: its first tile after the input
     * is the connector's label or inserter, not a plain belt the run could swallow.
     */
    private fun isConnectorTunnel(input: UndergroundEntity): Boolean {
        val after = board.entityAt(input.area.topLeft.east())
        return after is MarkerEntity || after is InserterEntity
    }

    private fun stretchRun(input: UndergroundEntity) {
        if (input !in board.placed) return
        val output = board.undergroundOutputOf(input) ?: return
        RunGrowth(
                board,
                input.area.topLeft,
                output.area.topLeft,
                input.direction,
                input.material,
                fluidPorts,
            )
            .stretch()
    }
}

/**
 * One underground run to grow. Grows both ends at most its reach, spreading the extra tiles evenly;
 * when one end runs out the other takes the remainder, so the run grows toward the end that still
 * has plain material to swallow (fewer free tiles). The end entities are re-placed and the
 * swallowed tiles become part of the run.
 */
private class RunGrowth(
    private val board: LayoutBoard,
    private val inputTile: Coordinate,
    private val outputTile: Coordinate,
    private val direction: Direction,
    private val material: Material,
    private val fluidPorts: Set<Coordinate>,
) {
    private val step = direction.step
    private val backward = step * -1
    private val budget = board.range(material) - inputTile.manhattanDist(outputTile)
    private val fluid = board.isFluid(material)
    private val type = board.endName(material)

    fun stretch() {
        if (budget <= 0) return
        val (back, forward) = share(budget, length(inputTile, backward), length(outputTile, step))
        if (back == 0 && forward == 0) return

        for (i in 1..back) board.removeAt(inputTile + backward * i)
        for (i in 1..forward) board.removeAt(outputTile + step * i)
        board.removeAt(inputTile)
        board.removeAt(outputTile)
        val newInput = inputTile + backward * back
        val newOutput = outputTile + step * forward

        board.placeEnd(material, newInput, direction, INPUT)
        board.placeEnd(material, newOutput, direction, OUTPUT)

        board.markTunnel(newInput, newOutput, type)
    }

    /**
     * How far the end at [start] can grow in [step], at most [budget] tiles: every swallowed tile
     * must be [swallowable], it must leave something for the new end to join beyond it (never
     * another underground end of a belt), and the tile that becomes part of the run must not
     * already carry a tunnel of the same [type] on the same axis.
     */
    private fun length(start: Coordinate, step: Coordinate): Int {
        var count = 0
        while (count < budget) {
            val next = start + step * (count + 1)
            val beyond = board.entityAt(next + step)
            if (!swallowable(next) || beyond == null) break
            // a belt exit/entrance joins a surface belt, not the next run's underground end
            if (!fluid && beyond is UndergroundBeltEntity) break
            if (board.hasTunnel(start + step * count, step, type)) break
            count++
        }
        return count
    }

    private fun swallowable(tile: Coordinate) =
        when (val entity = board.entityAt(tile)) {
            is BeltEntity -> !fluid && entity.material == material && entity.direction == direction
            // a pipe is only swallowable when it is a straight pass-through of the same fluid;
            // a machine port or a corner must stay a surface tile, or the run would end on it
            is PipeEntity ->
                fluid && entity.material == material && tile !in fluidPorts && isStraight(tile)
            else -> false
        }

    /** A straight pipe has no plumbing neighbor across the run's axis (a turn has one). */
    private fun isStraight(tile: Coordinate): Boolean {
        val across =
            if (step.x != 0) listOf(Coordinate.north, Coordinate.south)
            else listOf(Coordinate.east, Coordinate.west)
        return across.none { board.entityAt(tile + it) is Plumbing }
    }

    /**
     * Splits [budget] extra tiles as evenly as possible, the remainder to the end with more room.
     */
    private fun share(budget: Int, maxBack: Int, maxForward: Int): Pair<Int, Int> {
        var back = minOf(maxBack, budget / 2)
        val forward = minOf(maxForward, budget - back)
        val left = budget - back - forward
        if (left > 0) back = minOf(maxBack, back + left)
        return back to forward
    }
}
