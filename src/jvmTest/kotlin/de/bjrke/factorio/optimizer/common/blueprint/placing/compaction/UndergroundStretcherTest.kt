package de.bjrke.factorio.optimizer.common.blueprint.placing.compaction

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeInserter
import de.bjrke.factorio.optimizer.common.blueprint.layout.placePipe
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeTunnel
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UndergroundStretcherTest : BaseGameCtxTest() {

    private fun board() =
        LayoutBoard(
            names = LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
            undergroundRange = 6,
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

        UndergroundStretcher(board).stretchAll()

        val output = assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(5, 0)))
        assertEquals(Io.OUTPUT, output.io)
    }

    @Test
    fun aRunLeavesAHalfBeltLane() {
        val board = board()
        board.placeTunnel(ironPlate, Coordinate(0, 0), Coordinate(2, 0), EAST)
        // a product lane fills one lane only; a tunnel would lose that lane, so it must stay
        for (x in 3..6) {
            board.placeBelt(Coordinate(x, 0), LaneLoad.half(ironPlate, left = false), EAST)
        }

        UndergroundStretcher(board).stretchAll()

        val output = assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(2, 0)))
        assertAll(
            { assertEquals(Io.OUTPUT, output.io) },
            { assertIs<BeltEntity>(board.entityAt(Coordinate(3, 0))) },
        )
    }

    @Test
    fun aRunLeavesTheBeltUnderAnInserter() {
        val board = board()
        board.tunnelWithLane()
        // the inserter takes from (3, 0); the run must not swallow that belt
        board.placeInserter(Coordinate(3, 1), Direction.NORTH, ironPlate, long = false)

        UndergroundStretcher(board).stretchAll()

        val output = assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(2, 0)))
        assertAll(
            { assertIs<BeltEntity>(board.entityAt(Coordinate(3, 0))) },
            { assertEquals(Io.OUTPUT, output.io) },
        )
    }

    /**
     * A machine port is a plain pipe on the machine edge, and the run grows over the straight lane
     * continuation on either side of it. The port is recognized on the settled board, so a port
     * that a compaction shift moved is still protected.
     */
    @Test
    fun aRunLeavesTheMachinePortPastItsOutput() {
        val board = board()
        board.place(
            MachineEntity(
                Factory.ASSEMBLING_MACHINE_3,
                Area(0, 3, 3, 3),
                "assembling-machine-3",
            ),
        )
        board.placeTunnel(water, Coordinate(-1, -1), Coordinate(-1, 2), SOUTH)
        // the port pipe feeds the machine, and two more pipes continue the lane past it
        for (y in 3..5) board.placePipe(Coordinate(-1, y), water)

        UndergroundStretcher(board).stretchAll()

        val output = assertIs<PipeToGroundEntity>(board.entityAt(Coordinate(-1, 2)))
        assertAll(
            { assertIs<PipeEntity>(board.entityAt(Coordinate(-1, 3))) },
            { assertEquals(Io.OUTPUT, output.io) },
        )
    }

    /**
     * The entrance a grown run moves back onto must still be fed straight. The corner belt it
     * swallows is followed by a foreign belt, not by the same material, so the run stops; the
     * reported rocket-silo layout moved a copper-cable entrance off its feeder this way.
     */
    @Test
    fun aRunDoesNotMoveItsEntranceOntoAForeignFeed() {
        val board = board()
        board.placeTunnel(ironPlate, Coordinate(4, 0), Coordinate(2, 0), WEST)
        board.placeBelt(Coordinate(5, 0), ironPlate, WEST)
        board.placeBelt(Coordinate(6, 0), copperPlate, WEST)

        UndergroundStretcher(board).stretchAll()

        assertAll(
            { assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(4, 0))) },
            { assertIs<BeltEntity>(board.entityAt(Coordinate(5, 0))) },
        )
    }

    /**
     * An exit may only grow onto a lane of its own material: a foreign belt would take the run's
     * material and merge it into another lane.
     */
    @Test
    fun aRunDoesNotGrowItsExitIntoAForeignLane() {
        val board = board()
        board.placeTunnel(ironPlate, Coordinate(0, 0), Coordinate(2, 0), EAST)
        board.placeBelt(Coordinate(3, 0), ironPlate, EAST)
        board.placeBelt(Coordinate(4, 0), copperPlate, EAST)

        UndergroundStretcher(board).stretchAll()

        assertAll(
            { assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(2, 0))) },
            { assertIs<BeltEntity>(board.entityAt(Coordinate(3, 0))) },
        )
    }

    /**
     * An exit may grow into a splitter: it takes items on its back and hands them along, so the
     * output pours straight into it. The reported logistic-science layout's iron-plate run left a
     * lone belt in front of a splitter, and that belt kept its row from compacting.
     */
    @Test
    fun aRunGrowsItsExitIntoASplitter() {
        val board = board()
        board.placeTunnel(ironPlate, Coordinate(0, 0), Coordinate(2, 0), EAST)
        board.placeBelt(Coordinate(3, 0), ironPlate, EAST)
        board.place(
            SplitterEntity(
                Area(4, 0, 1, 2),
                "fast-splitter",
                ironPlate,
                EAST,
            ),
        )

        UndergroundStretcher(board).stretchAll()

        val output = assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(3, 0)))
        assertAll(
            { assertEquals(Io.OUTPUT, output.io) },
            { assertIs<SplitterEntity>(board.entityAt(Coordinate(4, 0))) },
        )
    }

    /** A splitter of another material is not a lane continuation. */
    @Test
    fun aRunDoesNotGrowItsExitIntoAForeignSplitter() {
        val board = board()
        board.placeTunnel(ironPlate, Coordinate(0, 0), Coordinate(2, 0), EAST)
        board.placeBelt(Coordinate(3, 0), ironPlate, EAST)
        board.place(
            SplitterEntity(
                Area(4, 0, 1, 2),
                "fast-splitter",
                copperPlate,
                EAST,
            ),
        )

        UndergroundStretcher(board).stretchAll()

        val output = assertIs<UndergroundBeltEntity>(board.entityAt(Coordinate(2, 0)))
        assertAll(
            { assertEquals(Io.OUTPUT, output.io) },
            { assertIs<BeltEntity>(board.entityAt(Coordinate(3, 0))) },
        )
    }
}
