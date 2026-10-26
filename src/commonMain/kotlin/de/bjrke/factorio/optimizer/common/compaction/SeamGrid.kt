package de.bjrke.factorio.optimizer.common.compaction

import de.bjrke.factorio.optimizer.common.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.north
import de.bjrke.factorio.optimizer.common.model.south
import de.bjrke.factorio.optimizer.common.model.west

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
): Boolean =
    alongTiles(orientation, tile).all { side ->
        when (val other = index.at(side)) {
            null -> true
            is BeltEntity -> other.material == belt.material && other.direction == belt.direction
            else -> false
        }
    } &&
        acrossTiles(orientation, tile).none { side ->
            val other = index.at(side)
            // a perpendicular belt facing into this tile would lose its side-load
            other is ConveyorEntity && other.direction.step == tile - side
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
): Boolean =
    alongTiles(orientation, tile).all { side ->
        when (val other = index.at(side)) {
            null -> true
            is PipeEntity -> other.material == pipe.material
            // a ground end or a machine tap would lose its connection
            else -> false
        }
    } &&
        acrossTiles(orientation, tile).none { side ->
            // a branch, a ground end or a machine tap would be split
            val other = index.at(side)
            other is Plumbing || other is MachineEntity
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

private class SeamSearch(private val grid: SeamGrid) {
    private val bottleneck = HashMap<Coordinate, Int>()
    private val cost = HashMap<Coordinate, Int>()
    private val previous = HashMap<Coordinate, Coordinate>()

    /** One dynamic-programming step: the tile, its widest bottleneck and its belt/pipe cost. */
    private data class Step(val tile: Coordinate, val width: Int, val cost: Int)

    fun find(): CompactedSeam? {
        val first = grid.primaryRange.first
        val last = grid.primaryRange.last
        if (first > last) return null
        seed(first)
        return if (bottleneck.isEmpty()) null
        else {
            for (primary in first + 1..last) extend(primary)
            bestOn(last)?.let {
                CompactedSeam(grid.orientation, pathTo(it.tile), it.width)
            }
        }
    }

    /** Seeds the first primary row: every removable run is a candidate seam start. */
    private fun seed(primary: Int) {
        for (secondary in grid.secondaryRange) {
            val tile = grid.tile(primary, secondary)
            val run = grid.runs[tile] ?: continue
            bottleneck[tile] = run.last - run.first + 1
            cost[tile] = grid.cost[tile] ?: 0
        }
    }

    /** Extends the seam into [primary] from the best of the three previous-row neighbors. */
    private fun extend(primary: Int) {
        for (secondary in grid.secondaryRange) {
            val tile = grid.tile(primary, secondary)
            val run = grid.runs[tile] ?: continue
            val step =
                bestPredecessor(primary, secondary, tile, run.last - run.first + 1) ?: continue
            bottleneck[tile] = step.width
            cost[tile] = step.cost
            previous[tile] = step.tile
        }
    }

    /**
     * The previous-row tile that leaves the widest bottleneck: wider wins, then cheaper, then the
     * northernmost/leftmost tile.
     */
    private fun bestPredecessor(
        primary: Int,
        secondary: Int,
        tile: Coordinate,
        runWidth: Int,
    ): Step? {
        var best: Step? = null
        for (previousSecondary in secondary - 1..secondary + 1) {
            if (previousSecondary !in grid.secondaryRange) continue
            val previousTile = grid.tile(primary - 1, previousSecondary)
            val previousWidth = bottleneck[previousTile] ?: continue
            val previousCost = cost[previousTile] ?: continue
            val step =
                Step(
                    previousTile,
                    minOf(previousWidth, runWidth),
                    previousCost + (grid.cost[tile] ?: 0),
                )
            if (best == null || isBetter(step, best, grid.orientation)) best = step
        }
        return best
    }

    /** The best seam end on [primary]: widest bottleneck, then cheapest, then leftmost. */
    private fun bestOn(primary: Int): Step? {
        var best: Step? = null
        for (secondary in grid.secondaryRange) {
            val tile = grid.tile(primary, secondary)
            val width = bottleneck[tile] ?: continue
            val step = Step(tile, width, cost[tile] ?: 0)
            if (best == null || isBetter(step, best, grid.orientation)) best = step
        }
        return best
    }

    /** Walks the predecessor chain back into a row-to-tile path. */
    private fun pathTo(tile: Coordinate): Map<Int, Int> {
        val path = HashMap<Int, Int>()
        var current: Coordinate? = tile
        while (current != null) {
            path[grid.primary(current)] = grid.secondary(current)
            current = previous[current]
        }
        return path
    }

    private fun isBetter(candidate: Step, best: Step, orientation: SeamOrientation) =
        candidate.width > best.width ||
            (candidate.width == best.width && candidate.cost < best.cost) ||
            (candidate.width == best.width &&
                candidate.cost == best.cost &&
                compareTiles(orientation, candidate.tile, best.tile) < 0)
}

/**
 * The seam whose narrowest row still has the widest run: the layout's widest corridor. Ties go to
 * the seam that cuts fewer belts/pipes, then to the northernmost/leftmost one. A seam may step one
 * tile up or down per row.
 */
internal fun findSeam(grid: SeamGrid) = SeamSearch(grid).find()

private fun compareTiles(orientation: SeamOrientation, a: Coordinate, b: Coordinate): Int {
    val secondary = secondaryOf(orientation, a).compareTo(secondaryOf(orientation, b))
    if (secondary != 0) return secondary
    return primaryOf(orientation, a).compareTo(primaryOf(orientation, b))
}
