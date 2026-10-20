package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/**
 * The two ways a layout is compacted: a vertical seam runs north to south and shrinks the width, a
 * horizontal seam runs west to east and shrinks the height.
 */
internal enum class SeamOrientation {
    VERTICAL,
    HORIZONTAL,
}

/**
 * A winding seam through the layout. [path] maps the coordinate the seam travels along (the row of
 * a vertical seam, the column of a horizontal one) to the tile it cuts in that row/column. [width]
 * is how many tiles the seam can be widened into a corridor before it hits something it cannot
 * remove.
 */
internal data class CompactedSeam(
    val orientation: SeamOrientation,
    val path: Map<Int, Int>,
    val width: Int,
)

/**
 * A layout after one seam corridor was removed, together with the corridor's [width]. [pinnedShift]
 * is how far the pinned import group moved on that axis, so the caller can keep its pin set
 * current.
 */
internal data class CompactionResult(
    val entities: List<LayoutEntity>,
    val width: Int,
    val orientation: SeamOrientation,
    val pinnedShift: Int,
)

/** How many different seams one call tries before it gives up. */
private const val MAX_SEAM_ATTEMPTS = 8

/**
 * Finds the widest removable corridor in [orientation] and removes it in one shift. The corridor is
 * bounded by the seam: every tile inside it must be empty or a spliceable belt/pipe, and the two
 * sides stay on their own side. Everything beyond the corridor moves one corridor width towards the
 * seam, so the layout shrinks by exactly that width.
 *
 * Returns `null` when no corridor shrinks the layout, or when every candidate fails
 * [validateLayout]. [forbidden] keeps a caller from finding a seam it already rejected. [pinned]
 * tiles (the import markers) may not be moved, so the import bus keeps its straight column.
 */
internal fun nextCompaction(
    entities: List<LayoutEntity>,
    orientation: SeamOrientation,
    forbidden: Set<Coordinate> = emptySet(),
    pinned: Set<Coordinate> = emptySet(),
    westBound: Int = Int.MIN_VALUE,
): CompactionResult? {
    val index = LayoutIndex(entities)
    if (index.isEmpty) return null
    val blocked = forbidden.toMutableSet()
    repeat(MAX_SEAM_ATTEMPTS) {
        val grid =
            SeamGrid(
                orientation,
                primaryRange(index, orientation),
                secondaryRange(index, orientation),
                index,
                blocked,
            )
        val seam = findSeam(grid) ?: return null
        var width = seam.width
        while (width >= 1) {
            val corridor = corridorOf(grid, seam, width) ?: break
            // never pull an imported row's factory edge west of its input lane
            if (
                orientation == SeamOrientation.VERTICAL &&
                    corridor.values.any { range -> range.first < westBound }
            ) {
                width--
                continue
            }
            val (candidate, pinnedShift) =
                applyCorridor(index, corridor, orientation, pinned, width) ?: break
            if (shrinks(index, candidate, orientation) && validateLayout(candidate).isEmpty()) {
                return CompactionResult(candidate, width, orientation, pinnedShift)
            }
            width--
        }
        // the same path would be found again; rule it out and look for another corridor
        for ((primary, secondary) in seam.path) {
            blocked += tileAt(orientation, primary, secondary)
        }
    }
    return null
}

/** Every tile of every entity, so a tile query is O(1). */
internal class LayoutIndex(val entities: List<LayoutEntity>) {
    val tiles = HashMap<Coordinate, LayoutEntity>()
    var minX = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var minY = Int.MAX_VALUE
    var maxY = Int.MIN_VALUE

    init {
        for (entity in entities) {
            for (tile in entity.area) {
                tiles[tile] = entity
                if (tile.x < minX) minX = tile.x
                if (tile.x > maxX) maxX = tile.x
                if (tile.y < minY) minY = tile.y
                if (tile.y > maxY) maxY = tile.y
            }
        }
    }

    val isEmpty
        get() = tiles.isEmpty()

    fun at(tile: Coordinate) = tiles[tile]
}

private fun primaryRange(index: LayoutIndex, orientation: SeamOrientation) =
    if (orientation == SeamOrientation.VERTICAL) index.minY..index.maxY else index.minX..index.maxX

private fun secondaryRange(index: LayoutIndex, orientation: SeamOrientation) =
    if (orientation == SeamOrientation.VERTICAL) index.minX..index.maxX else index.minY..index.maxY

/**
 * The corridor around the seam: a [width]-tile slice of each row's removable run, kept as centered
 * on the seam as the run allows. Every row removes the same number of tiles, so the far side shifts
 * by exactly [width].
 */
private fun corridorOf(
    grid: SeamGrid,
    seam: CompactedSeam,
    width: Int,
): Map<Int, IntRange>? {
    if (width < 1) return null
    val corridor = HashMap<Int, IntRange>()
    for (primary in grid.primaryRange) {
        val secondary = seam.path[primary] ?: return null
        val run = grid.runs[grid.tile(primary, secondary)] ?: return null
        val minStart = maxOf(run.first, secondary - width + 1)
        val maxStart = minOf(secondary, run.last - width + 1)
        if (minStart > maxStart) return null
        val start = minStart + (maxStart - minStart) / 2
        corridor[primary] = start..(start + width - 1)
    }
    return corridor
}

/**
 * Applies [corridor]: cut the belts/pipes inside it, move everything beyond it [width] tiles
 * towards the seam and leave the near side in place. An entity is never cut in half; a multi-tile
 * entity that would end up on both sides rejects the corridor.
 */
private fun applyCorridor(
    index: LayoutIndex,
    corridor: Map<Int, IntRange>,
    orientation: SeamOrientation,
    pinned: Set<Coordinate>,
    width: Int,
): Pair<List<LayoutEntity>, Int>? {
    val result = ArrayList<LayoutEntity>(index.entities.size)
    // every import marker/feed tile has to stay on the same side, so the markers keep one column
    var pinnedSide = 0
    for (entity in index.entities) {
        var side = 0
        var cut = false
        for (tile in entity.area) {
            val primary = primaryOf(orientation, tile)
            val secondary = secondaryOf(orientation, tile)
            val range = corridor[primary] ?: return null
            val tileSide =
                when {
                    secondary < range.first -> -1
                    secondary > range.last -> 1
                    else -> 2
                }
            if (tileSide == 2) {
                // only a plain belt/pipe is cut; every other entity is a wall
                if (entity !is BeltEntity && entity !is PipeEntity) return null
                // the import marker and its feed are the connection; never delete them
                if (pinned.any { it in entity.area }) return null
                cut = true
                break
            }
            if (side == 0) side = tileSide else if (side != tileSide) return null
        }
        if (cut) continue
        if (pinned.any { it in entity.area }) {
            if (pinnedSide == 0) pinnedSide = side else if (pinnedSide != side) return null
        }
        result += if (side == 1) entity.shifted(orientation, -width) else entity
    }
    return result to if (pinnedSide == 1) -width else 0
}

private fun shrinks(
    before: LayoutIndex,
    after: List<LayoutEntity>,
    orientation: SeamOrientation,
): Boolean {
    val afterIndex = LayoutIndex(after)
    return if (orientation == SeamOrientation.VERTICAL) {
        afterIndex.maxX - afterIndex.minX < before.maxX - before.minX
    } else {
        afterIndex.maxY - afterIndex.minY < before.maxY - before.minY
    }
}

private fun LayoutEntity.shifted(orientation: SeamOrientation, amount: Int): LayoutEntity {
    val moved =
        if (orientation == SeamOrientation.VERTICAL) area.copy(topLeft = area.topLeft.east(amount))
        else area.copy(topLeft = area.topLeft.south(amount))
    return when (this) {
        is BeltEntity -> copy(area = moved)
        is UndergroundBeltEntity -> copy(area = moved)
        is SplitterEntity -> copy(area = moved)
        is MachineEntity -> copy(area = moved)
        is InserterEntity -> copy(area = moved)
        is LoaderEntity -> copy(area = moved)
        is InfinityChestEntity -> copy(area = moved)
        is InfinityPipeEntity -> copy(area = moved)
        is ChestEntity -> copy(area = moved)
        is MarkerEntity -> copy(area = moved)
        is PipeEntity -> copy(area = moved)
        is PipeToGroundEntity -> copy(area = moved)
        is PoleEntity -> copy(area = moved)
    }
}
