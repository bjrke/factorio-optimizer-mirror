package de.bjrke.factorio.optimizer.common.util

/** A binary min-heap; commonMain has no priority queue and A* needs one. */
class MinHeap<T : Comparable<T>> {
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
        if (item >= parentValue) return index

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
            if (rightValue < smallestValue) {
                smallest = right
                smallestValue = rightValue
            }
        }

        if (item <= smallestValue) return index

        items[index] = smallestValue
        return siftDown(smallest, item)
    }
}
