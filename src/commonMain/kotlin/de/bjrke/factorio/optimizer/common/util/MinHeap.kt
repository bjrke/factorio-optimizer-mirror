package de.bjrke.factorio.optimizer.common.util

/**
 * A binary min-heap ordered by [comparator]; commonMain has no priority queue and A* needs one.
 * Naturally ordered elements can use `MinHeap<T>()`, which orders them by themselves.
 *
 * Reading and removing are separate: [first]/[firstOrNull] only look at the smallest element,
 * [removeFirst]/[removeFirstOrNull] (and the [poll] alias) take it out.
 */
class MinHeap<T : Any>(private val comparator: Comparator<T>) {
    private val items = mutableListOf<T>()

    /** Builds a heap ordered by [comparator] from [elements] with one linear heapify. */
    constructor(comparator: Comparator<T>, elements: Iterable<T>) : this(comparator) {
        addAll(elements)
    }

    val size: Int
        get() = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun add(element: T): Boolean {
        items.add(element)
        items[this.siftUp(items.lastIndex, element)] = element
        return true
    }

    /**
     * Adds [elements] and re-heapifies once, so adding many elements costs one linear heapify
     * instead of a sift-up per element. Returns whether anything was added.
     */
    fun addAll(elements: Iterable<T>): Boolean {
        val sizeBefore = size
        for (element in elements) items.add(element)
        val added = sizeBefore != size
        if (added) this.heapify()
        return added
    }

    /** Returns the smallest element without removing it, or `null` if this [MinHeap] is empty. */
    fun firstOrNull(): T? = items.firstOrNull()

    /**
     * Returns the smallest element without removing it, or throws [NoSuchElementException] if this
     * [MinHeap] is empty.
     */
    fun first(): T = firstOrNull() ?: throw NoSuchElementException("MinHeap is empty.")

    /** Removes the smallest element and returns it, or `null` if this [MinHeap] is empty. */
    fun removeFirstOrNull(): T? {
        val last = items.removeLastOrNull() ?: return null
        if (items.isEmpty()) return last

        val top = items[0]
        items[this.siftDown(0, last)] = last
        return top
    }

    /**
     * Removes the smallest element from this [MinHeap] and returns that removed element, or throws
     * [NoSuchElementException] if this [MinHeap] is empty.
     */
    fun removeFirst(): T = removeFirstOrNull() ?: throw NoSuchElementException("MinHeap is empty.")

    /** Alias for [removeFirst], so code can read like `PriorityQueue.poll`. */
    fun poll(): T = removeFirst()

    /**
     * Drains this [MinHeap] from smallest to largest. The iterator removes an element only when
     * [Iterator.next] is called, so [Iterator.hasNext] leaves the heap untouched.
     */
    fun drainAll(): Iterator<T> =
        // removeFirst() throws NoSuchElementException; the rule only inspects next()'s own body.
        @Suppress("IteratorNotThrowingNoSuchElementException")
        object : Iterator<T> {
            override fun hasNext(): Boolean = !isEmpty()

            override fun next(): T = removeFirst()
        }

    companion object {
        /**
         * A heap for naturally ordered elements, so callers without a comparator keep
         * `MinHeap<T>()`.
         */
        operator fun <T : Comparable<T>> invoke(): MinHeap<T> = MinHeap(naturalOrder())

        /** Builds a naturally ordered heap from [elements] with one linear heapify. */
        operator fun <T : Comparable<T>> invoke(elements: Iterable<T>) =
            MinHeap(naturalOrder(), elements)

        /** Moves [item] from [index] towards the root until its parent is not greater. */
        private tailrec fun <T : Any> MinHeap<T>.siftUp(index: Int, item: T): Int {
            if (index <= 0) return 0

            val parent = (index - 1) / 2
            val parentValue = items[parent]
            if (comparator.compare(item, parentValue) >= 0) return index

            items[index] = parentValue
            return siftUp(parent, item)
        }

        /**
         * Moves [item] from [index] towards the leaves while a child is smaller, and returns its
         * index.
         */
        private tailrec fun <T : Any> MinHeap<T>.siftDown(index: Int, item: T): Int {
            val left = index * 2 + 1
            if (left >= items.size) return index

            var smallest = left
            var smallestValue = items[smallest]

            val right = left + 1
            if (right < items.size) {
                val rightValue = items[right]
                if (comparator.compare(rightValue, smallestValue) < 0) {
                    smallest = right
                    smallestValue = rightValue
                }
            }
            if (comparator.compare(item, smallestValue) <= 0) return index

            items[index] = smallestValue
            return this.siftDown(smallest, item)
        }

        /**
         * Restores the invariant bottom-up over an arbitrary array. A node at height `h` moves at
         * most `h` levels, and there are about `n / 2^(h+1)` nodes at that height, so the sum `n/2
         * · Σ h/2^h` over all heights is `O(n)` — a linear build, unlike one sift-up per element.
         */
        private fun <T : Any> MinHeap<T>.heapify() {
            for (index in items.size / 2 - 1 downTo 0) {
                val item = items[index]
                items[siftDown(index, item)] = item
            }
        }
    }
}
