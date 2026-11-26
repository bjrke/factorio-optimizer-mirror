package de.bjrke.factorio.optimizer.common.blueprint.layout.validation

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.areaTile
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val BELT = "express-transport-belt"
private const val UNDERGROUND_BELT = "express-underground-belt"

class LayoutValidationTransportTest : BaseGameCtxTest() {

    private fun belt(
        x: Int,
        y: Int,
        material: Material = ironOre,
        direction: Direction = EAST,
        load: LaneLoad = LaneLoad.full(material),
    ) = BeltEntity(areaTile(x, y), BELT, material, direction, load)

    private fun underground(
        x: Int,
        y: Int,
        direction: Direction,
        io: Io,
        load: LaneLoad = LaneLoad.full(ironOre),
    ) = UndergroundBeltEntity(areaTile(x, y), UNDERGROUND_BELT, load.material, direction, io, load)

    @Test
    fun twoEntitiesOnOneTileReportAnOverlap() {
        val entities: List<LayoutEntity> =
            listOf(belt(0, 0), belt(0, 0, material = ironPlate, direction = NORTH))

        val errors = validateLayout(entities)
        val overlap =
            assertNotNull(
                errors.firstOrNull { "overlap" in it.message },
                errors.joinToString { it.message },
            )

        assertEquals(areaTile(0, 0), overlap.area)
    }

    /**
     * An underground entrance dives into its run; the belt above the tunnel span is independent of
     * it. Treating the entrance as feeding the tile ahead made the reported fusion-reactor layout
     * invalid, which in turn switched compaction off completely.
     */
    @Test
    fun aBuriedEntranceDoesNotFeedTheBeltAboveItsRun() {
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0),
                underground(1, 0, EAST, Io.INPUT),
                belt(2, 0, ironPlate, NORTH),
                underground(4, 0, EAST, Io.OUTPUT),
                belt(5, 0),
            )

        assertEquals(emptyList(), validateLayout(entities))
    }

    @Test
    fun aMixedBeltRunsThroughAMixedTunnel() {
        val mixed = LaneLoad.mixed(left = ironOre, right = ironPlate)
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = mixed),
                underground(1, 0, EAST, Io.INPUT, load = mixed),
                underground(3, 0, EAST, Io.OUTPUT, load = mixed),
                belt(4, 0, load = mixed),
            )

        assertEquals(emptyList(), validateLayout(entities))
    }

    @Test
    fun aTunnelWhoseEndsSwapTheirLanesIsRejected() {
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = LaneLoad.mixed(left = ironOre, right = ironPlate)),
                underground(
                    1,
                    0,
                    EAST,
                    Io.INPUT,
                    load = LaneLoad.mixed(left = ironOre, right = ironPlate),
                ),
                underground(
                    3,
                    0,
                    EAST,
                    Io.OUTPUT,
                    load = LaneLoad.mixed(left = ironPlate, right = ironOre),
                ),
                belt(4, 0, load = LaneLoad.mixed(left = ironPlate, right = ironOre)),
            )

        val errors = validateLayout(entities)
        assertTrue(errors.any { "unpaired" in it.message }, errors.joinToString { it.message })
    }

    @Test
    fun aMixedBeltTurnsOffAMixedTunnel() {
        val mixed = LaneLoad.mixed(left = ironOre, right = ironPlate)
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = mixed),
                underground(1, 0, EAST, Io.INPUT, load = mixed),
                underground(3, 0, EAST, Io.OUTPUT, load = mixed),
                belt(4, 0, direction = NORTH, load = mixed),
            )

        assertEquals(emptyList(), validateLayout(entities))
    }

    /** A mixed belt may turn into a lane head, but it can never side-load a belt already fed. */
    @Test
    fun aMixedBeltDoesNotSideLoadAFedBelt() {
        val mixed = LaneLoad.mixed(left = ironOre, right = ironPlate)
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 1, load = mixed),
                belt(1, 1, load = mixed),
                belt(1, 0, direction = Direction.SOUTH, load = mixed),
            )

        val errors = validateLayout(entities)
        assertTrue(
            errors.any { "foreign side-load" in it.message },
            errors.joinToString { it.message },
        )
    }

    @Test
    fun aMixedBeltTurnWithSwappedLanesIsAForeignSideLoad() {
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = LaneLoad.mixed(left = ironOre, right = ironPlate)),
                belt(
                    1,
                    0,
                    material = ironPlate,
                    direction = NORTH,
                    load = LaneLoad.mixed(left = ironPlate, right = ironOre),
                ),
            )

        val errors = validateLayout(entities)
        assertTrue(
            errors.any { "foreign side-load" in it.message },
            errors.joinToString { it.message },
        )
    }

    @Test
    fun aStraightMixedBeltDoesNotSwapItsLanePositions() {
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = LaneLoad.mixed(left = ironOre, right = ironPlate)),
                belt(
                    1,
                    0,
                    material = ironPlate,
                    load = LaneLoad.mixed(left = ironPlate, right = ironOre),
                ),
            )

        val errors = validateLayout(entities)
        assertTrue(errors.any { "runs into" in it.message }, errors.joinToString { it.message })
    }

    @Test
    fun aHalfBeltDoesNotFeedAMixedTunnel() {
        val mixed = LaneLoad.mixed(left = ironOre, right = ironPlate)
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = LaneLoad.half(ironOre, left = true)),
                underground(1, 0, EAST, Io.INPUT, load = mixed),
                underground(3, 0, EAST, Io.OUTPUT, load = mixed),
                belt(4, 0, load = mixed),
            )

        val errors = validateLayout(entities)
        assertTrue(
            errors.any { "is not fed straight" in it.message },
            errors.joinToString { it.message },
        )
    }

    /**
     * A full single-material belt reserves its second lane instead of claiming that lane is fed, so
     * a half product belt may feed a consumer path straight; the plan bounds that producer by one
     * lane's capacity.
     */
    @Test
    fun aHalfBeltFeedsAFullBeltStraight() {
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = LaneLoad.half(ironOre, left = true)),
                belt(1, 0, load = LaneLoad.full(ironOre)),
            )

        assertEquals(emptyList(), validateLayout(entities))
    }

    @Test
    fun aHalfBeltDoesNotFeedAMixedBeltStraight() {
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = LaneLoad.half(ironOre, left = true)),
                belt(1, 0, load = LaneLoad.mixed(left = ironOre, right = ironPlate)),
            )

        val errors = validateLayout(entities)
        assertTrue(errors.any { "runs into" in it.message }, errors.joinToString { it.message })
    }

    @Test
    fun aFullBeltDoesNotFeedAHalfTunnel() {
        val half = LaneLoad.half(ironOre, left = true)
        val entities: List<LayoutEntity> =
            listOf(
                belt(0, 0, load = LaneLoad.full(ironOre)),
                underground(1, 0, EAST, Io.INPUT, load = half),
                underground(3, 0, EAST, Io.OUTPUT, load = half),
                belt(4, 0, load = half),
            )

        val errors = validateLayout(entities)
        assertTrue(
            errors.any { "is not fed straight" in it.message },
            errors.joinToString { it.message },
        )
    }

    @Test
    fun aMixedSplitterChecksBothOfItsOutputs() {
        val mixed = LaneLoad.mixed(left = ironOre, right = ironPlate)
        val entities: List<LayoutEntity> =
            listOf(
                belt(-1, 0, load = mixed),
                belt(-1, 1, load = mixed),
                SplitterEntity(Area(0, 0, 1, 2), "splitter", ironOre, EAST, mixed),
                belt(1, 0, load = mixed),
                belt(1, 1, material = ironOre),
            )

        val errors = validateLayout(entities)
        assertTrue(
            errors.any { "mixed splitter" in it.message },
            errors.joinToString { it.message },
        )
    }
}
