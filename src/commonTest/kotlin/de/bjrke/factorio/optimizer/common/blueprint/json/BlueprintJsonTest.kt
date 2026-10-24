package de.bjrke.factorio.optimizer.common.blueprint.json

import de.bjrke.factorio.optimizer.common.blueprint.model.Blueprint
import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintWire
import de.bjrke.factorio.optimizer.common.layout.entity.CircuitCondition
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.layout.entity.InfinitySettings
import de.bjrke.factorio.optimizer.common.layout.entity.RequestFilter
import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.areaTile
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
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

class BlueprintJsonTest {

    /** The `blueprint` object a [blueprint] serializes to. */
    private fun blueprintJson(blueprint: Blueprint): JsonObject =
        Json.parseToJsonElement(blueprint.toJson()).jsonObject.getValue("blueprint").jsonObject

    /** The JSON object a single-entity blueprint writes for its only entity. */
    private fun entityJson(entity: BlueprintEntity): JsonObject =
        blueprintJson(Blueprint("test", entities = listOf(entity)))
            .getValue("entities")
            .jsonArray
            .first()
            .jsonObject

    @Test
    fun directionsSerializeToTheirBlueprintNumbers() =
        assertTableOf(
            Direction.NORTH to 0,
            Direction.EAST to 4,
            Direction.SOUTH to 8,
            Direction.WEST to 12,
        ) { (direction, expected) ->
            val entity = BlueprintEntity("transport-belt", areaTile(0, 0), direction = direction)
            val written = entityJson(entity).getValue("direction").jsonPrimitive.int
            assertEquals(expected, written)
        }

    @Test
    fun anEntityWithoutDirectionOmitsIt() {
        val entity = BlueprintEntity("pipe", areaTile(0, 0))
        assertNull(entityJson(entity)["direction"])
    }

    @Test
    fun ioSerializesToTheTypeString() =
        assertTableOf(
            Io.INPUT to "input",
            Io.OUTPUT to "output",
        ) { (io, expected) ->
            val entity = BlueprintEntity("transport-belt", areaTile(0, 0), type = io)
            val written = entityJson(entity).getValue("type").jsonPrimitive.content
            assertEquals(expected, written)
        }

    @Test
    fun anEntityWithoutIoOmitsTheType() {
        val entity = BlueprintEntity("pipe", areaTile(0, 0))
        assertNull(entityJson(entity)["type"])
    }

    @Test
    fun theEntityCentreIsWrittenAsAnIntegralPosition() {
        val entity =
            BlueprintEntity("assembling-machine-1", Area(Coordinate(1, 2), Dimension(2, 2)))
        val position = entityJson(entity).getValue("position").jsonObject

        assertAll(
            { assertEquals(JsonPrimitive(2), position["x"]) },
            { assertEquals(JsonPrimitive(3), position["y"]) },
        )
    }

    @Test
    fun anEntityRecipeIsWritten() {
        val entity =
            BlueprintEntity(
                "assembling-machine-1",
                areaTile(0, 0),
                recipe = "iron-gear-wheel",
            )

        assertEquals(JsonPrimitive("iron-gear-wheel"), entityJson(entity)["recipe"])
    }

    @Test
    fun infinityChestSettingsWriteTheirFiltersAndVoidFlag() =
        assertTableOf(
            InfinitySettings(Material("iron-plate"), removeUnfiltered = true) to
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
            val entity = BlueprintEntity("infinity-chest", areaTile(0, 0), infinity = settings)

            assertEquals(expected, entityJson(entity).getValue("infinity_settings"))
        }

    @Test
    fun anInfinityPipeWritesItsFluidSettings() {
        val entity =
            BlueprintEntity(
                "infinity-pipe",
                areaTile(0, 0),
                infinityPipe =
                    InfinityPipeSettings(
                        Material("water"),
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
    fun neighboursSerializeAsEntityNumbers() {
        val entity =
            BlueprintEntity("small-electric-pole", areaTile(0, 0), neighbours = listOf(2, 5))
        val expected = buildJsonArray {
            add(2)
            add(5)
        }

        assertEquals(expected, entityJson(entity).getValue("neighbours"))
    }

    @Test
    fun aMarkerWritesAConstantCombinatorFilter() =
        assertTableOf(
            Triple(Material("iron-plate"), false, null),
            Triple(Material("water"), true, JsonPrimitive("fluid")),
        ) { (material, fluid, expectedType) ->
            val entity =
                BlueprintEntity(
                    "constant-combinator",
                    areaTile(0, 0),
                    marker = material,
                    markerFluid = fluid,
                )
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
            BlueprintEntity(
                "buffer-chest",
                areaTile(0, 0),
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
            BlueprintEntity(
                "storage-chest",
                areaTile(0, 0),
                requestFilter = RequestFilter("iron-plate", 1),
            )

        assertNull(entityJson(entity).getValue("request_filters").jsonObject["trash_not_requested"])
    }

    @Test
    fun anInserterCircuitConditionWritesItsControlBehavior() {
        val entity =
            BlueprintEntity(
                "fast-inserter",
                areaTile(0, 0),
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
        val entity =
            BlueprintEntity("buffer-chest", areaTile(0, 0), circuitConditionEnabled = false)
        val expected = buildJsonObject { put("circuit_condition_enabled", false) }

        assertEquals(expected, entityJson(entity).getValue("control_behavior"))
    }

    @Test
    fun aChestBarAndAnInserterMirrorAreWrittenOnlyWhenSet() =
        assertAll(
            {
                val entity = BlueprintEntity("wooden-chest", areaTile(0, 0), bar = 4)
                assertEquals(JsonPrimitive(4), entityJson(entity)["bar"])
            },
            {
                val entity = BlueprintEntity("wooden-chest", areaTile(0, 0))
                assertNull(entityJson(entity)["bar"])
            },
            {
                val entity = BlueprintEntity("fast-inserter", areaTile(0, 0), mirror = true)
                assertEquals(JsonPrimitive(true), entityJson(entity)["mirror"])
            },
            {
                val entity = BlueprintEntity("fast-inserter", areaTile(0, 0))
                assertNull(entityJson(entity)["mirror"])
            },
        )

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
