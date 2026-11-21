package de.bjrke.factorio.optimizer.common.core.model

import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST

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

/** The cardinal direction from [from] to [to]; the caller guarantees they share a row or column. */
internal fun directionBetween(from: Coordinate, to: Coordinate) =
    when {
        to.x > from.x -> EAST
        to.x < from.x -> WEST
        to.y > from.y -> SOUTH
        else -> NORTH
    }

/**
 * Which end of a directional entity (an underground belt or a loader or belt) this is: its `type`.
 */
enum class Io {
    INPUT,
    OUTPUT,
}

/** Which side of a row a lane serves: the feed above its machines, or the drain below them. */
enum class LineSide {
    NORTH,
    SOUTH,
}

data class Dimension(val width: Int, val height: Int) {

    init {
        require(width >= 0) { "width $width should not be negative" }
        require(height >= 0) { "height $height should not be negative" }
    }

    val size
        get() = width * height

    fun isEmpty() = width == 0 || height == 0
}

data class Area(val topLeft: Coordinate, val dimension: Dimension) : Set<Coordinate> {
    override val size
        get() = dimension.size

    inline val x
        get() = topLeft.x

    inline val y
        get() = topLeft.y

    /** The first column past the tiles: `endX - x == width`, so the columns are `x until endX`. */
    inline val endX
        get() = x + width

    /** The first row past the tiles, the row counterpart of [endX]: `y until endY` is [height]. */
    inline val endY
        get() = y + height

    inline val width
        get() = dimension.width

    inline val height
        get() = dimension.height

    override fun isEmpty() = dimension.isEmpty()

    override fun contains(element: Coordinate) =
        element.x >= x && element.y >= y && element.x < endX && element.y < endY

    override fun iterator() =
        object : Iterator<Coordinate> {
            private val endX = this@Area.endX
            private val endY = this@Area.endY
            private var nextX = x
            private var nextY = y

            override fun hasNext() = nextX < endX && nextY < endY

            override fun next(): Coordinate {
                if (!hasNext()) throw NoSuchElementException()
                val tile = Coordinate(nextX, nextY)
                nextY++
                if (nextY >= endY) {
                    nextY = y
                    nextX++
                }
                return tile
            }
        }

    override fun containsAll(elements: Collection<Coordinate>) = elements.all(::contains)

    fun grow(amount: Int) =
        Area(
            Coordinate(x - amount, y - amount),
            Dimension(width + amount * 2, height + amount * 2),
        )

    /**
     * The tiles orthogonally outside this area: the rows above and below it and the columns to its
     * left and right. Corners and the area's own tiles are not included, so a caller that scans the
     * ring around a machine sees each port tile once. An empty area has no neighbors.
     */
    fun neighbors() =
        if (isEmpty()) emptySet()
        else
            buildSet {
                for (nx in x until endX) {
                    add(Coordinate(nx, y - 1))
                    add(Coordinate(nx, endY))
                }
                for (ny in y until endY) {
                    add(Coordinate(x - 1, ny))
                    add(Coordinate(endX, ny))
                }
            }

    companion object {
        operator fun invoke(x: Int, y: Int, width: Int, height: Int) =
            Area(Coordinate(x, y), Dimension(width, height))
    }
}

/**
 * A one-tile area whose only coordinate is ([x], [y]); the single definition of the one-tile case
 * shared by every one-tile entity and by the no-argument `Coordinate.area()`.
 */
fun areaTile(x: Int, y: Int) = Area(x, y, 1, 1)

/**
 * The smallest area that contains every non-empty area of this sequence, or `null` when it holds
 * none. An empty area covers no tile, so it is skipped instead of widening the box.
 */
tailrec fun Iterator<Area>.boundingBox(): Area? {
    if (!hasNext()) return null

    val first = next()
    if (first.isEmpty()) return boundingBox()

    var x = first.x
    var y = first.y
    var endX = first.endX
    var endY = first.endY

    for (next in this) {
        if (next.isEmpty()) continue
        if (next.x < x) x = next.x
        if (next.y < y) y = next.y
        if (next.endX > endX) endX = next.endX
        if (next.endY > endY) endY = next.endY
    }

    return Area(x, y, endX - x, endY - y)
}

/**
 * The smallest area that contains every non-empty area of this sequence, or `null` when it holds
 * none. An empty area covers no tile, so it is skipped instead of widening the box.
 */
fun Iterable<Area>.boundingBox() = iterator().boundingBox()
