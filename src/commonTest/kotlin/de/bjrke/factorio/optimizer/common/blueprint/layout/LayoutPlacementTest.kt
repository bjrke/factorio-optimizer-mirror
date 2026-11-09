package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.heavyOil
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutPlacementTest {

    private fun newBoard() =
        LayoutBoard(
            names =
                LayoutNames(
                    belt = "transport-belt",
                    inserter = "inserter",
                    longInserter = "long-handed-inserter",
                ),
            undergroundRange = 4,
            fluidPredicate = { true },
            temperatureOf = { 0 },
        )

    /** A board whose materials are solids, so a link rides on belts with the tier's reach. */
    private fun solidBoard() =
        LayoutBoard(
            names =
                LayoutNames(
                    belt = "transport-belt",
                    inserter = "inserter",
                    longInserter = "long-handed-inserter",
                ),
            undergroundRange = 4,
            fluidPredicate = { false },
            temperatureOf = { 0 },
        )

    @Test
    fun aCheatFluidSourceIsAFilteredInfinityPipe() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.INFINITY_CHEST, Coordinate(4, 0), water)

        val pipe = board.entityAt(Coordinate(4, 0)) as InfinityPipeEntity
        assertEquals(InfinityPipeSettings(water, temperature = 0), pipe.settings)
    }

    @Test
    fun aCheatFluidSinkIsAnUnfilteredInfinityPipe() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.INFINITY_CHEST, Coordinate(4, 0), water, io = Io.OUTPUT)

        val pipe = board.entityAt(Coordinate(4, 0)) as InfinityPipeEntity
        assertNull(pipe.settings)
    }

    @Test
    fun aRealFluidSourceBuildsWestFromItsEnd() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.CONSTANT_COMBINATOR, Coordinate(4, 0), water)

        val input = board.entityAt(Coordinate(2, 0)) as PipeToGroundEntity
        val marker = board.entityAt(Coordinate(3, 0)) as MarkerEntity
        val output = board.entityAt(Coordinate(4, 0)) as PipeToGroundEntity
        assertAll(
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Direction.EAST, input.direction) },
            { assertEquals(Signal(water, fluid = true), marker.signal) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(Direction.EAST, output.direction) },
            { assertEquals(output, board.undergroundOutputOf(input)) },
        )
    }

    @Test
    fun removingAFluidSourceRemovesItsWholeBlock() {
        val board = newBoard()
        board.placeFluidSource(BeltEnd.CONSTANT_COMBINATOR, Coordinate(4, 0), water)

        board.removeFluidSource(BeltEnd.CONSTANT_COMBINATOR, Coordinate(4, 0))

        assertAll(
            { assertNull(board.entityAt(Coordinate(2, 0))) },
            { assertNull(board.entityAt(Coordinate(3, 0))) },
            { assertNull(board.entityAt(Coordinate(4, 0))) },
        )
    }

    @Test
    fun aPipeOnlyMergesWithAForeignEndOnItsMouthSide() {
        val board = newBoard()
        // a water shaft end whose mouth faces south into a machine
        board.placeEnd(water, Coordinate(4, 0), Direction.SOUTH, Io.OUTPUT)
        // a plain water pipe joins on every side
        board.placePipe(Coordinate(8, 0), water)

        assertAll(
            // beside the closed side the heavy oil may stand
            { assertTrue(board.canPlacePipes(Coordinate(3, 0), heavyOil)) },
            // on the mouth side it would join the water network
            { assertFalse(board.canPlacePipes(Coordinate(4, 1), heavyOil)) },
            // a plain pipe has no closed side
            { assertFalse(board.canPlacePipes(Coordinate(7, 0), heavyOil)) },
        )
    }

    @Test
    fun aLinkLongerThanThePipeReachSplitsIntoRuns() {
        val board = newBoard()

        val plan = board.tunnelPlan(water, Coordinate(0, 0), Coordinate(13, 0))

        assertAll(
            // the pipe-to-ground reach is 10, so the link needs a second run beyond it
            {
                assertEquals(
                    listOf(
                        Coordinate(0, 0) to Coordinate(6, 0),
                        Coordinate(7, 0) to Coordinate(13, 0),
                    ),
                    plan.runs,
                )
            },
            // no surface tiles
            { assertEquals(emptyList(), plan.surface) },
            // the tiles a run passes under are not claimed; only its ends are
            {
                assertEquals(
                    setOf(
                        Coordinate(0, 0),
                        Coordinate(6, 0),
                        Coordinate(7, 0),
                        Coordinate(13, 0),
                    ),
                    plan.tiles.toSet(),
                )
            },
        )
    }

    @Test
    fun aLinkLongerThanThePipeReachSplitsIntoRuns2() {
        val board = newBoard()

        val plan = board.tunnelPlan(water, Coordinate(0, 0), Coordinate(31, 0))

        assertAll(
            {
                assertEquals(
                    listOf(
                        Coordinate(0, 0) to Coordinate(7, 0),
                        Coordinate(8, 0) to Coordinate(15, 0),
                        Coordinate(16, 0) to Coordinate(23, 0),
                        Coordinate(24, 0) to Coordinate(31, 0),
                    ),
                    plan.runs,
                )
            },
            { assertEquals(emptyList(), plan.surface) },
        )
    }

    @Test
    fun aLinkLongerThanThePipeReachSplitsIntoRuns3() {
        val board = newBoard()

        assertTableOf(
            2 to 1,
            10 to 1,
            11 to 2,
            20 to 2,
            21 to 3,
            30 to 3,
            31 to 4,
            32 to 4,
            33 to 4,
            34 to 4,
            35 to 4,
            36 to 4,
            37 to 4,
            38 to 4,
            39 to 4,
            40 to 4,
        ) { (dist, segments) ->
            val plan = board.tunnelPlan(water, Coordinate(0, 0), Coordinate(dist, 0))

            val segmentLengths = plan.runs.map { (f, t) -> (t.x - f.x) }.toSet()
            assertAll(
                { assertEquals(segments, plan.runs.size) },
                {
                    assertContains(
                        setOf(1, 2),
                        segmentLengths.size,
                    )
                },
                {
                    assertEquals(
                        setOf(
                            segmentLengths.min(),
                            segmentLengths.min() + segmentLengths.size - 1,
                        ),
                        segmentLengths,
                    )
                },
            )
        }
    }

    @Test
    fun aLinkLongerThanTheBeltReachSplitsIntoRunsOfThatTier() {
        val board = solidBoard()

        val plan = board.tunnelPlan(gear, Coordinate(0, 0), Coordinate(6, 0))

        assertAll(
            // the belt tier's reach is 4, not the pipe's 10
            {
                assertEquals(
                    listOf(
                        Coordinate(0, 0) to Coordinate(3, 0),
                        Coordinate(4, 0) to Coordinate(6, 0),
                    ),
                    plan.runs,
                )
            },
            { assertEquals(emptyList(), plan.surface) },
        )
    }

    @Test
    fun aSolidLinkIsLaidAsBeltsAndUndergroundBelts() {
        val board = solidBoard()

        board.placeTunnels(gear, Coordinate(0, 0), Coordinate(6, 0))

        val input1 = board.entityAt(Coordinate(0, 0)) as UndergroundBeltEntity
        val output1 = board.entityAt(Coordinate(3, 0)) as UndergroundBeltEntity

        val input2 = board.entityAt(Coordinate(4, 0)) as UndergroundBeltEntity
        val output2 = board.entityAt(Coordinate(6, 0)) as UndergroundBeltEntity

        assertAll(
            { assertEquals(Io.INPUT, input1.io) },
            { assertEquals(Io.OUTPUT, output1.io) },
            { assertEquals(output1, board.undergroundOutputOf(input1)) },
            { assertEquals(Direction.EAST, input1.direction) },
            { assertEquals(Direction.EAST, output1.direction) },
            { assertEquals(gear, input1.material) },
            { assertEquals(gear, output1.material) },
            { assertEquals(Io.INPUT, input2.io) },
            { assertEquals(Io.OUTPUT, output2.io) },
            { assertEquals(output2, board.undergroundOutputOf(input2)) },
            { assertEquals(Direction.EAST, input2.direction) },
            { assertEquals(Direction.EAST, output2.direction) },
            { assertEquals(gear, input2.material) },
            { assertEquals(gear, output2.material) },
        )
    }

    @Test
    fun anUndergroundOutputFeedsTheNextRunDirectly() {
        val board = solidBoard()
        // the first and last run still need a surface belt to feed and to empty onto
        board.placeBelt(Coordinate(-1, 0), gear, Direction.EAST)
        board.placeTunnels(gear, Coordinate(0, 0), Coordinate(6, 0))
        board.placeBelt(Coordinate(7, 0), gear, Direction.EAST)

        assertEquals(emptyList(), validateLayout(board.placed))
    }

    @Test
    fun aLinkOffTheRowOrColumnIsRejected() {
        val board = newBoard()

        assertFailsWith<IllegalArgumentException> {
            board.tunnelPlan(water, Coordinate(0, 0), Coordinate(3, 4))
        }
    }
}
