package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.area

/**
 * One layout failure and the place it blames, so the text rendering can print the reason and the
 * web preview can mark the spot. The area is the smallest box that helps a reader find the problem:
 * a tile for a clash or a missing route, an entity's footprint for a bad inserter, and a whole row
 * when the row itself is what does not fit. An issue must name an area: a box no tile can show
 * would vanish silently from the preview.
 */
data class LayoutIssue(val message: String, val area: Area) {
    init {
        require(!area.isEmpty()) { "a layout issue must name an area: $message" }
    }
}

/** One issue on the single tile at this coordinate. */
internal fun Coordinate.issue(message: String) = area().issue(message)

/** One issue over this whole area; an entity footprint, a row, or a computed route box. */
internal fun Area.issue(message: String) = LayoutIssue(message, this)
