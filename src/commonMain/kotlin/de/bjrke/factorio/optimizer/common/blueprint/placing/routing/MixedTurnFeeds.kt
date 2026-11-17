package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.BeltMerge
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.Row
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowBelt
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * The turn-feed geometry of a row's mixed belts: a produced turn feeder on an outer lane feeds the
 * merge from its open side, while an import or an inner lane keeps the U-turn.
 *
 * The feed is the turn column on [BeltMerge.mergeRow], so it pours into the merge tile from the
 * side. The merge tile is then the straight belt that is fed from both sides, one lane per feeder;
 * its turn into the lane keeps the slots. Feeding the lane directly instead would side-load a full
 * belt into one lane and back it up, so the merge must stay two-sided.
 */
internal class MixedTurnFeeds(
    private val board: LayoutBoard,
    private val produced: (Material) -> Boolean,
) {

    /** Whether [belt]'s turn feeder is produced and feeds the merge instead of U-turning. */
    fun sideLoadsTurn(belt: RowBelt): Boolean =
        belt.merge?.let { merge ->
            merge.sideLoadsTurn(belt.side, produced = produced(merge.turnMaterial))
        } ?: false

    /**
     * The merge-side feed of every mixed belt that side-loads, or null for the belts that keep the
     * U-turn. Claimed tiles are passed on, so two lanes never pick the same feed.
     */
    fun sideLoadFeeds(inputBelts: List<Pair<Row, RowBelt>>): List<Coordinate?> {
        val claimed = mutableSetOf<Coordinate>()
        return inputBelts.map { (_, belt) ->
            val merge = belt.merge
            val feed =
                merge
                    ?.takeIf { sideLoadsTurn(belt) }
                    ?.let { Coordinate(it.turnColumn, it.mergeRow(belt.y)) }
            if (feed == null || feed in claimed || !board.isFree(feed)) {
                null
            } else {
                claimed += feed
                feed
            }
        }
    }

    /** The head-on tiles the U-turns may use; a tile a side-load claims is never unique. */
    fun uniqueHeadOn(
        inputBelts: List<Pair<Row, RowBelt>>,
        sideLoadFeeds: List<Coordinate?>,
    ): Set<Coordinate> {
        val merged = inputBelts.mapIndexedNotNull { index, (_, belt) ->
            val merge = belt.merge ?: return@mapIndexedNotNull null
            sideLoadFeeds[index]
                ?: Coordinate(
                    merge.turnColumn,
                    merge.turnRow(belt.y) + if (merge.above) -1 else 1,
                )
        }
        // a head-on tile only one U-turn asks for is free to use; a shared one is not
        return merged.groupingBy { it }.eachCount().filterValues { it == 1 }.keys
    }
}
