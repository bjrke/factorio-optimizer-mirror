package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/**
 * Finding the widest removable corridor through a layout: [SeamGrid] marks every removable run, and
 * [findSeam] walks it to the seam whose narrowest row still has the widest run.
 */
internal fun tileAt(orientation: SeamOrientation, primary: Int, secondary: Int) =
    if (orientation == SeamOrientation.VERTICAL) Coordinate(secondary, primary)
    else Coordinate(primary, secondary)

internal fun primaryOf(orientation: SeamOrientation, tile: Coordinate) =
    if (orientation == SeamOrientation.VERTICAL) tile.y else tile.x

internal fun secondaryOf(orientation: SeamOrientation, tile: Coordinate) =
    if (orientation == SeamOrientation.VERTICAL) tile.x else tile.y

/** The tiles along the corridor's flow axis; a splice joins these two sides. */
private fun alongTiles(orientation: SeamOrientation, tile: Coordinate) =
    if (orientation == SeamOrientation.VERTICAL) listOf(tile.west(), tile.east())
    else listOf(tile.north(), tile.south())

/** The tiles across the corridor: branches or side-loads here would be broken by the shift. */
private fun acrossTiles(orientation: SeamOrientation, tile: Coordinate) =
    if (orientation == SeamOrientation.VERTICAL) listOf(tile.north(), tile.south())
    else listOf(tile.east(), tile.west())

/** Whether a corridor may use this tile: empty, or a belt/pipe that can be spliced. */
private fun isRemovable(
    index: LayoutIndex,
    tile: Coordinate,
    orientation: SeamOrientation,
    forbidden: Set<Coordinate>,
): Boolean {
    if (tile in forbidden) return false
    val entity = index.at(tile) ?: return true
    return when (entity) {
        is BeltEntity ->
            when (orientation) {
                SeamOrientation.VERTICAL ->
                    (entity.direction == Direction.EAST || entity.direction == Direction.WEST) &&
                        beltCuttable(index, tile, entity, orientation)
                SeamOrientation.HORIZONTAL ->
                    (entity.direction == Direction.NORTH || entity.direction == Direction.SOUTH) &&
                        beltCuttable(index, tile, entity, orientation)
            }
        is PipeEntity -> pipeCuttable(index, tile, entity, orientation)
        else -> false
    }
}

/**
 * A belt is spliceable when the run on both sides is the same material and direction and no belt
 * side-loads into it. Anything other than a plain belt next to it (a splitter, a loader, an
 * underground end, a machine) stops the corridor, so a shift can never pull a belt off such a
 * neighbour.
 */
private fun beltCuttable(
    index: LayoutIndex,
    tile: Coordinate,
    belt: BeltEntity,
    orientation: SeamOrientation,
): Boolean {
    for (side in alongTiles(orientation, tile)) {
        when (val other = index.at(side)) {
            null -> {}
            is BeltEntity ->
                if (other.material != belt.material || other.direction != belt.direction)
                    return false
            else -> return false
        }
    }
    for (side in acrossTiles(orientation, tile)) {
        val other = index.at(side)
        // a perpendicular belt facing into this tile would lose its side-load
        if (other is ConveyorEntity && other.direction.step == tile - side) return false
    }
    return true
}

/**
 * A pipe is spliceable only on a straight run: a pipe branching north/south (for a vertical seam),
 * a pipe-to-ground end or a machine tap next to it would be disconnected or short-circuited by the
 * shift, so those stop the corridor.
 */
private fun pipeCuttable(
    index: LayoutIndex,
    tile: Coordinate,
    pipe: PipeEntity,
    orientation: SeamOrientation,
): Boolean {
    for (side in alongTiles(orientation, tile)) {
        when (val other = index.at(side)) {
            null -> {}
            is PipeEntity -> if (other.material != pipe.material) return false
            // a ground end or a machine tap would lose its connection
            else -> return false
        }
    }
    for (side in acrossTiles(orientation, tile)) {
        when (index.at(side)) {
            null -> {}
            // a branch, a ground end or a machine tap would be split
            is Plumbing,
            is MachineEntity -> return false
            else -> {}
        }
    }
    return true
}

/**
 * The removable runs of the current orientation. Each removable tile remembers the run it belongs
 * to; the run width is how wide a corridor through it can become.
 */
internal class SeamGrid(
    val orientation: SeamOrientation,
    val primaryRange: IntRange,
    val secondaryRange: IntRange,
    index: LayoutIndex,
    forbidden: Set<Coordinate>,
) {
    val runs = HashMap<Coordinate, IntRange>()
    val cost = HashMap<Coordinate, Int>()

    init {
        for (primary in primaryRange) {
            var secondary = secondaryRange.first
            while (secondary <= secondaryRange.last) {
                val tile = tileAt(orientation, primary, secondary)
                if (!isRemovable(index, tile, orientation, forbidden)) {
                    secondary++
                    continue
                }
                val start = secondary
                while (
                    secondary + 1 <= secondaryRange.last &&
                        isRemovable(
                            index,
                            tileAt(orientation, primary, secondary + 1),
                            orientation,
                            forbidden,
                        )
                ) {
                    secondary++
                }
                val run = start..secondary
                for (member in start..secondary) {
                    val memberTile = tileAt(orientation, primary, member)
                    runs[memberTile] = run
                    cost[memberTile] = if (index.at(memberTile) == null) 0 else 1
                }
                secondary++
            }
        }
    }

    fun tile(primary: Int, secondary: Int) = tileAt(orientation, primary, secondary)

    fun primary(tile: Coordinate) = primaryOf(orientation, tile)

    fun secondary(tile: Coordinate) = secondaryOf(orientation, tile)
}

/**
 * The seam whose narrowest row still has the widest run: the layout's widest corridor. Ties go to
 * the seam that cuts fewer belts/pipes, then to the northernmost/leftmost one. A seam may step one
 * tile up or down per row.
 */
internal fun findSeam(grid: SeamGrid): CompactedSeam? {
    val first = grid.primaryRange.first
    val last = grid.primaryRange.last
    if (first > last) return null

    val bottleneck = HashMap<Coordinate, Int>()
    val cost = HashMap<Coordinate, Int>()
    val previous = HashMap<Coordinate, Coordinate>()

    for (secondary in grid.secondaryRange) {
        val tile = grid.tile(first, secondary)
        val run = grid.runs[tile] ?: continue
        bottleneck[tile] = run.last - run.first + 1
        cost[tile] = grid.cost[tile] ?: 0
    }
    if (bottleneck.isEmpty()) return null

    for (primary in first + 1..last) {
        for (secondary in grid.secondaryRange) {
            val tile = grid.tile(primary, secondary)
            val run = grid.runs[tile] ?: continue
            val runWidth = run.last - run.first + 1
            var bestPrevious: Coordinate? = null
            var bestWidth = -1
            var bestCost = Int.MAX_VALUE
            for (previousSecondary in secondary - 1..secondary + 1) {
                if (previousSecondary !in grid.secondaryRange) continue
                val previousTile = grid.tile(primary - 1, previousSecondary)
                val previousWidth = bottleneck[previousTile] ?: continue
                val previousCost = cost[previousTile] ?: continue
                val width = minOf(previousWidth, runWidth)
                val total = previousCost + (grid.cost[tile] ?: 0)
                val better =
                    bestPrevious == null ||
                        width > bestWidth ||
                        (width == bestWidth && total < bestCost) ||
                        (width == bestWidth &&
                            total == bestCost &&
                            compareTiles(grid.orientation, previousTile, bestPrevious) < 0)
                if (better) {
                    bestPrevious = previousTile
                    bestWidth = width
                    bestCost = total
                }
            }
            if (bestPrevious != null) {
                bottleneck[tile] = bestWidth
                cost[tile] = bestCost
                previous[tile] = bestPrevious
            }
        }
    }

    var bestLast: Coordinate? = null
    var bestWidth = -1
    var bestCost = Int.MAX_VALUE
    for (secondary in grid.secondaryRange) {
        val tile = grid.tile(last, secondary)
        val width = bottleneck[tile] ?: continue
        val total = cost[tile] ?: 0
        val better =
            bestLast == null ||
                width > bestWidth ||
                (width == bestWidth && total < bestCost) ||
                (width == bestWidth &&
                    total == bestCost &&
                    compareTiles(grid.orientation, tile, bestLast) < 0)
        if (better) {
            bestLast = tile
            bestWidth = width
            bestCost = total
        }
    }
    val lastTile = bestLast ?: return null
    val path = HashMap<Int, Int>()
    var current: Coordinate? = lastTile
    while (current != null) {
        path[grid.primary(current)] = grid.secondary(current)
        current = previous[current]
    }
    return CompactedSeam(grid.orientation, path, bestWidth)
}

private fun compareTiles(orientation: SeamOrientation, a: Coordinate, b: Coordinate): Int {
    val secondary = secondaryOf(orientation, a).compareTo(secondaryOf(orientation, b))
    if (secondary != 0) return secondary
    return primaryOf(orientation, a).compareTo(primaryOf(orientation, b))
}
