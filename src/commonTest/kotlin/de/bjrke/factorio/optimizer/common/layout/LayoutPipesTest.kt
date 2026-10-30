package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.fixtures.crudeOil
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.neighbors
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutPipesTest {

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
    fun aPipeOfAnotherFluidOnAnySideBlocksTheTile() =
        assertTable(Coordinate(5, 5).neighbors) { neighbor ->
            val board = newBoard()
            board.placePipe(neighbor, crudeOil)
            assertFalse(board.canPlacePipes(Coordinate(5, 5), water))
        }

    @Test
    fun aPipeOfTheSameFluidBesideTheTileStillFits() =
        assertTable(Coordinate(5, 5).neighbors) { neighbor ->
            val board = newBoard()
            board.placePipe(neighbor, water)
            assertTrue(board.canPlacePipes(Coordinate(5, 5), water))
        }

    @Test
    fun aForeignFluidOnlyDiagonallyDoesNotTouch() {
        val board = newBoard()
        board.placePipe(Coordinate(6, 6), crudeOil)
        assertFalse(board.touchesForeign(Coordinate(5, 5), water))
        assertTrue(board.canPlacePipes(Coordinate(5, 5), water))
    }

    @Test
    fun aTakenTileNeverTakesAPipe() {
        val board = newBoard()
        board.placePipe(Coordinate(5, 5), water)
        assertFalse(board.canPlacePipes(Coordinate(5, 5), water))
    }

    @Test
    fun aReservationForAnotherFluidIsNotATouchingPipe() {
        val board = newBoard()
        board.reserve(Coordinate(4, 5), crudeOil)
        assertFalse(board.touchesForeign(Coordinate(5, 5), water))
        assertTrue(board.canPlacePipes(Coordinate(5, 5), water))
    }
}
