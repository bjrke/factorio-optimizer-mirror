package de.bjrke.factorio.optimizer.common.model

/**
 * A tile position. The arithmetic operators stay on the type; the geometry and compass helpers live
 * in `CoordinateGeometry.kt`, so a tile stays a small value and the helpers can be read as one
 * vocabulary.
 */
data class Coordinate(val x: Int, val y: Int) {
    operator fun plus(other: Coordinate) = Coordinate(x + other.x, y + other.y)

    operator fun minus(other: Coordinate) = Coordinate(x - other.x, y - other.y)

    operator fun times(factor: Int) = Coordinate(x * factor, y * factor)

    companion object {
        val origin = Coordinate(0, 0)

        val north = origin.north()
        val south = origin.south()
        val west = origin.west()
        val east = origin.east()
    }
}

/** A cardinal facing, stored in a blueprint as 0/4/8/12 (see the entity JSON writer). */
enum class Direction(val step: Coordinate) {
    NORTH(Coordinate.north),
    EAST(Coordinate.east),
    SOUTH(Coordinate.south),
    WEST(Coordinate.west);

    /** The facing 180° away from this one. */
    fun opposite() =
        when (this) {
            NORTH -> SOUTH
            EAST -> WEST
            SOUTH -> NORTH
            WEST -> EAST
        }
}

/** Which end of a directional entity (an underground belt or a loader) this is: its `type`. */
enum class Io {
    INPUT,
    OUTPUT,
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
