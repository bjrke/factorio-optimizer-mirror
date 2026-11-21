package de.bjrke.factorio.optimizer.common.core.model

import kotlin.math.abs
import kotlin.math.sign

/** The squared Euclidean distance, kept squared so a comparison never needs a square root. */
fun Coordinate.distanceSq(b: Coordinate): Long {
    val dx = x.toLong() - b.x
    val dy = y.toLong() - b.y
    return dx * dx + dy * dy
}

val Coordinate.chebyshevLen: Int
    get() = maxOf(abs(x), abs(y))

fun Coordinate.chebyshevDist(b: Coordinate) = (this - b).chebyshevLen

val Coordinate.manhattanLen: Int
    get() = abs(x) + abs(y)

/** The tile distance without a subtraction, so the route search does not allocate per estimate. */
fun Coordinate.manhattanDist(b: Coordinate) = abs(x - b.x) + abs(y - b.y)

/** True when [to] is one tile orthogonally next to this. */
fun Coordinate.adjacent(to: Coordinate) = manhattanDist(to) == 1

/**
 * The one-tile [Area] at this coordinate. It shares [Dimension.ONE] with [areaTile] but builds the
 * area from this coordinate directly, so a call allocates only the [Area].
 */
fun Coordinate.area() = Area(this, Dimension.ONE)

fun Coordinate.north(amount: Int = 1) = Coordinate(x, y - amount)

fun Coordinate.south(amount: Int = 1) = Coordinate(x, y + amount)

fun Coordinate.east(amount: Int = 1) = Coordinate(x + amount, y)

fun Coordinate.west(amount: Int = 1) = Coordinate(x - amount, y)

val Coordinate.direction: Coordinate
    get() {
        val newX = x.sign
        val newY = y.sign
        return if (x == newX && y == newY) this else Coordinate(newX, newY)
    }

/**
 * The four tiles orthogonally next to this one, north first and then clockwise (north, east, south,
 * west), so every walk over the neighborhood visits them in the same order.
 */
val Coordinate.neighbors: List<Coordinate>
    get() = listOf(north(), east(), south(), west())
