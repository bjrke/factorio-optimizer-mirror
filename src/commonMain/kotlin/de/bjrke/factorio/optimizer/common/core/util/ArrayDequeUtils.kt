package de.bjrke.factorio.optimizer.common.core.util

/**
 * Applies [block] to the elements in dequeue order until the deque is empty, including the elements
 * [block] adds. The deque is the block's receiver, so a walk can enqueue its follow-up work with
 * `addLast`; [block] never sees a null element.
 */
tailrec fun <T> ArrayDeque<T>.drain(block: ArrayDeque<T>.(T) -> Unit) {
    block(removeFirstOrNull() ?: return)
    drain(block)
}
