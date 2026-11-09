package de.bjrke.factorio.optimizer.common.blueprint.placing.compaction

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeInserter
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeTunnel
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UndergroundStretcherTest {

    private fun board() =
        LayoutBoard(
            names = LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
            undergroundRange = 6,
            fluidPredicate = { false },
            temperatureOf = { 0 },
        )

    /** A short run followed by the plain lane it may swallow. */
    private fun LayoutBoard.tunnelWithLane() {
        placeTunnel(ironPlate, Coordinate(0, 0), Coordinate(2, 0), EAST)
        for (x in 3..5) placeBelt(Coordinate(x, 0), ironPlate, EAST)
        placeBelt(Coordinate(6, 0), ironPlate, EAST)
    }

    @Test
    fun aRunSwallowsAPlainLaneWhenNothingUsesIt() {
        val board = board()
        board.tunnelWithLane()

        UndergroundStretcher(board, emptySet()).stretchAll()

        val output = assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(5, 0)))
        assertEquals(Io.OUTPUT, output.io)
    }

    @Test
    fun aRunLeavesTheBeltUnderAnInserter() {
        val board = board()
        board.tunnelWithLane()
        // the inserter takes from (3, 0); the run must not swallow that belt
        board.placeInserter(Coordinate(3, 1), Direction.NORTH, ironPlate, long = false)

        UndergroundStretcher(board, emptySet()).stretchAll()

        val output = assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(2, 0)))
        assertAll(
            { assertIs<BeltEntity>(board.entityAt(Coordinate(3, 0))) },
            { assertEquals(Io.OUTPUT, output.io) },
        )
    }
}
