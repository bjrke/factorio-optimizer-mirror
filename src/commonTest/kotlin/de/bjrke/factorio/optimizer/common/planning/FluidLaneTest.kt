package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.LineSide
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The board-free shape of one row's fluid lane: a plain junction over every port, a pipe-to-ground
 * run across every gap of two tiles or more, and a vertical shaft per port. The run is chosen so
 * even the two-tile gap between touching machines is underground, which is what lets the player
 * walk across the line.
 */
class FluidLaneTest {

    private val laneRow = 10

    private fun lane(vararg xs: Int, side: LineSide = LineSide.NORTH) =
        FluidLane.of(laneRow, xs.map { Coordinate(it, 0) }, side)

    @Test
    fun touchingMachinesGetAJunctionPerPortAndAPairAcrossEveryGap() {
        val lane = lane(40, 43, 46)

        assertAll(
            { assertEquals(listOf(40, 43, 46), lane.surface.map { it.x }) },
            { assertEquals(listOf(41 to 42, 44 to 45), lane.runs.map { (a, b) -> a.x to b.x }) },
            { assertEquals(40, lane.head.x) },
            { assertEquals(3, lane.shafts.size) },
        )
    }

    @Test
    fun aSouthLaneRunsTheOtherWay() =
        assertTableOf(
            LineSide.NORTH to listOf(41 to 42, 44 to 45),
            LineSide.SOUTH to listOf(42 to 41, 45 to 44),
        ) { (side, expected) ->
            assertEquals(expected, lane(40, 43, 46, side = side).runs.map { (a, b) -> a.x to b.x })
        }

    @Test
    fun aWiderGapBuriesTheTilesBetweenItsEnds() {
        val lane = lane(40, 45)

        assertAll(
            { assertEquals(listOf(40, 45), lane.surface.map { it.x }) },
            // the ends sit one tile inside the junctions; 41..43 is passed under
            { assertEquals(listOf(41 to 44), lane.runs.map { (a, b) -> a.x to b.x }) },
        )
    }

    @Test
    fun aOneTileGapStaysAPlainPipe() {
        val lane = lane(40, 42)

        assertAll(
            { assertEquals(listOf(40, 41, 42), lane.surface.map { it.x }) },
            { assertEquals(emptyList(), lane.runs) },
        )
    }

    @Test
    fun everyShaftStartsBesideTheLaneAndLandsOnItsPort() =
        assertTableOf(LineSide.NORTH, LineSide.SOUTH) { side ->
            val lane = lane(40, 43, side = side)
            val shafts = lane.shafts
            assertAll(
                { assertEquals(2, shafts.size) },
                { assertEquals(listOf(40, 43), shafts.map { (a, _) -> a.x }) },
                { assertEquals(shafts.map { (a, _) -> a.x }, shafts.map { (_, b) -> b.x }) },
                {
                    assertTrue(
                        shafts.all { (a, b) ->
                            minOf(abs(a.y - laneRow), abs(b.y - laneRow)) == 1
                        },
                    )
                },
                {
                    assertEquals(
                        listOf(40, 43),
                        shafts.map { (a, b) -> (if (side == LineSide.NORTH) b else a).x },
                    )
                },
            )
        }
}
