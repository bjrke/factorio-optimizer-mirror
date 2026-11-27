package de.bjrke.factorio.optimizer.common.blueprint.placing.compaction

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.east

/** Upper bound on compaction corridors, so a pathological layout cannot loop forever. */
private const val MAX_COMPACTION_PASSES = 256

/**
 * Removes empty or spliceable corridors from the layout until none is left. Every corridor is
 * validated before it is kept; electricity is placed afterward, so poles follow the compacted
 * geometry. A corridor can expose a longer underground run, so the stretcher runs again after each
 * one; the pins and the west bound move with the import group compaction shifts. The pass
 * alternates the axis after every corridor it applies, so neither orientation starves; when the
 * preferred one has no corridor, the other is used.
 */
internal class CompactionPass(
    private val board: LayoutBoard,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
) {

    fun compact() {
        // a corridor can look valid before stretching and break a tunnel feed afterward; rule its
        // tiles out of its own orientation and try the next candidate instead of stopping the pass
        val rejectedVertical = mutableSetOf<Coordinate>()
        val rejectedHorizontal = mutableSetOf<Coordinate>()
        var preferVertical = true
        repeat(MAX_COMPACTION_PASSES) {
            // one grid for both orientations; nothing changes between the two calls
            val index = LayoutIndex(board.placed.toList())
            val vertical =
                nextCompaction(
                    index,
                    SeamOrientation.VERTICAL,
                    forbidden = rejectedVertical,
                    pinned = imports.pinnedImportTiles,
                    westBound = imports.compactionWestBound,
                )
            val horizontal =
                nextCompaction(
                    index,
                    SeamOrientation.HORIZONTAL,
                    forbidden = rejectedHorizontal,
                )
            val chosen = nextCorridor(vertical, horizontal, preferVertical) ?: return
            // a rejected corridor also hands the next pass to the other orientation
            preferVertical = chosen.orientation == SeamOrientation.HORIZONTAL
            val saved = board.placed.toList()
            board.replaceAll(chosen.entities)
            // a corridor can expose a longer underground run; grow it again before the next one
            UndergroundStretcher(board).stretchAll()
            if (validateLayout(board.placed).isNotEmpty()) {
                board.replaceAll(saved)
                val rejected =
                    if (chosen.orientation == SeamOrientation.VERTICAL) rejectedVertical
                    else rejectedHorizontal
                rejected += chosen.seamTiles
                return@repeat
            }
            // a vertical corridor can move the whole import group west; a horizontal one only
            // shifts rows, so it must not touch the x bound the poles use
            if (chosen.orientation == SeamOrientation.VERTICAL && chosen.pinnedShift != 0) {
                if (!beltEnd.isChest) imports.poleWestBound += chosen.pinnedShift
                val moved =
                    imports.pinnedImportTiles.mapTo(mutableSetOf()) { tile ->
                        tile.east(chosen.pinnedShift)
                    }
                imports.pinnedImportTiles.clear()
                imports.pinnedImportTiles += moved
            }
        }
    }
}

/**
 * The corridor to apply next: the preferred axis when it has one, otherwise the other. The pass
 * flips its preference after every applied corridor, so neither axis starves.
 */
internal fun nextCorridor(
    vertical: CompactionResult?,
    horizontal: CompactionResult?,
    preferVertical: Boolean,
): CompactionResult? = if (preferVertical) vertical ?: horizontal else horizontal ?: vertical
