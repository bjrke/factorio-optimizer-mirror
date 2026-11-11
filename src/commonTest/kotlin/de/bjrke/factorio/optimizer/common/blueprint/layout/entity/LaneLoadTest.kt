package de.bjrke.factorio.optimizer.common.blueprint.layout.entity

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaneLoadTest {

    @Test
    fun aFullBeltCarriesOneMaterialOnBothLanes() {
        val load = LaneLoad.full(ironPlate)

        assertAll(
            { assertEquals(setOf(ironPlate), load.materials) },
            { assertEquals(ironPlate, load.left) },
            { assertEquals(ironPlate, load.right) },
            { assertTrue(load.isFull) },
            { assertFalse(load.isMixed) },
        )
    }

    @Test
    fun aMixedBeltCarriesOneMaterialPerLane() {
        val load = LaneLoad.mixed(left = ironPlate, right = copperPlate)

        assertAll(
            { assertEquals(setOf(ironPlate, copperPlate), load.materials) },
            { assertEquals(ironPlate, load.left) },
            { assertEquals(copperPlate, load.right) },
            { assertTrue(load.isFull) },
            { assertTrue(load.isMixed) },
        )
    }

    @Test
    fun aHalfBeltLeavesOneLaneFree() {
        assertAll(
            {
                val right = LaneLoad.half(ironPlate, left = false)
                assertNull(right.left)
                assertEquals(ironPlate, right.right)
            },
            {
                val left = LaneLoad.half(ironPlate, left = true)
                assertEquals(ironPlate, left.left)
                assertNull(left.right)
            },
        )
    }

    @Test
    fun aSideLoadEntersTheLaneOnThatSide() =
        assertTable(
            listOf(
                // direction to the lane, compass side of the neighbor, expected lane material
                Triple(EAST, NORTH, ironPlate),
                Triple(EAST, SOUTH, copperPlate),
                Triple(WEST, SOUTH, ironPlate),
                Triple(WEST, NORTH, copperPlate),
                Triple(NORTH, WEST, ironPlate),
                Triple(NORTH, EAST, copperPlate),
                Triple(SOUTH, EAST, ironPlate),
                Triple(SOUTH, WEST, copperPlate),
            ),
        ) { (direction, side, expected) ->
            val load = LaneLoad.mixed(left = ironPlate, right = copperPlate)
            assertEquals(expected, load.laneAt(direction, side))
        }

    @Test
    fun aStraightNeighborDoesNotEnterALane() {
        val load = LaneLoad.mixed(left = ironPlate, right = copperPlate)

        for (direction in Direction.entries) {
            assertNull(load.laneAt(direction, direction))
            assertNull(load.laneAt(direction, direction.opposite()))
        }
    }
}
