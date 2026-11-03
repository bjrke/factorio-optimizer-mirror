package de.bjrke.factorio.optimizer.common.util

import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The removal side of [MinHeap]: reading versus removing, what a poll costs the heap, and what the
 * empty heap answers. Ordering itself is covered by [MinHeapTest].
 */
class MinHeapRemovalTest {

    @Test
    fun emptyHeapAnswersWithNoElement() {
        val heap = MinHeap<Int>()

        assertAll(
            { assertTrue(heap.isEmpty()) },
            { assertEquals(0, heap.size) },
            { assertNull(heap.firstOrNull()) },
            { assertNull(heap.removeFirstOrNull()) },
            { assertEquals(emptyList(), heap.drainAll().asSequence().toList()) },
        )
    }

    @Test
    fun readingOrRemovingOnEmptyHeapThrowsWithMessage() {
        assertAll(
            { assertEmptyMessage(assertFailsWith { MinHeap<Int>().first() }) },
            { assertEmptyMessage(assertFailsWith { MinHeap<Int>().removeFirst() }) },
            { assertEmptyMessage(assertFailsWith { MinHeap<Int>().poll() }) },
        )
    }

    @Test
    fun firstAndFirstOrNullReadWithoutRemoving() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(5, 1, 8))

        assertAll(
            { assertEquals(1, heap.first()) },
            { assertEquals(1, heap.firstOrNull()) },
            { assertEquals(3, heap.size) },
            { assertEquals(listOf(1, 5, 8), heap.drainAll().asSequence().toList()) },
        )
    }

    @Test
    fun removeFirstOnSingleElementEmptiesTheHeap() {
        val heap = MinHeap<Int>()
        heap.add(7)

        assertAll(
            { assertEquals(7, heap.removeFirstOrNull()) },
            { assertTrue(heap.isEmpty()) },
            { assertNull(heap.removeFirstOrNull()) },
        )
    }

    @Test
    fun removeFirstOrNullRemovesOnlyTheSmallest() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(5, 1, 8, 3))

        assertEquals(1, heap.removeFirstOrNull())
        assertEquals(listOf(3, 5, 8), heap.drainAll().asSequence().toList())
    }

    @Test
    fun pollIsAnAliasForRemoveFirst() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(2, 4, 1))

        assertAll(
            { assertEquals(1, heap.poll()) },
            { assertEquals(2, heap.size) },
            { assertEquals(listOf(2, 4), heap.drainAll().asSequence().toList()) },
        )
    }

    @Test
    fun sizeTracksAddsAndRemovals() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(3, 1, 2))
        assertEquals(3, heap.size)

        heap.removeFirst()
        heap.add(0)
        assertEquals(3, heap.size)

        heap.drainAll().asSequence().toList()
        assertEquals(0, heap.size)
    }

    @Test
    fun drainAllEmptiesTheHeapAndASecondDrainYieldsNothing() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(2, 1, 4))

        assertEquals(listOf(1, 2, 4), heap.drainAll().asSequence().toList())
        assertAll(
            { assertTrue(heap.isEmpty()) },
            { assertEquals(0, heap.size) },
            { assertEquals(emptyList(), heap.drainAll().asSequence().toList()) },
        )
    }

    @Test
    fun partiallyConsumedDrainLeavesTheRestInTheHeap() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(5, 3, 9, 1))
        val drained = heap.drainAll()

        assertAll(
            { assertEquals(1, drained.next()) },
            { assertEquals(3, drained.next()) },
            { assertEquals(listOf(5, 9), heap.drainAll().asSequence().toList()) },
        )
    }

    @Test
    fun drainAllHasNextLeavesTheHeapUntouched() {
        val heap = MinHeap<Int>()
        heap.addAll(listOf(2, 1, 4))
        val drained = heap.drainAll()

        assertAll(
            { assertTrue(drained.hasNext()) },
            { assertEquals(3, heap.size) },
            { assertEquals(1, heap.first()) },
            { assertEquals(1, drained.next()) },
            { assertEquals(2, heap.size) },
        )
    }

    @Test
    fun drainAllNextOnAnExhaustedHeapThrows() {
        val heap = MinHeap<Int>()
        heap.add(1)
        val drained = heap.drainAll()

        assertEquals(1, drained.next())
        assertFalse(drained.hasNext())
        assertFailsWith<NoSuchElementException> { drained.next() }
    }

    @Test
    fun addAndAddAllReportWhetherTheyChangedTheHeap() {
        val heap = MinHeap<Int>()

        assertAll(
            { assertFalse(heap.addAll(emptyList())) },
            { assertTrue(heap.add(2)) },
            { assertTrue(heap.addAll(listOf(4, 3))) },
            { assertFalse(heap.addAll(emptySet())) },
            { assertEquals(listOf(2, 3, 4), heap.drainAll().asSequence().toList()) },
        )
    }

    private fun assertEmptyMessage(failure: NoSuchElementException) {
        assertEquals("MinHeap is empty.", failure.message)
    }
}
