package de.bjrke.factorio.optimizer.common.util

import de.bjrke.test.assertTableOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Deliberately not [Comparable], so only an injected comparator can order it. */
private data class Weighted(val weight: Int, val name: String)

class MinHeapTest {

    @Test
    fun comparatorOrdersElementsWithoutNaturalOrder() {
        val heap = MinHeap(compareBy<Weighted> { it.weight })

        heap.add(Weighted(5, "five"))
        heap.add(Weighted(1, "one"))
        heap.add(Weighted(8, "eight"))
        heap.add(Weighted(3, "three"))

        assertEquals(
            listOf("one", "three", "five", "eight"),
            heap.drainAll().asSequence().map { it.name }.toList(),
        )
    }

    @Test
    fun descendingComparatorPollsLargestFirst() {
        val heap = MinHeap(compareByDescending<Int> { it })
        heap.addAll(listOf(5, 1, 8, 3))

        assertEquals(listOf(8, 5, 3, 1), heap.drainAll().asSequence().toList())
    }

    @Test
    fun companionKeepsNaturalOrder() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(5, 1, 8, 3, 2))

        assertEquals(listOf(1, 2, 3, 5, 8), heap.drainAll().asSequence().toList())
    }

    @Test
    fun pollingBetweenAddsStillReturnsTheSmallest() {
        val heap = MinHeap<Int>()
        heap.add(4)
        heap.add(1)
        assertEquals(1, heap.removeFirst())
        heap.add(0)
        heap.add(3)

        assertEquals(listOf(0, 3, 4), heap.drainAll().asSequence().toList())
    }

    @Test
    fun duplicatesAreAllPolledInOrder() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(3, 1, 3, 2, 1, 3))

        assertEquals(listOf(1, 1, 2, 3, 3, 3), heap.drainAll().asSequence().toList())
    }

    @Test
    fun comparatorTieBreakerDecidesEqualWeights() {
        val heap = MinHeap(compareBy<Weighted> { it.weight }.thenBy { it.name })

        heap.addAll(
            listOf(
                Weighted(1, "beta"),
                Weighted(2, "single"),
                Weighted(1, "alpha"),
            ),
        )

        assertEquals(
            listOf("alpha", "beta", "single"),
            heap.drainAll().asSequence().toList().map { it.name },
        )
    }

    @Test
    fun shuffledInputPollsInSortedOrder() {
        val random = Random(20261103)
        assertTableOf(0, 1, 2, 3, 4, 7, 8, 15, 16, 100, 1_000) { size ->
            val values = List(size) { random.nextInt() }
            val heap = MinHeap<Int>()
            heap.addAll(values.shuffled(random))

            assertEquals(values.sorted(), heap.drainAll().asSequence().toList())
        }
    }

    @Test
    fun interleavedAddsAndRemovalsMatchAMultisetModel() {
        val random = Random(4711)
        val heap = MinHeap<Int>()
        val model = mutableListOf<Int>()

        repeat(1_000) {
            val removing = model.isNotEmpty() && random.nextInt(10) >= 7
            if (removing) {
                val smallest = model.min()
                assertEquals(smallest, heap.removeFirst())
                model.remove(smallest)
            } else {
                val value = random.nextInt(-50, 51)
                heap.add(value)
                model.add(value)
            }
        }

        assertEquals(model.sorted(), heap.drainAll().asSequence().toList())
    }
}
