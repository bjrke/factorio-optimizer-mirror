package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.neighbors
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val WATER = Material("water")
private val OIL = Material("heavy-oil")

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
            board.placePipe(neighbor, OIL)
            assertFalse(board.canPlacePipes(Coordinate(5, 5), WATER))
        }

    @Test
    fun aPipeOfTheSameFluidBesideTheTileStillFits() =
        assertTable(Coordinate(5, 5).neighbors) { neighbor ->
            val board = newBoard()
            board.placePipe(neighbor, WATER)
            assertTrue(board.canPlacePipes(Coordinate(5, 5), WATER))
        }

    @Test
    fun aForeignFluidOnlyDiagonallyDoesNotTouch() {
        val board = newBoard()
        board.placePipe(Coordinate(6, 6), OIL)
        assertFalse(board.touchesForeign(Coordinate(5, 5), WATER))
        assertTrue(board.canPlacePipes(Coordinate(5, 5), WATER))
    }

    @Test
    fun aTakenTileNeverTakesAPipe() {
        val board = newBoard()
        board.placePipe(Coordinate(5, 5), WATER)
        assertFalse(board.canPlacePipes(Coordinate(5, 5), WATER))
    }

    @Test
    fun aReservationForAnotherFluidIsNotATouchingPipe() {
        val board = newBoard()
        board.reserve(Coordinate(4, 5), OIL)
        assertFalse(board.touchesForeign(Coordinate(5, 5), WATER))
        assertTrue(board.canPlacePipes(Coordinate(5, 5), WATER))
    }
}
