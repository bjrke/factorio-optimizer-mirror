package de.bjrke.factorio.optimizer.common.compaction

import de.bjrke.factorio.optimizer.common.blueprint.validateLayout
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.south
import de.bjrke.factorio.optimizer.common.routing.ImportExtension

/** Upper bound on compaction corridors, so a pathological layout cannot loop forever. */
private const val MAX_COMPACTION_PASSES = 256

/**
 * Removes empty or spliceable corridors from the layout until none is left. Every corridor is
 * validated before it is kept; electricity is placed afterward, so poles follow the compacted
 * geometry. A corridor can expose a longer underground run, so the stretcher runs again after each
 * one; the pins and the west bound move with the import group compaction shifts.
 */
internal class CompactionPass(
    private val board: LayoutBoard,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
    /** The machine-port tiles a fluid must reach; a grown run never swallows one. */
    private val fluidPorts: Set<Coordinate>,
) {

    fun compact() {
        repeat(MAX_COMPACTION_PASSES) {
            val vertical =
                nextCompaction(
                    board.placed.toList(),
                    SeamOrientation.VERTICAL,
                    pinned = imports.pinnedImportTiles,
                    westBound = imports.compactionWestBound,
                )
            val horizontal = nextCompaction(board.placed.toList(), SeamOrientation.HORIZONTAL)
            val chosen =
                when {
                    vertical == null -> horizontal
                    horizontal == null -> vertical
                    vertical.width >= horizontal.width -> vertical
                    else -> horizontal
                } ?: return
            val saved = board.placed.toList()
            board.replaceAll(chosen.entities)
            // a corridor can expose a longer underground run; grow it again before the next one
            UndergroundStretcher(board, fluidPorts).stretchAll()
            if (validateLayout(board.placed).isNotEmpty()) {
                board.replaceAll(saved)
                return
            }
            // the whole import group moved together, so its pins and the no-pole zone move with it
            if (chosen.pinnedShift != 0) {
                if (!beltEnd.isChest) imports.poleWestBound += chosen.pinnedShift
                val moved =
                    imports.pinnedImportTiles.mapTo(mutableSetOf()) { tile ->
                        if (chosen.orientation == SeamOrientation.VERTICAL) {
                            tile.east(chosen.pinnedShift)
                        } else {
                            tile.south(chosen.pinnedShift)
                        }
                    }
                imports.pinnedImportTiles.clear()
                imports.pinnedImportTiles += moved
            }
        }
    }
}
