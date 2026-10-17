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
                    corridor.values.any { it.first < westBound }
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

/**
 * The invariant checks a compacted layout has to keep. It is deliberately absolute rather than a
 * diff: the original layout satisfies these, so any corridor that keeps them cannot merge two
 * materials, split a fluid network, misalign an underground run or leave an inserter without a
 * source or target.
 */
internal fun validateLayout(entities: List<LayoutEntity>): List<String> {
    val errors = mutableListOf<String>()
    val tiles = HashMap<Coordinate, LayoutEntity>()
    for (entity in entities) {
        for (tile in entity.area) {
            val previous = tiles.put(tile, entity)
            if (previous != null && previous !== entity) errors += "overlap at $tile"
        }
    }
    val index = LayoutIndex(entities)

    for (entity in entities) {
        if (entity is ConveyorEntity) {
            val tile = entity.area.topLeft
            val ahead = index.at(tile + entity.direction.step)
            if (ahead is ConveyorEntity && ahead.material != entity.material) {
                errors +=
                    "${entity.material.name} runs into ${ahead.material.name} at " +
                        "${tile + entity.direction.step}"
            }
            for (side in fourNeighbours(tile)) {
                val other = index.at(side) as? ConveyorEntity ?: continue
                if (other.direction.step == tile - side && other.material != entity.material) {
                    errors += "foreign side-load into ${entity.material.name} at $tile"
                }
            }
        }
        if (entity is Plumbing) {
            val tile = entity.area.topLeft
            for (side in fourNeighbours(tile)) {
                val other = index.at(side) as? Plumbing ?: continue
                if (other.material != entity.material) {
                    errors +=
                        "fluids ${entity.material.name} and ${other.material.name} touch at $side"
                }
            }
        }
    }

    for (input in entities.filterIsInstance<UndergroundEntity>().filter { it.io == Io.INPUT }) {
        if (findUndergroundOutput(index, input) == null) {
            errors += "unpaired ${input.material.name} underground at ${input.area.topLeft}"
        }
    }

    for (inserter in entities.filterIsInstance<InserterEntity>()) {
        val reach = if (inserter.long) 2 else 1
        val source = index.at(inserter.area.topLeft + inserter.direction.step * reach)
        when {
            source == null || (source !is MachineEntity && source !is TransportEntity) ->
                errors += "inserter at ${inserter.area.topLeft} has no source"
            source is TransportEntity && source.material != inserter.material ->
                errors +=
                    "inserter at ${inserter.area.topLeft} draws ${source.material.name} " +
                        "instead of ${inserter.material.name}"
        }
        val drop = index.at(inserter.area.topLeft - inserter.direction.step)
        when {
            drop == null || (drop !is MachineEntity && drop !is TransportEntity) ->
                errors += "inserter at ${inserter.area.topLeft} has no drop"
            drop is TransportEntity && drop.material != inserter.material ->
                errors +=
                    "inserter at ${inserter.area.topLeft} drops ${drop.material.name} " +
                        "instead of ${inserter.material.name}"
        }
    }
    return errors
}

/** Every tile of every entity, so a tile query is O(1). */
private class LayoutIndex(val entities: List<LayoutEntity>) {
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

private fun fourNeighbours(tile: Coordinate) =
    listOf(tile.north(), tile.east(), tile.south(), tile.west())

private fun primaryRange(index: LayoutIndex, orientation: SeamOrientation) =
    if (orientation == SeamOrientation.VERTICAL) index.minY..index.maxY else index.minX..index.maxX

private fun secondaryRange(index: LayoutIndex, orientation: SeamOrientation) =
    if (orientation == SeamOrientation.VERTICAL) index.minX..index.maxX else index.minY..index.maxY

private fun tileAt(orientation: SeamOrientation, primary: Int, secondary: Int) =
    if (orientation == SeamOrientation.VERTICAL) Coordinate(secondary, primary)
    else Coordinate(primary, secondary)

private fun primaryOf(orientation: SeamOrientation, tile: Coordinate) =
    if (orientation == SeamOrientation.VERTICAL) tile.y else tile.x

private fun secondaryOf(orientation: SeamOrientation, tile: Coordinate) =
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
private class SeamGrid(
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
private fun findSeam(grid: SeamGrid): CompactedSeam? {
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
        is MarkerEntity -> copy(area = moved)
        is PipeEntity -> copy(area = moved)
        is PipeToGroundEntity -> copy(area = moved)
        is PoleEntity -> copy(area = moved)
    }
}

private fun findUndergroundOutput(
    index: LayoutIndex,
    input: UndergroundEntity,
): UndergroundEntity? {
    val step = input.direction.step
    val range =
        when {
            input is PipeToGroundEntity -> 9
            input.name.contains("turbo") -> 11
            input.name.contains("express") -> 9
            input.name.contains("fast") -> 7
            else -> 5
        }
    for (distance in 2..range) {
        val candidate = index.at(input.area.topLeft + step * distance)
        if (candidate !is UndergroundEntity || candidate.io != Io.OUTPUT) continue
        if (candidate.direction != input.direction) continue
        if (candidate.material != input.material) continue
        if ((input is PipeToGroundEntity) != (candidate is PipeToGroundEntity)) continue
        return candidate
    }
    return null
}
