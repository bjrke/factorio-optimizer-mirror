package de.bjrke.factorio.optimizer.common.model

import kotlin.math.abs
import kotlin.math.sign

data class Coordinate(val x: Int, val y: Int) {
    operator fun plus(other: Coordinate) = Coordinate(x + other.x, y + other.y)

    operator fun minus(other: Coordinate) = Coordinate(x - other.x, y - other.y)

    operator fun times(factor: Int) = Coordinate(x * factor, y * factor)

    fun distanceSq(b: Coordinate): Long {
        val dx = x.toLong() - b.x
        val dy = y.toLong() - b.y
        return dx * dx + dy * dy
    }

    val chebyshevLen by lazy { maxOf(abs(x), abs(y)) }

    fun chebyshevDist(b: Coordinate) = (this - b).chebyshevLen

    val manhattanLen by lazy { abs(x) + abs(y) }

    fun manhattanDist(b: Coordinate) = (this - b).manhattanLen

    /** True when [to] is one tile orthogonally next to this. */
    fun adjacent(to: Coordinate) = manhattanDist(to) == 1

    fun area(dimension: Dimension) = Area(this, dimension)

    /** The one-tile [Area] at this coordinate; the shared [areaTile] single-tile case. */
    fun area() = areaTile(x, y)

    fun area(width: Int, height: Int) = area(Dimension(width, height))

    fun north(amount: Int = 1) = copy(y = y - amount)

    fun south(amount: Int = 1) = copy(y = y + amount)

    fun east(amount: Int = 1) = copy(x = x + amount)

    fun west(amount: Int = 1) = copy(x = x - amount)

    companion object {
        val origin = Coordinate(0, 0)

        val north = origin.north()
        val south = origin.south()
        val west = origin.west()
        val east = origin.east()
    }

    val direction by lazy {
        val newX = x.sign
        val newY = y.sign
        if (x == newX && y == newY) this else Coordinate(newX, newY)
    }
}

data class Dimension(val width: Int, val height: Int) {

    init {
        require(width >= 0) { "width $width should not be negative" }
        require(height >= 0) { "height $height should not be negative" }
    }

    val size = width * height

    private val empty = size == 0

    fun isEmpty() = empty
}

data class Area(val topLeft: Coordinate, val dimension: Dimension) : Set<Coordinate> {
    override val size
        get() = dimension.size

    inline val x
        get() = topLeft.x

    inline val y
        get() = topLeft.y

    val bottomRight by lazy {
        Coordinate(x + width - 1, y + height - 1)
    }

    inline val width
        get() = dimension.width

    inline val height
        get() = dimension.height

    override fun isEmpty() = dimension.isEmpty()

    val xRange = x until x + width
    val yRange = y until y + height

    override fun contains(element: Coordinate) =
        xRange.contains(element.x) && yRange.contains(element.y)

    override fun iterator() = iterator {
        for (x in xRange) for (y in yRange) yield(Coordinate(x, y))
    }

    override fun containsAll(elements: Collection<Coordinate>) = elements.all(::contains)

    fun grow(amount: Int) =
        Area(
            Coordinate(x - amount, y - amount),
            Dimension(width + amount * 2, height + amount * 2),
        )
}

/**
 * A one-tile area whose only coordinate is ([x], [y]); the single definition of the one-tile case
 * shared by every one-tile entity and by the no-argument `Coordinate.area()`.
 */
fun areaTile(x: Int, y: Int) = Area(Coordinate(x, y), Dimension(1, 1))
