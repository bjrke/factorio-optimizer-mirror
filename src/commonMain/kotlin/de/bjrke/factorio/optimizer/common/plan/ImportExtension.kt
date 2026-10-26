package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material

/**
 * One imported input: its belt connector (label, cheat chest or chest marker) and the tile its belt
 * starts on. The prefix between the two is trimmed after routing, so the import bus does not have
 * to cross the whole layout before the compactor sees it.
 */
internal class ImportFeed(
    val material: Material,
    val start: Coordinate,
    val row: Int,
    /** The lane tile the route feeds; the trim must never eat into the lane beyond it. */
    val feed: Coordinate,
)

/**
 * One imported fluid: the lane row and head its marker feeds, the direction its lane flows and the
 * inline source block it placed. The trimmer moves the source to the common import column and joins
 * it to the head with underground runs, so one column serves both the solid and the fluid markers.
 */
internal class FluidFeed(
    val material: Material,
    val row: Int,
    val head: Coordinate,
    /** Whether the lane flows east (a north feed); a south drain flows west. */
    val flowEast: Boolean,
    /** The eastmost tile of the source block as placed inline. */
    val source: Coordinate,
    /** Whether the lane drains its fluid, so its source block is an unfiltered sink. */
    val sink: Boolean,
)

/**
 * The import extension west of the factory: the marker rows already claimed, the imports to trim,
 * the tiles compaction must not move and the west bounds that keep poles and compaction out of the
 * extension. It is shared state, so the marker, trim, compaction and power stages agree on one
 * picture of the extension instead of keeping a copy each.
 */
internal class ImportExtension {

    /** Rows already claimed by an import or fluid marker, so two markers never share a row. */
    val markerRows = mutableSetOf<Int>()

    /** The imported inputs to trim, in placement order. */
    val importFeeds = mutableListOf<ImportFeed>()

    /** The fluid lanes whose marker joins the solid import column, in placement order. */
    val fluidFeeds = mutableListOf<FluidFeed>()

    /**
     * The import marker tiles. A vertical corridor may not move them, so the markers stay in one
     * straight column even after the layout is compacted.
     */
    val pinnedImportTiles = mutableSetOf<Coordinate>()

    /**
     * West of this column lies the import extension (markers and supply belts), not the factory.
     */
    var poleWestBound = Int.MIN_VALUE

    /**
     * West of this column the imported rows may not be compacted: the input lane starts at the
     * factory edge, and moving the factory west of it would pull the lane over the marker.
     */
    var compactionWestBound = Int.MIN_VALUE
}
