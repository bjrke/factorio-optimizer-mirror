package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.hasTunnelUnder
import de.bjrke.factorio.optimizer.common.blueprint.layout.rebuildTunnels
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportFeed
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST

/**
 * Moves every import marker to one common column and removes the straight belt prefix in front of
 * it. The routing already happened, so the router saw the old, wide west corridor and did not have
 * to route around the markers; only the unused prefix disappears. The common column is the furthest
 * east every feed has a straight eastward run, so the import bus stays straight.
 *
 * The fluid lane markers join that same column: [FluidFeedLinker] keeps their source blocks on the
 * shared import column and joins each one to its lane head with a pipe-to-ground run along the lane
 * row, so the west corridor stays walkable and the player only has to connect one column when
 * placing the blueprint. A fluid marker is a label, so it shares the solid marker's east edge and
 * reaches less far west than a chest marker.
 */
internal class ImportTrimmer(
    private val board: LayoutBoard,
    private val connector: BeltEndConnector,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
) {
    private val fluidLinker = FluidFeedLinker(board, beltEnd, imports)

    /** The tiles an import's solid connector occupies west of [IMPORT_FEED]. */
    private val ownMarker = (IMPORT_FEED - connector.width) until IMPORT_FEED

    fun trim() {
        val width = connector.width
        // a mixed feed that turns before its lane cannot be trimmed; it must not drag the common
        // column west for the straight imports, so only the straight runs decide the column
        val straight =
            imports.importFeeds.mapNotNull { feed ->
                lastStraightEast(feed)?.let { end -> feed to end }
            }
        val common =
            listOfNotNull(
                    straight.minOfOrNull { (_, end) -> end - width },
                    // the fluid marker and the solid marker share their east edge; its west edge
                    // is the same common column for the solid connector, so one trim aligns both
                    imports.fluidMarkerEast?.minus(width - 1),
                )
                .minOrNull()
        val trimmable = straight.map { it.first }
        // a connector may only replace its own marker and the straight feed belts; any other
        // entity or a tunnel span keeps the whole column where it is
        val safeCommon = common?.let { start ->
            (start downTo IMPORT_FEED - width + 1).firstOrNull { column ->
                trimmable.all { feed -> connectorClear(column, feed) }
            }
        }
        val trimmedColumn = safeCommon?.takeIf { trimSolid(trimmable, it, width) }
        val column = trimmedColumn ?: fallbackColumn()
        // a chest marker is powered, so its poles may sit in the import extension
        if (!beltEnd.isChest) {
            imports.poleWestBound = maxOf(imports.poleWestBound, column + width)
        }
        fluidLinker.link(column + width - 1)
        pinSolidConnectors(
            trimmedColumn,
            if (trimmedColumn == null) emptySet() else trimmable.toSet(),
            width,
        )
        // the old connector marks are gone; re-index the tunnels before stretching
        board.rebuildTunnels()
    }

    /**
     * Moves the [feeds] connectors to [common], or restores the board and returns false when the
     * moved column breaks the layout.
     */
    private fun trimSolid(feeds: List<ImportFeed>, common: Int, width: Int): Boolean {
        val saved = board.placed.toList()
        for (feed in feeds) {
            val (sx, sy) = feed.start
            for (x in (sx - width) until sx) {
                board.removeAt(Coordinate(x, sy))
            }
            for (x in sx until common + width) {
                board.removeAt(Coordinate(x, sy))
            }
            connector.placeSolidConnector(feed.material, common, sy, input = true)
            board.reserve(Coordinate(common + width, sy), feed.material)
        }
        if (validateLayout(board.placed).isEmpty()) return true
        board.replaceAll(saved)
        return false
    }

    /**
     * Whether [feed]'s connector can sit at [column]: every tile is free, a straight feed belt the
     * trim removes, or part of the feed's own marker, and no tunnel passes under it.
     */
    private fun connectorClear(column: Int, feed: ImportFeed): Boolean {
        return (column until column + connector.width).all { x ->
            val tile = Coordinate(x, feed.start.y)
            val entity = board.entityAt(tile)
            val removable =
                x in ownMarker ||
                    entity == null ||
                    (entity is BeltEntity &&
                        entity.material == feed.material &&
                        entity.direction == EAST)
            removable && !board.hasTunnelUnder(tile)
        }
    }

    /**
     * The column the import markers share when the solids keep theirs: the connector's own west
     * column, or the west edge of the fluid marker column when the plan has no solid imports.
     */
    private fun fallbackColumn(): Int {
        return if (imports.importFeeds.isEmpty())
            imports.fluidMarkerEast?.minus(connector.width - 1) ?: (IMPORT_FEED - connector.width)
        else IMPORT_FEED - connector.width
    }

    /**
     * Pins the whole connector, its feed tile and the lane tile that receives it together, so the
     * markers keep one column and a vertical corridor cannot slip between them and the lane.
     */
    private fun pinSolidConnectors(
        trimmedColumn: Int?,
        trimmed: Set<ImportFeed>,
        width: Int,
    ) {
        for (feed in imports.importFeeds) {
            val connectorStart =
                if (feed in trimmed && trimmedColumn != null) trimmedColumn
                else feed.start.x - width
            for (x in connectorStart..(connectorStart + width)) {
                imports.pinnedImportTiles += Coordinate(x, feed.start.y)
            }
            laneTile(feed)?.let { imports.pinnedImportTiles += it }
        }
    }

    /**
     * The first lane tile behind [feed]'s connector: the tile the feed belt hands its material to.
     * A corridor that cut between the connector and this tile would leave the marker feeding a
     * different lane tile than the one the route filled, so compaction pins it as well.
     */
    private fun laneTile(feed: ImportFeed): Coordinate? {
        val feedTile = Coordinate(feed.feed.x, feed.feed.y)
        val belt = board.entityAt(feedTile) as? BeltEntity ?: return null
        val target = board.entityAt(feedTile + belt.direction.step) as? BeltEntity ?: return null
        // a mixed merge tile contains both feeder materials; a lane head contains its one
        if (feed.material !in target.materials) return null
        return target.area.topLeft
    }

    /**
     * The last column of the straight eastward belt run from [feed]'s source to its lane. The lane
     * itself starts behind [ImportFeed.feed] and is never part of the trim. A U-turn feed enters
     * the merge head-on from its own row, so its last belt may turn into the merge; the run behind
     * it is still straight and the marker can sit on the shared column.
     */
    private fun lastStraightEast(feed: ImportFeed): Int? {
        if (feed.feed.y != feed.start.y || feed.feed.x < feed.start.x) return null
        var end = feed.feed.x
        var straight = true
        for (x in feed.start.x..feed.feed.x) {
            val belt = board.entityAt(Coordinate(x, feed.start.y)) as? BeltEntity
            when {
                belt == null || belt.material != feed.material -> straight = false
                belt.direction == EAST -> {}
                // only the tile the merge is entered from may leave the straight run
                x != feed.feed.x -> straight = false
                else -> end = x - 1
            }
        }
        return end.takeIf { straight }
    }
}
