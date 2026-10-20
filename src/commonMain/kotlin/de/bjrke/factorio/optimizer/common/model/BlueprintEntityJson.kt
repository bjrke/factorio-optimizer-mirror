package de.bjrke.factorio.optimizer.common.model

import kotlinx.serialization.json.*

/** The stock an infinity chest keeps available, matching what the game writes into a blueprint. */
private const val INFINITY_STOCK = 100

internal fun BlueprintEntity.toJson(entityNumber: Int) = buildJsonObject {
    put("entity_number", entityNumber)
    put("name", name)
    put("position", positionJson())

    direction?.let { put("direction", it.blueprintNumber) }
    recipe?.let { put("recipe", it) }
    type?.let { put("type", it.typeName) }
    putSettings(this)
    if (neighbours.isNotEmpty()) put("neighbours", neighboursJson())
    controlBehaviorJson()?.let { put("control_behavior", it) }
}

private fun BlueprintEntity.positionJson() = buildJsonObject {
    put("x", jsonNumber(area.topLeft.x + area.width / 2.0))
    put("y", jsonNumber(area.topLeft.y + area.height / 2.0))
}

/** The 2.0 convention doubles the cardinal values to 0/4/8/12. */
private val Direction.blueprintNumber: Int
    get() =
        when (this) {
            Direction.NORTH -> 0
            Direction.EAST -> 4
            Direction.SOUTH -> 8
            Direction.WEST -> 12
        }

private val Io.typeName: String
    get() =
        when (this) {
            Io.INPUT -> "input"
            Io.OUTPUT -> "output"
        }

/** Writes the machine, chest and inserter settings in their blueprint field order. */
private fun BlueprintEntity.putSettings(json: JsonObjectBuilder) {
    infinity?.let { json.put("infinity_settings", it.chestJson()) }
    infinityPipe?.let { json.put("infinity_settings", it.pipeJson()) }
    requestFilter?.let { json.put("request_filters", it.requestFiltersJson()) }
    bar?.let { json.put("bar", it) }
    if (mirror) json.put("mirror", true)
}

private fun InfinitySettings.chestJson() = buildJsonObject {
    put("remove_unfiltered_items", removeUnfiltered)
    item?.let { item ->
        putJsonArray("filters") {
            addJsonObject {
                put("name", item.name)
                put("count", INFINITY_STOCK)
                put("mode", "at-least")
                put("index", 1)
            }
        }
    }
}

private fun InfinityPipeSettings.pipeJson() = buildJsonObject {
    put("name", fluid.name)
    put("percentage", percentage)
    put("temperature", temperature)
    put("mode", mode)
}

private fun RequestFilter.requestFiltersJson() = buildJsonObject {
    putJsonArray("sections") {
        addJsonObject {
            put("index", 1)
            putJsonArray("filters") {
                addJsonObject {
                    put("index", 1)
                    put("name", name)
                    put("quality", "normal")
                    put("comparator", "=")
                    put("count", count)
                }
            }
        }
    }
    if (trashNotRequested) put("trash_not_requested", true)
}

private fun BlueprintEntity.neighboursJson() = buildJsonArray {
    neighbours.forEach { add(JsonPrimitive(it)) }
}

/**
 * The one control behavior an entity writes. The game-specific fields overwrite each other in field
 * order, so the latest set field is the one that survives.
 */
private fun BlueprintEntity.controlBehaviorJson(): JsonObject? =
    circuitConditionEnabled?.let { enabled ->
        buildJsonObject { put("circuit_condition_enabled", enabled) }
    }
        ?: circuitCondition?.let { it.controlBehaviorJson() }
        ?: marker?.let { markerControlBehavior(it, markerFluid) }

private fun CircuitCondition.controlBehaviorJson() = buildJsonObject {
    put("circuit_enabled", true)
    putJsonObject("circuit_condition") {
        putJsonObject("first_signal") { put("name", name) }
        put("constant", constant)
        put("comparator", comparator)
    }
}

/**
 * The control behavior that makes a constant combinator display [material]. 2.0 models constant
 * combinators as logistic sections, each holding its filters.
 */
private fun markerControlBehavior(material: Material, fluid: Boolean) = buildJsonObject {
    put("is_on", true)
    putJsonObject("sections") {
        putJsonArray("sections") {
            addJsonObject {
                put("index", 1)
                putJsonArray("filters") {
                    addJsonObject {
                        put("index", 1)
                        // match what the game writes for a constant combinator;
                        // a filter without a concrete quality is not displayed
                        if (fluid) put("type", "fluid")
                        put("name", material.name)
                        put("quality", "normal")
                        put("comparator", "=")
                        put("count", 1)
                    }
                }
            }
        }
    }
}

/** Integral values are written as integers so e.g. `1.0` becomes the shorter `1`. */
private fun jsonNumber(value: Double): JsonPrimitive =
    if (value == value.toLong().toDouble()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
