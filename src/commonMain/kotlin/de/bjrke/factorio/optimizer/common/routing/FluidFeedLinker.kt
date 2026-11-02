package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.FLUID_SOURCE_WIDTH
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.layout.placeFluidSource
import de.bjrke.factorio.optimizer.common.layout.placeTunnels
import de.bjrke.factorio.optimizer.common.layout.removeFluidSource
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.west
import de.bjrke.factorio.optimizer.common.plan.FluidFeed
import de.bjrke.factorio.optimizer.common.plan.ImportExtension

/**
 * Moves the inline fluid lane markers into the common import column and joins each one to its lane
 * head with walkable pipe-to-ground runs.
 *
 * The tiles the link will claim are the lane row's own: the lane planner placed the lane before any
 * route ran, so a solid route that crosses the row is part of the layout and the link is laid over
 * it. A link is therefore not pre-checked: it is placed and the whole move is rolled back when
 * [validateLayout] rejects the result (an overlap with a placed tile or a foreign fluid touching
 * the pipe), which keeps the inline marker. A feed that still cannot be placed keeps its inline
 * marker.
 */
internal class FluidFeedLinker(
    private val board: LayoutBoard,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
) {

    /** Moves every fluid feed's source to [column] and links it to the lane head. */
    fun link(column: Int) {
        for (feed in imports.fluidFeeds) link(feed, column)
    }

    /** Moves one feed; a feed that no longer fits west of its head keeps the inline marker. */
    private fun link(feed: FluidFeed, column: Int) {
        val sourceEnd =
            if (beltEnd == BeltEnd.INFINITY_CHEST) column else column + FLUID_SOURCE_WIDTH - 1
        if (sourceEnd >= feed.head.x) return
        if (feed.source.x == sourceEnd) {
            pin(feed, column)
            return
        }
        val saved = board.placed.toList()
        board.removeFluidSource(beltEnd, feed.source)
        placeSource(feed, column)
        if (validateLayout(board.placed).isNotEmpty()) {
            board.replaceAll(saved)
            return
        }
        imports.compactionWestBound = maxOf(imports.compactionWestBound, sourceEnd + 1)
        pin(feed, column)
    }

    /** Places the fluid source block at [column] and the walkable link to the lane head. */
    private fun placeSource(feed: FluidFeed, column: Int) {
        val row = feed.row
        val width = if (beltEnd == BeltEnd.INFINITY_CHEST) 1 else FLUID_SOURCE_WIDTH
        val end = Coordinate(column + width - 1, row)
        board.placeFluidSource(beltEnd, end, feed.material, feed.io)
        board.placeTunnels(feed.material, end.east(), Coordinate(feed.head.x, row).west())
    }

    /** Pins the marker, the link and the head (plus the lane behind it) into one column group. */
    private fun pin(feed: FluidFeed, column: Int) {
        for (x in column..feed.head.x) {
            imports.pinnedImportTiles += Coordinate(x, feed.row)
        }
        val next = feed.head.east()
        if (board.entityAt(next) is Plumbing) imports.pinnedImportTiles += next
    }
}
