package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class BeltMergeTest {

    private data class Case(
        val above: Boolean,
        val side: LineSide,
        val produced: Boolean,
        val expected: Boolean,
    )

    /**
     * Only a lower (outer) belt row has a free row behind its turn. A near row's turn row lies
     * between the lane and the machines, so it keeps the U-turn; an import also keeps it.
     */
    @Test
    fun onlyAProducedOuterTurnFeedSideLoads() =
        assertTableOf(
            Case(above = true, side = LineSide.NORTH, produced = true, expected = true),
            Case(above = false, side = LineSide.SOUTH, produced = true, expected = true),
            Case(above = false, side = LineSide.NORTH, produced = true, expected = false),
            Case(above = true, side = LineSide.SOUTH, produced = true, expected = false),
            Case(above = true, side = LineSide.NORTH, produced = false, expected = false),
            Case(above = false, side = LineSide.SOUTH, produced = false, expected = false),
        ) { (above, side, produced, expected) ->
            assertEquals(expected, merge(above).sideLoadsTurn(side, produced))
        }

    private fun merge(above: Boolean) =
        BeltMerge(
            column = 7,
            above = above,
            westMaterial = Material("iron-plate"),
            eastMaterial = Material("plastic-bar"),
            direction = Direction.EAST,
        )
}
