package de.bjrke.factorio.optimizer.common.util

/**
 * The connected components of [items]: every item appears in exactly one component, and two items
 * belong together when [neighbors] reaches one from the other. Components are returned in the order
 * of their first item, each as a set in discovery order.
 */
internal fun <T> connectedComponents(
    items: Iterable<T>,
    neighbors: (T) -> Iterable<T>,
): List<Set<T>> {
    val seen = mutableSetOf<T>()
    val components = mutableListOf<Set<T>>()
    for (start in items) {
        if (!seen.add(start)) continue
        val component = mutableSetOf<T>()
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            component += current
            for (next in neighbors(current)) if (seen.add(next)) queue += next
        }
        components += component
    }
    return components
}
