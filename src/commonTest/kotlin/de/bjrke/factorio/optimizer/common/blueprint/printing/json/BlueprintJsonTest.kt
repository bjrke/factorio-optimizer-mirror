package de.bjrke.factorio.optimizer.common.blueprint.printing.json

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.CircuitCondition
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinitySettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.RequestFilter
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.Blueprint
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintIcon
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintWire
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.areaTile
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class BlueprintJsonTest : BaseGameCtxTest() {

    /** The `blueprint` object a [blueprint] serializes to. */
    private fun blueprintJson(blueprint: Blueprint): JsonObject =
        Json.parseToJsonElement(blueprint.toJson()).jsonObject.getValue("blueprint").jsonObject

    /** The JSON object a single-entity blueprint writes for its only entity. */
    private fun entityJson(entity: LayoutEntity): JsonObject =
        blueprintJson(Blueprint("test", entities = listOf(entity)))
            .getValue("entities")
            .jsonArray
            .first()
            .jsonObject

    @Test
    fun anIconWritesItsSignalTypeOnlyForAFluid() =
        assertTableOf(
            Triple(petroleum.name, true, JsonPrimitive("fluid")),
            Triple(ironPlate.name, false, null),
        ) { (name, fluid, expectedType) ->
            val signal =
                blueprintJson(Blueprint("test", icons = listOf(BlueprintIcon(name, fluid))))
                    .getValue("icons")
                    .jsonArray
                    .single()
                    .jsonObject
                    .getValue("signal")
                    .jsonObject

            assertAll(
                { assertEquals(expectedType, signal["type"]) },
                { assertEquals(JsonPrimitive(name), signal["name"]) },
            )
        }

    @Test
    fun fourIconsGetConsecutiveIndices() {
        val icons =
            listOf(
                BlueprintIcon(ironPlate.name),
                BlueprintIcon("assembling-machine-3"),
                BlueprintIcon("express-transport-belt"),
                BlueprintIcon("fast-inserter"),
            )
        val written = blueprintJson(Blueprint("test", icons = icons)).getValue("icons").jsonArray

        assertAll(
            {
                assertEquals(
                    listOf(1, 2, 3, 4),
                    written.map { it.jsonObject.getValue("index").jsonPrimitive.int },
                )
            },
            {
                assertEquals(
                    icons.map { it.name },
                    written.map {
                        it.jsonObject
                            .getValue("signal")
                            .jsonObject
                            .getValue("name")
                            .jsonPrimitive
                            .content
                    },
                )
            },
        )
    }

    @Test
    fun aBlueprintWithoutIconsOmitsThem() {
        assertNull(blueprintJson(Blueprint("test"))["icons"])
    }

    @Test
    fun aBlueprintRejectsAFifthIcon() {
        assertFailsWith<IllegalArgumentException> {
            Blueprint("test", icons = List(5) { BlueprintIcon(ironPlate.name) })
        }
    }

    @Test
    fun directionsSerializeToTheirBlueprintNumbers() =
        assertTableOf(
            Direction.NORTH to null,
            Direction.EAST to 4,
            Direction.SOUTH to 8,
            Direction.WEST to 12,
        ) { (direction, expected) ->
            val entity = BeltEntity(areaTile(0, 0), "transport-belt", ironPlate, direction)
            val written = entityJson(entity)["direction"]?.jsonPrimitive?.int
            assertEquals(expected, written)
        }

    @Test
    fun ioSerializesToTheTypeString() =
        assertTableOf(
            Io.INPUT to "input",
            Io.OUTPUT to "output",
        ) { (io, expected) ->
            val entity =
                UndergroundBeltEntity(
                    areaTile(0, 0),
                    "transport-belt",
                    ironPlate,
                    Direction.NORTH,
                    io,
                )
            val written = entityJson(entity).getValue("type").jsonPrimitive.content
            assertEquals(expected, written)
        }

    @Test
    fun anEntityWithoutIoOmitsTheType() {
        val entity = PipeEntity(areaTile(0, 0), "pipe", water)
        assertNull(entityJson(entity)["type"])
    }

    @Test
    fun theEntityCentreIsWrittenAsAnIntegralPosition() {
        val entity =
            MachineEntity(
                Factory.ASSEMBLING_MACHINE_1,
                Area(1, 2, 2, 2),
                "iron-gear-wheel",
            )
        val position = entityJson(entity).getValue("position").jsonObject

        assertAll(
            { assertEquals(JsonPrimitive(2), position["x"]) },
            { assertEquals(JsonPrimitive(3), position["y"]) },
        )
    }

    @Test
    fun anEntityRecipeIsWritten() {
        val entity = MachineEntity(Factory.ASSEMBLING_MACHINE_1, areaTile(0, 0), "iron-gear-wheel")

        assertEquals(JsonPrimitive("iron-gear-wheel"), entityJson(entity)["recipe"])
    }

    @Test
    fun infinityChestSettingsWriteTheirFiltersAndVoidFlag() =
        assertTableOf(
            InfinitySettings(ironPlate, removeUnfiltered = true) to
                buildJsonObject {
                    put("remove_unfiltered_items", true)
                    putJsonArray("filters") {
                        addJsonObject {
                            put("name", "iron-plate")
                            put("count", 100)
                            put("mode", "at-least")
                            put("index", 1)
                        }
                    }
                },
            InfinitySettings(removeUnfiltered = true) to
                buildJsonObject { put("remove_unfiltered_items", true) },
        ) { (settings, expected) ->
            val entity = InfinityChestEntity(areaTile(0, 0), "infinity-chest", settings)

            assertEquals(expected, entityJson(entity).getValue("infinity_settings"))
        }

    @Test
    fun anInfinityPipeWritesItsFluidSettings() {
        val entity =
            InfinityPipeEntity(
                areaTile(0, 0),
                "infinity-pipe",
                water,
                InfinityPipeSettings(
                    water,
                    temperature = 25,
                    percentage = 1,
                    mode = "at-least",
                ),
            )
        val expected = buildJsonObject {
            put("name", "water")
            put("percentage", 1)
            put("temperature", 25)
            put("mode", "at-least")
        }

        assertEquals(expected, entityJson(entity).getValue("infinity_settings"))
    }

    @Test
    fun neighborsSerializeAsEntityNumbers() {
        val entity = PoleEntity(areaTile(0, 0), "small-electric-pole", listOf(2, 5))
        val expected = buildJsonArray {
            add(2)
            add(5)
        }

        assertEquals(expected, entityJson(entity).getValue("neighbours"))
    }

    @Test
    fun aMarkerWritesAConstantCombinatorFilter() =
        assertTableOf(
            Triple(ironPlate, false, null),
            Triple(water, true, JsonPrimitive("fluid")),
        ) { (material, fluid, expectedType) ->
            val entity =
                MarkerEntity(areaTile(0, 0), "constant-combinator", Signal(material, fluid))
            val behavior = entityJson(entity).getValue("control_behavior").jsonObject
            val filter =
                behavior
                    .getValue("sections")
                    .jsonObject
                    .getValue("sections")
                    .jsonArray
                    .first()
                    .jsonObject
                    .getValue("filters")
                    .jsonArray
                    .first()
                    .jsonObject

            assertAll(
                { assertEquals(JsonPrimitive(true), behavior["is_on"]) },
                { assertEquals(expectedType, filter["type"]) },
                { assertEquals(JsonPrimitive("normal"), filter["quality"]) },
                { assertEquals(JsonPrimitive(1), filter["count"]) },
                { assertEquals(JsonPrimitive(material.name), filter["name"]) },
            )
        }

    @Test
    fun aChestRequestFilterWritesTheGameSections() {
        val entity =
            ChestEntity(
                areaTile(0, 0),
                "buffer-chest",
                requestFilter = RequestFilter("iron-plate", 100, trashNotRequested = true),
            )
        val expected = buildJsonObject {
            putJsonArray("sections") {
                addJsonObject {
                    put("index", 1)
                    putJsonArray("filters") {
                        addJsonObject {
                            put("index", 1)
                            put("name", "iron-plate")
                            put("quality", "normal")
                            put("comparator", "=")
                            put("count", 100)
                        }
                    }
                }
            }
            put("trash_not_requested", true)
        }

        assertEquals(expected, entityJson(entity).getValue("request_filters"))
    }

    @Test
    fun aStorageChestRequestFilterOmitsTrashNotRequested() {
        val entity =
            ChestEntity(
                areaTile(0, 0),
                "storage-chest",
                requestFilter = RequestFilter("iron-plate", 1),
            )

        assertNull(entityJson(entity).getValue("request_filters").jsonObject["trash_not_requested"])
    }

    @Test
    fun anInserterCircuitConditionWritesItsControlBehavior() {
        val entity =
            InserterEntity(
                areaTile(0, 0),
                "fast-inserter",
                ironPlate,
                Direction.NORTH,
                long = false,
                circuitCondition = CircuitCondition("advanced-circuit", "<", 200),
            )
        val expected = buildJsonObject {
            put("circuit_enabled", true)
            putJsonObject("circuit_condition") {
                putJsonObject("first_signal") { put("name", "advanced-circuit") }
                put("constant", 200)
                put("comparator", "<")
            }
        }

        assertEquals(expected, entityJson(entity).getValue("control_behavior"))
    }

    @Test
    fun aChestCircuitConditionEnabledWritesItsControlBehavior() {
        val entity = ChestEntity(areaTile(0, 0), "buffer-chest", circuitConditionEnabled = false)
        val expected = buildJsonObject { put("circuit_condition_enabled", false) }

        assertEquals(expected, entityJson(entity).getValue("control_behavior"))
    }

    @Test
    fun aChestBarAndAnInserterMirrorAreWrittenOnlyWhenSet() =
        assertAll(
            {
                val entity = ChestEntity(areaTile(0, 0), "wooden-chest", bar = 4)
                assertEquals(JsonPrimitive(4), entityJson(entity)["bar"])
            },
            {
                val entity = ChestEntity(areaTile(0, 0), "wooden-chest")
                assertNull(entityJson(entity)["bar"])
            },
            {
                val entity =
                    InserterEntity(
                        areaTile(0, 0),
                        "fast-inserter",
                        ironPlate,
                        Direction.NORTH,
                        long = false,
                        mirror = true,
                    )
                assertEquals(JsonPrimitive(true), entityJson(entity)["mirror"])
            },
            {
                val entity =
                    InserterEntity(
                        areaTile(0, 0),
                        "fast-inserter",
                        ironPlate,
                        Direction.NORTH,
                        long = false,
                    )
                assertNull(entityJson(entity)["mirror"])
            },
        )

    @Test
    fun aPipeToGroundWritesTheSideItsSurfacePipeAttachesOn() =
        assertTableOf(
            PipeToGroundEntity(
                areaTile(0, 0),
                "pipe-to-ground",
                water,
                Direction.EAST,
                Io.INPUT,
            ) to 12,
            PipeToGroundEntity(
                areaTile(0, 0),
                "pipe-to-ground",
                water,
                Direction.EAST,
                Io.OUTPUT,
            ) to 4,
        ) { (entity, direction) ->
            assertAll(
                { assertEquals(JsonPrimitive(direction), entityJson(entity)["direction"]) },
                { assertNull(entityJson(entity)["type"]) },
            )
        }

    @Test
    fun anUndergroundBeltAndALoaderWriteTheirType() =
        assertTableOf(
            UndergroundBeltEntity(
                areaTile(0, 0),
                "transport-belt",
                ironPlate,
                Direction.NORTH,
                Io.INPUT,
            ) to "input",
            LoaderEntity(areaTile(0, 0), "fast-loader", ironPlate, Io.OUTPUT, Direction.EAST) to
                "output",
        ) { (entity, type) ->
            assertEquals(JsonPrimitive(type), entityJson(entity)["type"])
        }

    @Test
    fun anInfinityPipeSinkOmitsItsSettings() {
        val entity = InfinityPipeEntity(areaTile(0, 0), "infinity-pipe", water)

        assertNull(entityJson(entity)["infinity_settings"])
    }

    @Test
    fun circuitWiresSerializeAsFlatQuadruples() {
        val blueprint =
            Blueprint(
                "test",
                wires = listOf(BlueprintWire(2, 1, 3, 1), BlueprintWire(3, 1, 4, 1)),
            )
        val expected = buildJsonArray {
            addJsonArray {
                add(2)
                add(1)
                add(3)
                add(1)
            }
            addJsonArray {
                add(3)
                add(1)
                add(4)
                add(1)
            }
        }

        assertEquals(expected, blueprintJson(blueprint).getValue("wires"))
    }

    @Test
    fun aBlueprintWithoutWiresOmitsThem() {
        assertNull(blueprintJson(Blueprint("test"))["wires"])
    }
}
