package de.bjrke.factorio.optimizer.common.util

import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

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
            assertEquals(expected, countVectors(input, length))
        }
    }

    @Test
    fun testCountVectorsOfNoElements() {
        assertEquals(emptyList(), countVectors(emptyList<String>(), 3))
    }
}
