package de.bjrke.factorio.optimizer.common.model

import kotlin.math.abs
import kotlin.math.sign

/** The squared euclidean distance, kept squared so a comparison never needs a square root. */
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

fun Coordinate.manhattanDist(b: Coordinate) = (this - b).manhattanLen

/** True when [to] is one tile orthogonally next to this. */
fun Coordinate.adjacent(to: Coordinate) = manhattanDist(to) == 1

fun Coordinate.area(dimension: Dimension) = Area(this, dimension)

/** The one-tile [Area] at this coordinate; the shared [areaTile] single-tile case. */
fun Coordinate.area() = areaTile(x, y)

fun Coordinate.area(width: Int, height: Int) = area(Dimension(width, height))

fun Coordinate.north(amount: Int = 1) = copy(y = y - amount)

fun Coordinate.south(amount: Int = 1) = copy(y = y + amount)

fun Coordinate.east(amount: Int = 1) = copy(x = x + amount)

fun Coordinate.west(amount: Int = 1) = copy(x = x - amount)

val Coordinate.direction: Coordinate
    get() {
        val newX = x.sign
        val newY = y.sign
        return if (x == newX && y == newY) this else Coordinate(newX, newY)
    }
