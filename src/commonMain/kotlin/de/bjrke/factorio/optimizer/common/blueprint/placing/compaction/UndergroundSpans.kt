package de.bjrke.factorio.optimizer.common.blueprint.placing.compaction

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.findUndergroundOutput
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io

/**
 * The tiles an underground run passes under when it runs *along* the seam. A seam that crossed such
 * a run could shift one end and leave the other, which would report an unpaired underground;
 * treating the span as a wall keeps both ends on the same side of the corridor. A run across the
 * seam stays removable: shortening a tunnel still pairs if both ends stay within reach.
 */
internal fun parallelUndergroundSpans(
    index: LayoutIndex,
    orientation: SeamOrientation,
): Set<Coordinate> = buildSet {
    for (input in index.entities.filterIsInstance<UndergroundEntity>()) {
        if (input.io != Io.INPUT || !runsAlong(input.direction, orientation)) continue
        val output = findUndergroundOutput(index, input) ?: continue
        val step = input.direction.step
        var tile = input.area.topLeft + step
        while (tile != output.area.topLeft) {
            add(tile)
            tile += step
        }
    }
}

private fun runsAlong(direction: Direction, orientation: SeamOrientation) =
    when (orientation) {
        SeamOrientation.HORIZONTAL -> direction == Direction.EAST || direction == Direction.WEST
        SeamOrientation.VERTICAL -> direction == Direction.NORTH || direction == Direction.SOUTH
    }
