package de.bjrke.factorio.optimizer.common.blueprint.placing.compaction

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeMarker
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Dimension
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CompactionPassTest {

    private fun board() =
        LayoutBoard(
            names = LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
            undergroundRange = 6,
            fluidPredicate = { false },
            temperatureOf = { 0 },
        )

    /**
     * A corridor west of the pinned import group moves the group west. Its pins and the x bound the
     * poles use follow; a horizontal corridor must never touch that bound.
     */
    @Test
    fun aVerticalPinShiftMovesThePinsAndThePoleBound() {
        val board = board()
        board.place(
            MachineEntity(
                Factory.ASSEMBLING_MACHINE_3,
                Area(Coordinate(0, 0), Dimension(3, 3)),
                "assembling-machine-3",
            ),
        )
        board.placeMarker(Coordinate(4, 0), Signal(ironOre))
        val imports =
            ImportExtension().apply {
                pinnedImportTiles += Coordinate(4, 0)
                poleWestBound = 10
            }

        CompactionPass(board, BeltEnd.CONSTANT_COMBINATOR, imports).compact()

        assertAll(
            { assertIs<MarkerEntity>(board.entityAt(Coordinate(3, 0))) },
            { assertEquals(setOf(Coordinate(3, 0)), imports.pinnedImportTiles) },
            { assertEquals(9, imports.poleWestBound) },
            { assertEquals(emptyList(), validateLayout(board.placed)) },
        )
    }
}
