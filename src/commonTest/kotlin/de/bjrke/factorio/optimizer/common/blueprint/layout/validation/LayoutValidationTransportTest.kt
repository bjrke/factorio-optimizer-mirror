package de.bjrke.factorio.optimizer.common.blueprint.layout.validation

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.areaTile
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BELT = "express-transport-belt"
private const val UNDERGROUND_BELT = "express-underground-belt"

private fun belt(x: Int, y: Int, material: Material = ironOre, direction: Direction = EAST) =
    BeltEntity(areaTile(x, y), BELT, material, direction)

private fun underground(
    x: Int,
    y: Int,
    direction: Direction,
    io: Io,
    material: Material = ironOre,
) = UndergroundBeltEntity(areaTile(x, y), UNDERGROUND_BELT, material, direction, io)

class LayoutValidationTransportTest {

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
}
