package de.bjrke.util

import kotlin.jvm.JvmName

/**
 * Checks if this iterable shares at least one element with the [other] iterable, short-circuiting
 * at the first common element found without allocating a full intersection set.
 */
infix fun <T> Iterable<T>.intersects(other: Iterable<T>) =
    interSects(this, other as? Set<T> ?: other.toSet())

@JvmName("iterableInterSectsSet")
infix fun <T> Iterable<T>.interSects(otherSet: Set<T>) = interSects(this, otherSet)

infix fun <T> Set<T>.intersects(other: Iterable<T>) = interSects(other, this)

private fun <T> interSects(a: Iterable<T>, otherSet: Set<T>): Boolean = a.any { it in otherSet }
