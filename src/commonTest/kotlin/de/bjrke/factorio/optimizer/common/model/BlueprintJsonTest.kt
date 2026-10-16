package de.bjrke.factorio.optimizer.common.model

import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.*

class BlueprintJsonTest {

    /** The JSON object a single-entity blueprint writes for its only entity. */
    private fun entityJson(entity: BlueprintEntity): JsonObject {
        val json = Blueprint("test", entities = listOf(entity)).toJson()
        return Json.parseToJsonElement(json)
            .jsonObject
            .getValue("blueprint")
            .jsonObject
            .getValue("entities")
            .jsonArray
            .first()
            .jsonObject
    }

    @Test
    fun directionsSerializeToTheirBlueprintNumbers() =
        assertTableOf(
            Direction.NORTH to 0,
            Direction.EAST to 4,
            Direction.SOUTH to 8,
            Direction.WEST to 12,
        ) { (direction, expected) ->
            val entity = BlueprintEntity("transport-belt", AreaTile(0, 0), direction = direction)
            val written = entityJson(entity).getValue("direction").jsonPrimitive.int
            assertEquals(expected, written)
        }

    @Test
    fun anEntityWithoutDirectionOmitsIt() {
        val entity = BlueprintEntity("pipe", AreaTile(0, 0))
        assertNull(entityJson(entity)["direction"])
    }

    @Test
    fun ioSerializesToTheTypeString() =
        assertTableOf(
            Io.INPUT to "input",
            Io.OUTPUT to "output",
        ) { (io, expected) ->
            val entity = BlueprintEntity("transport-belt", AreaTile(0, 0), type = io)
            val written = entityJson(entity).getValue("type").jsonPrimitive.content
            assertEquals(expected, written)
        }

    @Test
    fun anEntityWithoutIoOmitsTheType() {
        val entity = BlueprintEntity("pipe", AreaTile(0, 0))
        assertNull(entityJson(entity)["type"])
    }
}
