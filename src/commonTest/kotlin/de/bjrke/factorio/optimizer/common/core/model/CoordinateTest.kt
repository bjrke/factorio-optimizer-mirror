package de.bjrke.factorio.optimizer.common.core.model

import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CoordinateTest {

    @Test
    fun equalCoordinatesShareTheirHash() {
        val first = Coordinate(7, -3)
        val second = Coordinate(7, -3)
        assertAll(
            { assertEquals(first, second) },
            { assertEquals(first.hashCode(), second.hashCode()) },
        )
    }

    @Test
    fun theGeneratedDiagonalCollisionIsGone() {
        // the default data-class hash 31 * x + y put these two tiles in one bucket
        assertNotEquals(Coordinate(2, 5).hashCode(), Coordinate(3, 5 - 31).hashCode())
    }

    @Test
    fun distinctCoordinatesHashDistinctly() {
        val hashes = (-8..8).flatMap { x -> (-8..8).map { y -> Coordinate(x, y).hashCode() } }
        assertEquals(hashes.size, hashes.toSet().size)
    }

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
    fun neighborsAreTheFourSidesNorthFirstClockwise() =
        assertEquals(
            listOf(Coordinate(5, 4), Coordinate(6, 5), Coordinate(5, 6), Coordinate(4, 5)),
            Coordinate(5, 5).neighbors,
        )

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
