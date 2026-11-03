package de.bjrke.factorio.optimizer.common.util

import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Counts the comparisons a build makes, so the linear heapify is measured instead of assumed. */
private class CountingComparator<T>(private val delegate: Comparator<T>) : Comparator<T> {
    var comparisons = 0
        private set

    override fun compare(a: T, b: T): Int {
        comparisons++
        return delegate.compare(a, b)
    }
}

/** Construction: the iterable factories, bulk [MinHeap.addAll] and its single heapify pass. */
class MinHeapBuildTest {

    @Test
    fun naturalOrderFactoryBuildsFromAnIterable() {
        val heap = MinHeap(listOf(5, 1, 8, 3))

        assertAll(
            { assertEquals(4, heap.size) },
            { assertEquals(listOf(1, 3, 5, 8), heap.drainAll().asSequence().toList()) },
        )
    }

    @Test
    fun comparatorFactoryBuildsFromAnIterable() {
        val heap = MinHeap(compareByDescending<Int> { it }, listOf(1, 5, 3))

        assertEquals(listOf(5, 3, 1), heap.drainAll().asSequence().toList())
    }

    @Test
    fun factoriesAcceptEmptyAndSingleElementIterables() {
        assertTableOf(emptyList<Int>(), listOf(42)) { elements ->
            val heap = MinHeap(elements)

            assertAll(
                { assertEquals(elements.size, heap.size) },
                { assertEquals(elements.sorted(), heap.drainAll().asSequence().toList()) },
            )
        }
    }

    @Test
    fun emptyFactoryLeavesNoFirstElement() {
        val heap = MinHeap(emptyList<Int>())

        assertNull(heap.firstOrNull())
    }

    @Test
    fun addAllAcceptsANonCollectionIterable() {
        val heap = MinHeap<Int>()

        heap.addAll(Iterable { listOf(4, 2, 6, 1).iterator() })

        assertEquals(listOf(1, 2, 4, 6), heap.drainAll().asSequence().toList())
    }

    @Test
    fun addAllOnAnExistingHeapReheapifies() {
        val heap = MinHeap<Int>()
        heap.add(5)

        heap.addAll(listOf(3, 8, 1))

        assertEquals(listOf(1, 3, 5, 8), heap.drainAll().asSequence().toList())
    }

    @Test
    fun bulkBuildStaysLinearInComparisons() {
        val size = 10_000
        val comparator = CountingComparator(naturalOrder<Int>())

        val heap = MinHeap(comparator, (1..size).shuffled(Random(20261103)))

        assertEquals(size, heap.size)
        assertTrue(
            comparator.comparisons <= 2 * size,
            "expected a linear heapify of at most ${2 * size} comparisons, " +
                "was ${comparator.comparisons}",
        )
    }
}
