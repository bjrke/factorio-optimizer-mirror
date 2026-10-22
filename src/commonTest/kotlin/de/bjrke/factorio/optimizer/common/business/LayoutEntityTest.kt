package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.blueprint.toBlueprintEntity
import de.bjrke.factorio.optimizer.common.layout.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.layout.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.layout.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.LoaderEntity
import de.bjrke.factorio.optimizer.common.layout.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.PoleEntity
import de.bjrke.factorio.optimizer.common.layout.Signal
import de.bjrke.factorio.optimizer.common.layout.SplitterEntity
import de.bjrke.factorio.optimizer.common.layout.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.model.InfinitySettings
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.areaTile
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutEntityTest {

    private val plate = Material("iron-plate")
    private val water = Material("water")

    @Test
    fun placedEntitiesMapToTheirBlueprintDto() =
        assertTableOf(
            BeltEntity(areaTile(7, 2), "transport-belt", plate, EAST) to
                BlueprintEntity("transport-belt", areaTile(7, 2), EAST),
            SplitterEntity(areaTile(9, 4), "fast-splitter", plate, EAST) to
                BlueprintEntity("fast-splitter", areaTile(9, 4), EAST),
            UndergroundBeltEntity(
                areaTile(1, 8),
                "transport-belt",
                plate,
                EAST,
                Io.INPUT,
            ) to
                BlueprintEntity(
                    "transport-belt",
                    areaTile(1, 8),
                    EAST,
                    type = Io.INPUT,
                ),
            UndergroundBeltEntity(
                areaTile(3, 8),
                "transport-belt",
                plate,
                SOUTH,
                OUTPUT,
            ) to
                BlueprintEntity(
                    "transport-belt",
                    areaTile(3, 8),
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
        ) { (entity, expected) ->
            assertEquals(expected, entity.toBlueprintEntity())
        }

    @Test
    fun machinesAndInsertersMapToTheirBlueprintDto() =
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
                areaTile(5, 1),
                "fast-inserter",
                plate,
                EAST,
                long = false,
            ) to BlueprintEntity("fast-inserter", areaTile(5, 1), EAST),
        ) { (entity, expected) ->
            assertEquals(expected, entity.toBlueprintEntity())
        }

    @Test
    fun stationaryEntitiesMapToTheirBlueprintDto() =
        assertTableOf(
            InfinityChestEntity(
                areaTile(2, 12),
                "infinity-chest",
                InfinitySettings(item = plate),
            ) to
                BlueprintEntity(
                    "infinity-chest",
                    areaTile(2, 12),
                    infinity = InfinitySettings(item = plate),
                ),
            MarkerEntity(areaTile(4, 14), "constant-combinator", Signal(plate)) to
                BlueprintEntity("constant-combinator", areaTile(4, 14), marker = plate),
            MarkerEntity(
                areaTile(6, 14),
                "constant-combinator",
                Signal(water, fluid = true),
            ) to
                BlueprintEntity(
                    "constant-combinator",
                    areaTile(6, 14),
                    marker = water,
                    markerFluid = true,
                ),
            PipeEntity(areaTile(8, 16), "pipe", water) to BlueprintEntity("pipe", areaTile(8, 16)),
            // a pipe-to-ground has no input/output type: the entrance faces back toward its source,
            // so an eastward run stores EAST but the blueprint gets WEST for the entrance
            PipeToGroundEntity(
                areaTile(10, 16),
                "pipe-to-ground",
                water,
                EAST,
                Io.INPUT,
            ) to BlueprintEntity("pipe-to-ground", areaTile(10, 16), WEST),
            PipeToGroundEntity(
                areaTile(11, 16),
                "pipe-to-ground",
                water,
                EAST,
                OUTPUT,
            ) to BlueprintEntity("pipe-to-ground", areaTile(11, 16), EAST),
            InfinityPipeEntity(
                areaTile(14, 16),
                "infinity-pipe",
                InfinityPipeSettings(water, temperature = 15),
            ) to
                BlueprintEntity(
                    "infinity-pipe",
                    areaTile(14, 16),
                    infinityPipe = InfinityPipeSettings(water, temperature = 15),
                ),
            PoleEntity(areaTile(12, 18), "medium-electric-pole", listOf(2, 5)) to
                BlueprintEntity(
                    "medium-electric-pole",
                    areaTile(12, 18),
                    neighbours = listOf(2, 5),
                ),
        ) { (entity, expected) ->
            assertEquals(expected, entity.toBlueprintEntity())
        }
}
