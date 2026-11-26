package de.bjrke.factorio.optimizer.common.util

import de.bjrke.test.assertTableOf
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MathUtilsTest {

    @Test
    fun testCountVectors() {
        assertTableOf(
            listOf("A", "B", "C", "D") to listOf(mapOf("A" to 1, "B" to 1, "C" to 1, "D" to 1)),
            listOf("A") to listOf(mapOf("A" to 3)),
            listOf("A", "B") to listOf(mapOf("A" to 1, "B" to 2), mapOf("A" to 2, "B" to 1)),
            listOf("A", "B") to
                listOf(
                    mapOf("A" to 1, "B" to 3),
                    mapOf("A" to 2, "B" to 2),
                    mapOf("A" to 3, "B" to 1),
                ),
            listOf("A", "B", "C") to
                listOf(
                    mapOf("A" to 1, "B" to 1, "C" to 3),
                    mapOf("A" to 1, "B" to 2, "C" to 2),
                    mapOf("A" to 1, "B" to 3, "C" to 1),
                    mapOf("A" to 2, "B" to 1, "C" to 2),
                    mapOf("A" to 2, "B" to 2, "C" to 1),
                    mapOf("A" to 3, "B" to 1, "C" to 1),
                ),
        ) { (input, expected) ->
            val length = expected.maxOf { it.values.sum() }
            assertEquals(expected, countVectors(input.toTypedArray(), length))
        }
    }

    @Test
    fun testCountVectorsOfNoElements() {
        assertEquals(emptyList(), countVectors(emptyArray<String>(), 3))
    }

    @Test
    fun snapToWholeIgnoresOnlyRoundingNoise() =
        assertTableOf(
            // the reported splitter flow: 4.8 items/s over 2.4 items/s per inserter
            4.800000000000001 / 2.4 to 2.0,
            12.999999999999998 to 13.0,
            2.0001 to 2.0001,
            0.5 to 0.5,
            0.0004 to 0.0004,
            0.0 to 0.0,
        ) { (count, expected) ->
            assertEquals(expected, snapToWhole(count))
        }

    @Test
    fun epsilonCeilRoundsUpButNotOnRoundingNoise() =
        assertTableOf(
            // the reported splitter flow needs two inserters, not three
            4.800000000000001 / 2.4 to 2,
            4.81 / 2.4 to 3,
            0.0004 to 1,
            0.0 to 0,
        ) { (count, expected) ->
            assertEquals(expected, epsilonCeil(count))
        }

    @Test
    fun superRoundShouldRoundTheNumber() =
        assertTableOf(
            0.00001234567 to 0.0,
            0.1234567890 to round(0.1234567890 * 1000.0) / 1000.0,
            1.5 to 1.5,
            100.12345 to 100.1,
            9876.5432 to 9877.0,
            123456789.1234 to 123456789.0,
            -0.00001234567 to round(-0.00001234567 * 1000.0) / 1000.0,
            -0.1234567890 to round(-0.1234567890 * 1000.0) / 1000.0,
            -1.5 to -1.5,
            -100.12345 to -100.1,
            -9876.5432 to -9877.0,
            -123456789.1234 to -123456789.0,
        ) { (from, expected) ->
            assertEquals(expected, superRound(from))
        }

    @Test
    fun superRoundRejectsNonFiniteValues() =
        assertTableOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        ) { value ->
            assertFailsWith<IllegalArgumentException> { superRound(value) }
        }
}
