package de.bjrke.factorio.optimizer.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Deliberately not [Comparable], so only an injected comparator can order it. */
private data class Weighted(val weight: Int, val name: String)

private fun <T> MinHeap<T>.pollAll(): List<T> = generateSequence {
    if (isEmpty()) null else poll()
}
    .toList()

class MinHeapTest {

    @Test
    fun comparatorOrdersElementsWithoutNaturalOrder() {
        val heap = MinHeap(compareBy<Weighted> { it.weight })
        listOf(
                Weighted(5, "five"),
                Weighted(1, "one"),
                Weighted(8, "eight"),
                Weighted(3, "three"),
            )
            .forEach(heap::add)

        assertEquals(listOf("one", "three", "five", "eight"), heap.pollAll().map { it.name })
    }

    @Test
    fun descendingComparatorPollsLargestFirst() {
        val heap = MinHeap(compareByDescending<Int> { it })
        listOf(5, 1, 8, 3).forEach(heap::add)

        assertEquals(listOf(8, 5, 3, 1), heap.pollAll())
    }

    @Test
    fun companionKeepsNaturalOrder() {
        val heap = MinHeap<Int>()
        listOf(5, 1, 8, 3, 2).forEach(heap::add)

        assertEquals(listOf(1, 2, 3, 5, 8), heap.pollAll())
    }

    @Test
    fun pollingBetweenAddsStillReturnsTheSmallest() {
        val heap = MinHeap<Int>()
        heap.add(4)
        heap.add(1)
        assertEquals(1, heap.poll())
        heap.add(0)
        heap.add(3)

        assertEquals(listOf(0, 3, 4), heap.pollAll())
    }

    @Test
    fun pollOnEmptyHeapThrows() {
        assertFailsWith<NoSuchElementException> { MinHeap<Int>().poll() }
    }
}
