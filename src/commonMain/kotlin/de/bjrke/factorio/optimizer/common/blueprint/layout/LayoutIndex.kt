package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate

/**
 * The tile-to-entity lookup and the extent of a fixed entity list: the read-only counterpart of
 * [LayoutBoard] for a list that is being checked or compacted. It lives in `layout` because both
 * the invariant checks and the compaction passes read it, and neither may depend on the other.
 *
 * The lookup is one dense rectangle over the entities' extent: a slot is `x + y * width` counting
 * from [minX]/[minY], so a tile query is an array access instead of a hashed [Coordinate]. The grid
 * holds the entities themselves, so a hit needs no second lookup. The later entity wins a tile two
 * entities claim.
 *
 * @param onOverlap hears each such tile while the grid is filled, so a caller that must report
 *   overlaps needs no second tile map.
 */
internal class LayoutIndex(
    val entities: List<LayoutEntity>,
    onOverlap: ((Coordinate) -> Unit)? = null,
) {
    val minX: Int
    val maxX: Int
    val minY: Int
    val maxY: Int

    /** The extent's width, or 0 when no entity covers a tile. */
    private val width: Int

    /**
     * One slot per tile of the extent, holding the entity covering it or `null` for an empty tile.
     */
    private val grid: Array<LayoutEntity?>

    init {
        // nothing covers a tile; a zero-size area at the origin keeps the bounds finite and the
        // zero width makes at() miss every tile
        val extent = layoutExtent(entities) ?: Area(0, 0, 0, 0)
        minX = extent.x
        maxX = extent.endX - 1
        minY = extent.y
        maxY = extent.endY - 1
        width = extent.width
        grid = arrayOfNulls(width * extent.height)
        if (grid.isNotEmpty()) {
            for (entity in entities) {
                entity.area.forEachTile { x, y ->
                    val slot = (x - minX) + (y - minY) * width
                    val previous = grid[slot]
                    if (onOverlap != null && previous != null && previous !== entity) {
                        onOverlap(Coordinate(x, y))
                    }
                    grid[slot] = entity
                }
            }
        }
    }

    val isEmpty
        get() = grid.isEmpty()

    /** The entity covering [tile], or null when nothing does or the tile is outside the extent. */
    fun at(tile: Coordinate): LayoutEntity? {
        val x = tile.x - minX
        val y = tile.y - minY
        if (x < 0 || y < 0 || x >= width) return null
        val slot = x + y * width
        return if (slot >= grid.size) null else grid[slot]
    }
}

/**
 * The bounding area of [entities], or null when they cover no tile. An entity with an empty area
 * contributes nothing, just as iterating it would.
 */
internal fun layoutExtent(entities: List<LayoutEntity>): Area? {
    var lowX = Int.MAX_VALUE
    var highX = Int.MIN_VALUE
    var lowY = Int.MAX_VALUE
    var highY = Int.MIN_VALUE
    for (entity in entities) {
        val area = entity.area
        if (area.isEmpty()) continue
        lowX = minOf(lowX, area.x)
        highX = maxOf(highX, area.endX)
        lowY = minOf(lowY, area.y)
        highY = maxOf(highY, area.endY)
    }
    if (lowX > highX || lowY > highY) return null
    return Area(lowX, lowY, highX - lowX, highY - lowY)
}
