package de.bjrke.factorio.optimizer.common.blueprint.placing.compaction

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.isSingleMaterial
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.core.model.north
import de.bjrke.factorio.optimizer.common.core.model.south
import de.bjrke.factorio.optimizer.common.core.model.west

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
 * A belt is spliceable when the run on both sides is the same material and direction, or when it is
 * a corner: the belt behind it feeds it straight and the tile ahead is a perpendicular belt of the
 * same material. Dropping the corner frees the row for the shift and lets the belt behind side-load
 * the lane that moved up. No belt may side-load into the cut tile. Anything other than a plain belt
 * next to it (a loader, an underground end, a machine) stops the corridor, so a shift can never
 * pull a belt off such a neighbor.
 *
 * A tunnel output right behind the belt is the one exception: removing the belt lands the output on
 * its tile, so the output takes over the feed and only the straight consumer ahead has to continue
 * the lane. That consumer may be a splitter, which takes items on its back.
 */
private fun beltCuttable(
    index: LayoutIndex,
    tile: Coordinate,
    belt: BeltEntity,
    orientation: SeamOrientation,
): Boolean {
    for (side in acrossTiles(orientation, tile)) {
        val other = index.at(side)
        // a perpendicular belt facing into this tile would lose its side-load
        if (other is ConveyorEntity && other.direction.step == tile - side) return false
    }
    val behind = index.at(tile - belt.direction.step)
    val ahead = index.at(tile + belt.direction.step)

    val takesFromBehind =
        behind == null ||
            behind is BeltEntity &&
                behind.direction == belt.direction &&
                belt.load.takesStraightFrom(behind.load)
    val takesFromAhead =
        ahead == null ||
            ahead is BeltEntity &&
                ahead.direction == belt.direction &&
                ahead.load.takesStraightFrom(belt.load)

    return when {
        takesFromBehind && takesFromAhead -> true
        replacedByTunnelOutput(behind, ahead, belt) -> true

        // a corner into a perpendicular lane: drop the turn belt, the one behind it turns into the
        // lane
        else -> cornersIntoPerpendicularLane(behind, ahead, belt)
    }
}

/**
 * Whether the belt behind turns into a perpendicular lane [belt] can feed: the lane head the shift
 * moves onto the corner keeps the feeder's lane frame, so a mixed feeder survives only when both
 * sides agree lane for lane. Material sets alone would splice a swapped corner and merge its lanes.
 */
private fun cornersIntoPerpendicularLane(
    behind: LayoutEntity?,
    ahead: LayoutEntity?,
    belt: BeltEntity,
): Boolean =
    behind is BeltEntity &&
        behind.direction == belt.direction &&
        behind.load.sameMaterialsAs(belt.load) &&
        ahead is BeltEntity &&
        ahead.direction != belt.direction &&
        ahead.direction != belt.direction.opposite() &&
        ahead.load.takesStraightFrom(behind.load)

/**
 * Whether the tunnel output behind [belt] takes over its feed when a corridor removes it: the
 * output lands on the belt's tile and pours into [ahead], so the belt itself is redundant. The
 * output and the consumer must carry the belt's single material and face along the lane; a splitter
 * is a consumer because it takes items on its back.
 */
private fun replacedByTunnelOutput(
    behind: LayoutEntity?,
    ahead: LayoutEntity?,
    belt: BeltEntity,
): Boolean =
    behind is UndergroundBeltEntity &&
        behind.io == Io.OUTPUT &&
        behind.direction == belt.direction &&
        behind.isSingleMaterial(belt.load.material) &&
        belt.isSingleMaterial(behind.material) &&
        when (ahead) {
            is BeltEntity ->
                ahead.direction == belt.direction && ahead.load.sameMaterialsAs(belt.load)
            is SplitterEntity ->
                ahead.direction == belt.direction && ahead.isSingleMaterial(behind.material)
            else -> false
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
 *
 * The runs are flat arrays over the exact rectangle [primaryRange] x [secondaryRange], indexed by
 * `(primary - primaryRange.first) * columns + (secondary - secondaryRange.first)`: compaction
 * builds this grid for every candidate seam, so a `Coordinate`-keyed map would hash on the hot
 * path. [undergroundSpans] is passed in because it is the same for every grid of one orientation.
 */
internal class SeamGrid(
    val orientation: SeamOrientation,
    val primaryRange: IntRange,
    val secondaryRange: IntRange,
    index: LayoutIndex,
    forbidden: Set<Coordinate>,
    undergroundSpans: Set<Coordinate>,
) {
    /** Tiles across the seam: the secondary axis of the scanned block. */
    val columns = secondaryRange.last - secondaryRange.first + 1

    /** One array slot per tile of the scanned block. */
    val size = (primaryRange.last - primaryRange.first + 1) * columns

    // -1 marks a tile outside any removable run; the ends are row offsets, because the absolute
    // secondary coordinate may itself be -1
    private val runFrom = IntArray(size) { -1 }
    private val runTo = IntArray(size) { -1 }
    private val runCost = IntArray(size)

    init {
        val blocked = forbidden + undergroundSpans
        for (primary in primaryRange) {
            var secondary = secondaryRange.first
            while (secondary <= secondaryRange.last) {
                val tile = tileAt(orientation, primary, secondary)
                if (!isRemovable(index, tile, orientation, blocked)) {
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
                            blocked,
                        )
                ) {
                    secondary++
                }
                for (member in start..secondary) {
                    val offset = offset(primary, member)
                    runFrom[offset] = start - secondaryRange.first
                    runTo[offset] = secondary - secondaryRange.first
                    runCost[offset] =
                        if (index.at(tileAt(orientation, primary, member)) == null) 0 else 1
                }
                secondary++
            }
        }
    }

    fun offset(primary: Int, secondary: Int) =
        (primary - primaryRange.first) * columns + (secondary - secondaryRange.first)

    /** The removable run through the tile at [offset], or null when it is not removable. */
    fun run(offset: Int): IntRange? {
        val from = runFrom[offset]
        return if (from < 0) null
        else (secondaryRange.first + from)..(secondaryRange.first + runTo[offset])
    }

    fun cost(offset: Int) = runCost[offset]

    fun primaryAt(offset: Int) = primaryRange.first + offset / columns

    fun secondaryAt(offset: Int) = secondaryRange.first + offset % columns
}

private class SeamSearch(private val grid: SeamGrid) {
    private val columns = grid.columns

    // -1 marks an unreachable tile
    private val bottleneck = IntArray(grid.size) { -1 }
    private val cost = IntArray(grid.size)
    private val previous = IntArray(grid.size) { -1 }

    fun find(): CompactedSeam? {
        val first = grid.primaryRange.first
        val last = grid.primaryRange.last
        if (first > last) return null
        seed(first)
        for (primary in first + 1..last) extend(primary)
        val best = bestOn(last) ?: return null
        return CompactedSeam(grid.orientation, pathTo(best), bottleneck[best])
    }

    /** Seeds the first primary row: every removable run is a candidate seam start. */
    private fun seed(primary: Int) {
        for (secondary in grid.secondaryRange) {
            val offset = grid.offset(primary, secondary)
            val run = grid.run(offset) ?: continue
            bottleneck[offset] = run.last - run.first + 1
            cost[offset] = grid.cost(offset)
        }
    }

    /** Extends the seam into [primary] from the best of the three previous-row neighbors. */
    private fun extend(primary: Int) {
        for (secondary in grid.secondaryRange) {
            val offset = grid.offset(primary, secondary)
            val run = grid.run(offset) ?: continue
            val runWidth = run.last - run.first + 1
            val predecessor = bestPredecessor(primary, secondary, offset, runWidth) ?: continue
            bottleneck[offset] = minOf(bottleneck[predecessor], runWidth)
            cost[offset] = cost[predecessor] + grid.cost(offset)
            previous[offset] = predecessor
        }
    }

    // The best candidate of the pass in progress; reset before each pass.
    private var best = -1
    private var bestWidth = 0
    private var bestCost = 0

    /**
     * The previous-row tile that leaves the widest bottleneck: wider wins, then cheaper, then the
     * northernmost/leftmost tile.
     */
    private fun bestPredecessor(
        primary: Int,
        secondary: Int,
        offset: Int,
        runWidth: Int,
    ): Int? {
        resetBest()
        val tileCost = grid.cost(offset)
        for (previousSecondary in secondary - 1..secondary + 1) {
            if (previousSecondary !in grid.secondaryRange) continue
            val previousOffset = grid.offset(primary - 1, previousSecondary)
            val previousWidth = bottleneck[previousOffset]
            if (previousWidth < 0) continue
            val width = minOf(previousWidth, runWidth)
            choose(width, cost[previousOffset] + tileCost, previousOffset)
        }
        return if (best < 0) null else best
    }

    /** The best seam end on [primary]: widest bottleneck, then cheapest, then leftmost. */
    private fun bestOn(primary: Int): Int? {
        resetBest()
        for (secondary in grid.secondaryRange) {
            val offset = grid.offset(primary, secondary)
            val width = bottleneck[offset]
            if (width < 0) continue
            choose(width, cost[offset], offset)
        }
        return if (best < 0) null else best
    }

    /** Walks the predecessor chain back into a row-to-tile path. */
    private fun pathTo(tile: Int): Map<Int, Int> {
        val path = HashMap<Int, Int>()
        var current = tile
        while (current >= 0) {
            path[grid.primaryAt(current)] = grid.secondaryAt(current)
            current = previous[current]
        }
        return path
    }

    private fun resetBest() {
        best = -1
        bestWidth = 0
        bestCost = 0
    }

    private fun choose(width: Int, cost: Int, tile: Int) {
        val better =
            when {
                best < 0 -> true
                width != bestWidth -> width > bestWidth
                cost != bestCost -> cost < bestCost
                else -> compareTiles(tile, best) < 0
            }
        if (better) {
            best = tile
            bestWidth = width
            bestCost = cost
        }
    }

    /** The final tie-break: the northernmost/leftmost tile wins. */
    private fun compareTiles(a: Int, b: Int): Int {
        val secondary = (a % columns).compareTo(b % columns)
        if (secondary != 0) return secondary
        return (a / columns).compareTo(b / columns)
    }
}

/**
 * The seam whose narrowest row still has the widest run: the layout's widest corridor. Ties go to
 * the seam that cuts fewer belts/pipes, then to the northernmost/leftmost one. A seam may step one
 * tile up or down per row.
 */
internal fun findSeam(grid: SeamGrid) = SeamSearch(grid).find()
