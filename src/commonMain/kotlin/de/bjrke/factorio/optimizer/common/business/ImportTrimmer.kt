package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST

/**
 * Moves every import marker to one common column and removes the straight belt prefix in front of
 * it. The routing already happened, so the router saw the old, wide west corridor and did not have
 * to route around the markers; only the unused prefix disappears. The common column is the furthest
 * east every feed has a straight eastward run, so the import bus stays straight.
 */
internal class ImportTrimmer(
    private val board: LayoutBoard,
    private val connector: BeltEndConnector,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
) {

    fun trim() {
        if (imports.importFeeds.isEmpty()) return
        val width = connector.width
        // the connector takes `width` tiles, so its column is the straight run's end - width
        val common = imports.importFeeds.minOf { feed -> lastStraightEast(feed) - width }
        var trimmed = common > connector.left
        if (trimmed) {
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
            if (validateLayout(board.placed).isNotEmpty()) {
                board.replaceAll(saved)
                trimmed = false
            }
        }
        // a chest marker is powered, so its poles may sit in the import extension
        if (trimmed && !beltEnd.isChest) {
            imports.poleWestBound = maxOf(imports.poleWestBound, common + width)
        }
        // keep the whole connector and the first belt together, so the markers keep one column
        for (feed in imports.importFeeds) {
            val connectorStart = if (trimmed) common else feed.start.x - width
            for (x in connectorStart..(connectorStart + width)) {
                imports.pinnedImportTiles += Coordinate(x, feed.row)
            }
        }
        // the old connector marks are gone; re-index the tunnels before stretching
        board.rebuildTunnels()
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
