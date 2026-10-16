package de.bjrke.factorio.optimizer.common.model

import kotlin.math.abs

data class Coordinate(val x: Int, val y: Int) {
    operator fun plus(other: Coordinate) = Coordinate(x + other.x, y + other.y)

    operator fun minus(other: Coordinate) = Coordinate(x - other.x, y - other.y)

    operator fun times(factor: Int) = Coordinate(x * factor, y * factor)

    fun distanceSq(b: Coordinate): Long {
        val dx = x.toLong() - b.x
        val dy = y.toLong() - b.y
        return dx * dx + dy * dy
    }

    fun chebyshevDistance(b: Coordinate) = maxOf(abs(x - b.x), abs(y - b.y))

    fun area(dimension: Dimension) = Area(this, dimension)

    fun area(width: Int = 1, height: Int = 1) = area(Dimension(width, height))

    fun north(amount: Int = 1) = copy(y = y - amount)

    fun south(amount: Int = 1) = copy(y = y + amount)

    fun east(amount: Int = 1) = copy(x = x + amount)

    fun west(amount: Int = 1) = copy(x = x - amount)
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

/** A one-tile area whose only coordinate is ([x], [y]). */
fun AreaTile(x: Int, y: Int) = Area(Coordinate(x, y), Dimension(1, 1))
