package de.bjrke.factorio.optimizer.common.blueprint.placing.compaction

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeMarker
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CompactionPassTest : BaseGameCtxTest() {

    private fun board() =
        LayoutBoard(
            names = LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
            undergroundRange = 6,
            temperatureOf = { 0 },
        )

    private fun machine(y: Int) =
        MachineEntity(Factory.ASSEMBLING_MACHINE_3, Area(0, y, 3, 3), "assembling-machine-3")

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
                Area(0, 0, 3, 3),
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

    /**
     * Every column is occupied, so vertical compaction has no corridor; the empty rows between the
     * machines are a horizontal one. The pass must fall through to the other orientation instead of
     * stopping at the vertical dead end.
     */
    @Test
    fun aVerticalDeadEndStillCompactsHorizontally() {
        val board = board()
        board.place(machine(0))
        board.place(machine(10))
        val entities = board.placed.toList()

        assertAll(
            { assertNull(nextCompaction(LayoutIndex(entities), SeamOrientation.VERTICAL)) },
            { assertNotNull(nextCompaction(LayoutIndex(entities), SeamOrientation.HORIZONTAL)) },
        )

        CompactionPass(board, BeltEnd.CONSTANT_COMBINATOR, ImportExtension()).compact()

        assertAll(
            { assertEquals(6, board.endY - board.minY) },
            { assertEquals(emptyList(), validateLayout(board.placed)) },
        )
    }

    /** The pass alternates the preferred axis and falls back to the other when it is a dead end. */
    @Test
    fun thePassAlternatesAxesAndFallsBackToTheOther() {
        val vertical = CompactionResult(emptyList(), 1, SeamOrientation.VERTICAL, 0, emptySet())
        val horizontal = CompactionResult(emptyList(), 4, SeamOrientation.HORIZONTAL, 0, emptySet())

        assertAll(
            { assertEquals(vertical, nextCorridor(vertical, horizontal, preferVertical = true)) },
            {
                assertEquals(horizontal, nextCorridor(vertical, horizontal, preferVertical = false))
            },
            { assertEquals(horizontal, nextCorridor(null, horizontal, preferVertical = true)) },
            { assertEquals(vertical, nextCorridor(vertical, null, preferVertical = false)) },
            { assertNull(nextCorridor(null, null, preferVertical = true)) },
        )
    }
}
