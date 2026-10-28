package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.beltend.BeltEndConnector
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.FLUID_SOURCE_WIDTH
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.rebuildTunnels
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.plan.ImportFeed

/**
 * Moves every import marker to one common column and removes the straight belt prefix in front of
 * it. The routing already happened, so the router saw the old, wide west corridor and did not have
 * to route around the markers; only the unused prefix disappears. The common column is the furthest
 * east every feed has a straight eastward run, so the import bus stays straight.
 *
 * The fluid lane markers join that same column: [FluidFeedLinker] moves their inline `oCo` block
 * west of the factory and joins it to the lane head with pipe-to-ground runs along the lane row, so
 * the west corridor stays walkable and the player only has to connect one column when placing the
 * blueprint.
 */
internal class ImportTrimmer(
    private val board: LayoutBoard,
    private val connector: BeltEndConnector,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
) {
    private val fluidLinker = FluidFeedLinker(board, beltEnd, imports)

    fun trim() {
        val width = connector.width
        // the connector takes `width` tiles, so its column is the straight run's end - width
        val common = imports.importFeeds.minOfOrNull { feed -> lastStraightEast(feed) - width }
        val trimmedColumn = common?.takeIf { it > connector.left && trimSolid(it, width) }
        val column = trimmedColumn ?: fallbackColumn()
        // a chest marker is powered, so its poles may sit in the import extension
        if (!beltEnd.isChest) {
            imports.poleWestBound = maxOf(imports.poleWestBound, column + width)
        }
        fluidLinker.link(column)
        pinSolidConnectors(trimmedColumn, width)
        // the old connector marks are gone; re-index the tunnels before stretching
        board.rebuildTunnels()
    }

    /**
     * Moves the solid connectors to [common], or restores the board and returns false when the
     * moved column breaks the layout.
     */
    private fun trimSolid(common: Int, width: Int): Boolean {
        val saved = board.placed.toList()
        for (feed in imports.importFeeds) {
            for (x in (feed.start.x - width) until feed.start.x) {
                board.removeAt(Coordinate(x, feed.row))
            }
            for (x in feed.start.x until common + width) {
                board.removeAt(Coordinate(x, feed.row))
            }
            connector.placeSolidConnector(feed.material, common, feed.row, input = true)
            board.reserve(Coordinate(common + width, feed.row), feed.material)
        }
        if (validateLayout(board.placed).isEmpty()) return true
        board.replaceAll(saved)
        return false
    }

    /**
     * The column the import markers share when the solids keep theirs: the connector's own west
     * column, or the westmost inline fluid marker when the plan has no solid imports at all.
     */
    private fun fallbackColumn(): Int =
        if (imports.importFeeds.isNotEmpty()) connector.left else fluidColumn() ?: connector.left

    /**
     * Pins the whole connector, its feed tile and the lane tile that receives it together, so the
     * markers keep one column and a vertical corridor cannot slip between them and the lane.
     */
    private fun pinSolidConnectors(trimmedColumn: Int?, width: Int) {
        for (feed in imports.importFeeds) {
            val connectorStart = trimmedColumn ?: (feed.start.x - width)
            for (x in connectorStart..(connectorStart + width)) {
                imports.pinnedImportTiles += Coordinate(x, feed.row)
            }
            laneTile(feed)?.let { imports.pinnedImportTiles += it }
        }
    }

    /** The westmost tile of the inline fluid markers, so every lane marker joins one column. */
    private fun fluidColumn(): Int? =
        imports.fluidFeeds.minOfOrNull { feed ->
            feed.source.x - (if (beltEnd == BeltEnd.INFINITY_CHEST) 0 else FLUID_SOURCE_WIDTH - 1)
        }

    /**
     * The first lane tile behind [feed]'s connector: the tile the feed belt hands its material to.
     * A corridor that cut between the connector and this tile would leave the marker feeding a
     * different lane tile than the one the route filled, so compaction pins it as well.
     */
    private fun laneTile(feed: ImportFeed): Coordinate? {
        val feedTile = Coordinate(feed.feed.x, feed.feed.y)
        val belt = board.entityAt(feedTile) as? BeltEntity ?: return null
        if (belt.material != feed.material || belt.direction != EAST) return null
        return feedTile.east()
    }

    /**
     * The last column of the straight eastward belt run from [feed]'s source to its lane. The lane
     * itself starts behind [ImportFeed.feed] and is never part of the trim.
     */
    private fun lastStraightEast(feed: ImportFeed): Int {
        if (feed.feed.y != feed.row || feed.feed.x < feed.start.x) return feed.start.x - 1
        for (x in feed.start.x..feed.feed.x) {
            val belt = board.entityAt(Coordinate(x, feed.row)) as? BeltEntity
            if (belt == null || belt.material != feed.material || belt.direction != EAST) {
                return feed.start.x - 1
            }
        }
        return feed.feed.x
    }
}
