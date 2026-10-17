package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutEntityTest {

    private val plate = Material("iron-plate")
    private val water = Material("water")

    @Test
    fun placedEntitiesMapToTheirBlueprintDto() =
        assertTableOf(
            MachineEntity(
                Coordinate(2, 3).area(3, 2),
                "assembling-machine-3",
                "iron-gear-wheel",
            ) to
                BlueprintEntity(
                    "assembling-machine-3",
                    Coordinate(2, 3).area(3, 2),
                    recipe = "iron-gear-wheel",
                ),
            InserterEntity(
                AreaTile(5, 1),
                "fast-inserter",
                plate,
                EAST,
                long = false,
            ) to BlueprintEntity("fast-inserter", AreaTile(5, 1), EAST),
            BeltEntity(AreaTile(7, 2), "transport-belt", plate, EAST) to
                BlueprintEntity("transport-belt", AreaTile(7, 2), EAST),
            SplitterEntity(AreaTile(9, 4), "fast-splitter", plate, EAST) to
                BlueprintEntity("fast-splitter", AreaTile(9, 4), EAST),
            UndergroundBeltEntity(
                AreaTile(1, 8),
                "transport-belt",
                plate,
                EAST,
                Io.INPUT,
            ) to
                BlueprintEntity(
                    "transport-belt",
                    AreaTile(1, 8),
                    EAST,
                    type = Io.INPUT,
                ),
            UndergroundBeltEntity(
                AreaTile(3, 8),
                "transport-belt",
                plate,
                SOUTH,
                OUTPUT,
            ) to
                BlueprintEntity(
                    "transport-belt",
                    AreaTile(3, 8),
                    SOUTH,
                    type = OUTPUT,
                ),
            LoaderEntity(
                Coordinate(6, 6).area(2, 1),
                "fast-loader",
                plate,
                OUTPUT,
                EAST,
            ) to
                BlueprintEntity(
                    "fast-loader",
                    Coordinate(6, 6).area(2, 1),
                    EAST,
                    type = OUTPUT,
                ),
            InfinityChestEntity(
                AreaTile(2, 12),
                "infinity-chest",
                InfinitySettings(item = plate),
            ) to
                BlueprintEntity(
                    "infinity-chest",
                    AreaTile(2, 12),
                    infinity = InfinitySettings(item = plate),
                ),
            MarkerEntity(AreaTile(4, 14), "constant-combinator", Signal(plate)) to
                BlueprintEntity("constant-combinator", AreaTile(4, 14), marker = plate),
            MarkerEntity(
                AreaTile(6, 14),
                "constant-combinator",
                Signal(water, fluid = true),
            ) to
                BlueprintEntity(
                    "constant-combinator",
                    AreaTile(6, 14),
                    marker = water,
                    markerFluid = true,
                ),
            PipeEntity(AreaTile(8, 16), "pipe", water) to BlueprintEntity("pipe", AreaTile(8, 16)),
            PipeToGroundEntity(
                AreaTile(10, 16),
                "pipe-to-ground",
                water,
                EAST,
                Io.INPUT,
            ) to BlueprintEntity("pipe-to-ground", AreaTile(10, 16), EAST),
            PoleEntity(AreaTile(12, 18), "medium-electric-pole", listOf(2, 5)) to
                BlueprintEntity(
                    "medium-electric-pole",
                    AreaTile(12, 18),
                    neighbours = listOf(2, 5),
                ),
        ) { (entity, expected) ->
            assertEquals(expected, entity.toBlueprintEntity())
        }
}
