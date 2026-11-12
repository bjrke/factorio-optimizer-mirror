package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.fixtures.crudeOil
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutBoardTest {

    private fun newBoard() =
        LayoutBoard(
            names =
                LayoutNames(
                    belt = "transport-belt",
                    inserter = "inserter",
                    longInserter = "long-handed-inserter",
                ),
            undergroundRange = 4,
            fluidPredicate = { false },
            temperatureOf = { 0 },
        )

    @Test
    fun aRowReservationStartsAtItsWestBound() {
        val board = newBoard()
        board.reserveRow(y = 5, fromX = 10, material = crudeOil)

        assertAll(
            { assertTrue(board.isReserved(Coordinate(10, 5))) },
            { assertTrue(board.isReserved(Coordinate(20, 5))) },
            { assertFalse(board.isReserved(Coordinate(9, 5))) },
            { assertFalse(board.isReserved(Coordinate(10, 6))) },
            { assertEquals(crudeOil, board.reservedMaterialAt(Coordinate(10, 5))) },
        )
    }

    @Test
    fun aTunnelIsRecordedUnderItsSpanAndNotOnItsEnds() {
        val board = newBoard()
        board.markTunnel(Coordinate(5, 3), Coordinate(5, 6), board.names.undergroundBelt)

        assertAll(
            { assertTrue(board.hasTunnelUnder(Coordinate(5, 4))) },
            { assertTrue(board.hasTunnelUnder(Coordinate(5, 5))) },
            { assertFalse(board.hasTunnelUnder(Coordinate(5, 3))) },
            { assertFalse(board.hasTunnelUnder(Coordinate(5, 6))) },
        )
    }
}
