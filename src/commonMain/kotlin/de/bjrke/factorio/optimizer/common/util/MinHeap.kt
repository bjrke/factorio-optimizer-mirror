package de.bjrke.factorio.optimizer.common.util

/**
 * A binary min-heap ordered by [comparator]; commonMain has no priority queue and A* needs one.
 * Naturally ordered elements can use `MinHeap<T>()`, which orders them by themselves.
 */
class MinHeap<T>(private val comparator: Comparator<T>) {
    private val items = mutableListOf<T>()

    fun isEmpty(): Boolean = items.isEmpty()

    fun add(item: T) {
        items.add(item)
        items[siftUp(items.lastIndex, item)] = item
    }

    private tailrec fun siftUp(index: Int, item: T): Int {
        if (index <= 0) return 0

        val parent = (index - 1) / 2
        val parentValue = items[parent]
        if (comparator.compare(item, parentValue) >= 0) return index

        items[index] = parentValue
        return siftUp(parent, item)
    }

    /**
     * Removes the smallest element from this [MinHeap] and returns that removed element, or throws
     * [NoSuchElementException] if this [MinHeap] is empty.
     */
    fun poll(): T {
        val last = items.removeLast()
        if (items.isEmpty()) return last

        val top = items[0]
        items[siftDown(0, last)] = last
        return top
    }

    private tailrec fun siftDown(index: Int, item: T): Int {
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
        return siftDown(smallest, item)
    }

    companion object {
        /**
         * A heap for naturally ordered elements, so callers without a comparator keep
         * `MinHeap<T>()`.
         */
        operator fun <T : Comparable<T>> invoke(): MinHeap<T> = MinHeap(naturalOrder())
    }
}
