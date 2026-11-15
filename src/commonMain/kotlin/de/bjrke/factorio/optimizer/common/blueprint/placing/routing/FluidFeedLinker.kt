package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.fluidSourceWidth
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeFluidSource
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeFluidSourceLink
import de.bjrke.factorio.optimizer.common.blueprint.layout.removeFluidSource
import de.bjrke.factorio.optimizer.common.blueprint.layout.removeFluidSourceLink
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.FluidFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.east

/**
 * Moves the shared fluid source blocks to the final import marker column and joins each one to its
 * lane head with walkable pipe-to-ground runs.
 *
 * The lane planner already placed every source block on the common east-aligned marker column
 * before routing. The solid marker column can move during layout, so this stage only has to move a
 * block when that column changed. The tile east of the block always carries the connection run,
 * even when the block sits right beside the lane head; the run may pass under the mixed merge's
 * U-turn.
 *
 * The tiles the link will claim are the lane row's own: the lane planner placed the lane before any
 * route ran, so a solid route that crosses the row is part of the layout and the link is laid over
 * it. A move is therefore not pre-checked: it is placed and the whole move is rolled back when
 * [validateLayout] rejects the result (an overlap with a placed tile or a foreign fluid touching
 * the pipe), which keeps the previous marker and link.
 */
internal class FluidFeedLinker(
    private val board: LayoutBoard,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
) {

    /** Moves every fluid feed's source block to [markerEast], the shared east-aligned column. */
    fun link(markerEast: Int) {
        for (feed in imports.fluidFeeds) link(feed, markerEast)
    }

    /** Moves one feed; a feed that no longer fits keeps its previous marker and link. */
    private fun link(feed: FluidFeed, markerEast: Int) {
        // three tiles west of the head leave the block's own end and the link's two ends touching
        if (markerEast > feed.head.x - 3) return
        if (feed.source.x == markerEast) {
            pin(feed, markerEast)
            return
        }
        val saved = board.placed.toList()
        removeSource(feed)
        placeSource(feed, markerEast)
        if (validateLayout(board.placed).isNotEmpty()) {
            board.replaceAll(saved)
            return
        }
        feed.source = Coordinate(markerEast, feed.row)
        imports.compactionWestBound = maxOf(imports.compactionWestBound, markerEast + 1)
        pin(feed, markerEast)
    }

    /** Removes the source block and the run that joins it to the lane head. */
    private fun removeSource(feed: FluidFeed) {
        board.removeFluidSource(beltEnd, feed.source)
        board.removeFluidSourceLink(feed.material, feed.source, feed.head)
    }

    /** Places the source block at [markerEast] and the walkable link to the lane head. */
    private fun placeSource(feed: FluidFeed, markerEast: Int) {
        val end = Coordinate(markerEast, feed.row)
        board.placeFluidSource(beltEnd, end, feed.material, feed.io)
        board.placeFluidSourceLink(feed.material, end, feed.head)
    }

    /** Pins the marker block, the link and the head (plus the lane behind it) at one column. */
    private fun pin(feed: FluidFeed, markerEast: Int) {
        val start = markerEast - fluidSourceWidth(beltEnd) + 1
        for (x in start..feed.head.x) {
            imports.pinnedImportTiles += Coordinate(x, feed.row)
        }
        val next = feed.head.east()
        if (board.entityAt(next) is Plumbing) imports.pinnedImportTiles += next
    }
}
