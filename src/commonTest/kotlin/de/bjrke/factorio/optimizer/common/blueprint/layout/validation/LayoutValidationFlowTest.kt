package de.bjrke.factorio.optimizer.common.blueprint.layout.validation

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area
import de.bjrke.factorio.optimizer.common.core.model.areaTile
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BELT = "express-transport-belt"
private const val SPLITTER = "express-splitter"

private fun belt(x: Int, y: Int, direction: Direction = EAST, material: Material = ironOre) =
    BeltEntity(areaTile(x, y), BELT, material, direction)

/** A splitter facing [direction]; its two tiles lie across that facing. */
private fun splitter(x: Int, y: Int, direction: Direction) =
    SplitterEntity(
        if (direction == EAST || direction == WEST) Coordinate(x, y).area(1, 2)
        else Coordinate(x, y).area(2, 1),
        SPLITTER,
        ironOre,
        direction,
    )

class LayoutValidationFlowTest {

    /**
     * The reported fusion-reactor layout had a circuit splitter sitting in front of a machine: the
     * trunk arrived along its facing, so its back — the only side a splitter takes items on — was
     * empty.
     */
    @Test
    fun aSplitterWithoutABeltOnItsBackIsReported() =
        assertTableOf(
            listOf(splitter(2, 0, EAST), belt(3, 0)) to
                listOf("splitter at Coordinate(x=2, y=0) has no input"),
            listOf(belt(2, 0, SOUTH), splitter(2, 1, EAST), belt(3, 1)) to
                listOf("splitter at Coordinate(x=2, y=1) has no input"),
        ) { (entities, expected) ->
            assertEquals(expected, validateLayout(entities))
        }

    @Test
    fun aSplitterFedStraightFromBehindIsAccepted() {
        val entities: List<LayoutEntity> =
            listOf(belt(1, 0), splitter(2, 0, EAST), belt(3, 0), belt(3, 1))

        assertEquals(emptyList(), validateLayout(entities))
    }

    /** A dead end is not a circle: the two ends of a straight line are not connected. */
    @Test
    fun aBeltLineThatEndsIsNotACircle() {
        val line: List<LayoutEntity> =
            listOf(belt(0, 0), belt(1, 0), belt(2, 0, SOUTH), belt(2, 1, SOUTH))

        assertEquals(emptyList(), validateLayout(line))
    }

    /**
     * A branch off a circle is not on it: the report names one tile per circle, and the belt the
     * splitter's second output feeds must not be blamed for the loop it merely receives from.
     */
    @Test
    fun aBeltCircleIsReportedOnceAtItsFirstTile() {
        val circle: List<LayoutEntity> =
            listOf(
                splitter(1, 1, SOUTH),
                belt(1, 0, SOUTH),
                belt(0, 0),
                belt(0, 1, NORTH),
                belt(0, 2, NORTH),
                belt(1, 2, WEST),
                belt(2, 2, EAST),
            )

        assertEquals(
            listOf("iron-ore belt loops back at Coordinate(x=0, y=0)"),
            validateLayout(circle),
        )
    }
}
