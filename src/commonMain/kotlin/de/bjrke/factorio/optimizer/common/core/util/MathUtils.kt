package de.bjrke.factorio.optimizer.common.core.util

/** Every way to give [elements] at least one each in [length] slots. */
fun <T> countVectors(elements: List<T>, length: Int) = buildList {
    if (elements.isEmpty()) return@buildList
    val current = IntArray(elements.size)

    fun assign(index: Int, left: Int, max: Int) {
        when {
            index != elements.lastIndex -> {
                for (count in 1..max) {
                    current[index] = count
                    assign(index + 1, left - count, max + 1 - count)
                }
            }
            left == max -> {
                current[index] = left
                add(current.withIndex().associate { (i, c) -> elements[i] to c })
            }
        }
    }

    assign(0, length, length + 1 - elements.size)
}

/** The greatest common divisor of [first] and [second]. */
fun greatestCommonDivisor(first: Int, second: Int): Int =
    if (second == 0) first else greatestCommonDivisor(second, first % second)
