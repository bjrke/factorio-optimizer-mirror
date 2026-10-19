package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT

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

    /**
     * Grows one underground run, at most its range, by spreading the extra tiles evenly between its
     * two ends; when one end runs out the other takes the remainder, so the run grows toward the
     * end that still has plain material to swallow (fewer free tiles). The end entities are
     * re-placed and the swallowed tiles become part of the run.
     */
    private fun stretchRun(input: UndergroundEntity) {
        if (input !in board.placed) return
        val output = board.undergroundOutputOf(input) ?: return

        val inputTile = input.area.topLeft
        val outputTile = output.area.topLeft

        val direction = input.direction
        val material = input.material
        val range = board.range(material)
        val type = board.endName(material)

        val step = direction.step
        val backward = step * -1
        val budget = range - inputTile.manhattanDist(outputTile)
        if (budget <= 0) return
        val fluid = board.isFluid(material)

        // a straight pipe has no plumbing neighbor across the run's axis (a turn has one)
        fun isStraight(tile: Coordinate): Boolean {
            val across =
                if (step.x != 0) listOf(Coordinate.north, Coordinate.south)
                else listOf(Coordinate.east, Coordinate.west)
            return across.none { board.entityAt(tile + it) is Plumbing }
        }

        fun swallowable(tile: Coordinate) =
            when (val entity = board.entityAt(tile)) {
                is BeltEntity ->
                    !fluid && entity.material == material && entity.direction == direction
                // a pipe is only swallowable when it is a straight pass-through of the same fluid;
                // a machine port or a corner must stay a surface tile, or the run would end on it
                is PipeEntity ->
                    fluid && entity.material == material && tile !in fluidPorts && isStraight(tile)
                else -> false
            }

        val maxBack = extension(inputTile, backward, budget, type, fluid, ::swallowable)
        val maxForward = extension(outputTile, step, budget, type, fluid, ::swallowable)

        val (back, forward) = share(budget, maxBack, maxForward)
        if (back == 0 && forward == 0) return

        for (i in 1..back) board.removeAt(inputTile + backward * i)
        for (i in 1..forward) board.removeAt(outputTile + step * i)
        board.removeAt(inputTile)
        board.removeAt(outputTile)
        val newInput = inputTile + backward * back
        val newOutput = outputTile + step * forward

        board.placeEnd(material, newInput, input.direction, INPUT)
        board.placeEnd(material, newOutput, input.direction, OUTPUT)

        board.markTunnel(newInput, newOutput, type)
    }

    /**
     * How far the end at [start] can grow in [step], at most [budget] tiles: every swallowed tile
     * must be [swallowable], it must leave something for the new end to join beyond it (never
     * another underground end of a belt), and the tile that becomes part of the run must not
     * already carry a tunnel of the same [type] on the same axis.
     */
    private fun extension(
        start: Coordinate,
        step: Coordinate,
        budget: Int,
        type: String,
        fluid: Boolean,
        swallowable: (Coordinate) -> Boolean,
    ): Int {
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
