package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val WATER = Material("water")

class LayoutPlacementTest {

    private fun newBoard() =
        LayoutBoard(
            names =
                LayoutNames(
                    belt = "transport-belt",
                    inserter = "inserter",
                    longInserter = "long-handed-inserter",
                ),
            undergroundRange = 4,
            fluidPredicate = { true },
            temperatureOf = { 0 },
        )

    @Test
    fun aCheatFluidSourceIsAFilteredInfinityPipe() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.INFINITY_CHEST, Coordinate(4, 0), WATER)

        val pipe = board.entityAt(Coordinate(4, 0)) as InfinityPipeEntity
        assertEquals(InfinityPipeSettings(WATER, temperature = 0), pipe.settings)
    }

    @Test
    fun aCheatFluidSinkIsAnUnfilteredInfinityPipe() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.INFINITY_CHEST, Coordinate(4, 0), WATER, sink = true)

        val pipe = board.entityAt(Coordinate(4, 0)) as InfinityPipeEntity
        assertNull(pipe.settings)
    }

    @Test
    fun aRealFluidSourceBuildsWestFromItsEnd() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.CONSTANT_COMBINATOR, Coordinate(4, 0), WATER)

        val input = board.entityAt(Coordinate(2, 0)) as PipeToGroundEntity
        val marker = board.entityAt(Coordinate(3, 0)) as MarkerEntity
        val output = board.entityAt(Coordinate(4, 0)) as PipeToGroundEntity
        assertAll(
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Direction.EAST, input.direction) },
            { assertEquals(Signal(WATER, fluid = true), marker.signal) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(Direction.EAST, output.direction) },
            { assertEquals(output, board.undergroundOutputOf(input)) },
        )
    }

    @Test
    fun removingAFluidSourceRemovesItsWholeBlock() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.CONSTANT_COMBINATOR, Coordinate(4, 0), WATER)

        board.removeFluidSource(BeltEnd.CONSTANT_COMBINATOR, Coordinate(4, 0))

        assertAll(
            { assertNull(board.entityAt(Coordinate(2, 0))) },
            { assertNull(board.entityAt(Coordinate(3, 0))) },
            { assertNull(board.entityAt(Coordinate(4, 0))) },
        )
    }
}
