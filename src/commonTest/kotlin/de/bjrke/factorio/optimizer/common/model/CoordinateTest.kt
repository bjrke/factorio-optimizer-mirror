package de.bjrke.factorio.optimizer.common.model

import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateTest {

    @Test
    fun areaWithoutParamsHasSize1() {
        assertEquals(1, Coordinate(34, 21).area().size)
    }

    @Test
    fun areaGrownBy3HasSize49() {
        assertEquals(49, Coordinate(132, 14).area().grow(3).size)
    }

    @Test
    fun directionsMoveOneTileByDefault() =
        assertTableOf(
            Coordinate(5, 4) to Coordinate(5, 5).north(),
            Coordinate(5, 6) to Coordinate(5, 5).south(),
            Coordinate(6, 5) to Coordinate(5, 5).east(),
            Coordinate(4, 5) to Coordinate(5, 5).west(),
        ) { (expected, actual) ->
            assertEquals(expected, actual)
        }

    @Test
    fun directionsHonourTheAmount() =
        assertTableOf(
            Coordinate(5, 3) to Coordinate(5, 5).north(2),
            Coordinate(5, 7) to Coordinate(5, 5).south(2),
            Coordinate(7, 5) to Coordinate(5, 5).east(2),
            Coordinate(3, 5) to Coordinate(5, 5).west(2),
        ) { (expected, actual) ->
            assertEquals(expected, actual)
        }
}
