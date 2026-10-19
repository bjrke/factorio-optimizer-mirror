package de.bjrke.factorio.optimizer.common.model

import kotlinx.serialization.json.*

/** The stock an infinity chest keeps available, matching what the game writes into a blueprint. */
private const val INFINITY_STOCK = 100

/**
 * Serializes the blueprint to the compact JSON Factorio embeds in a blueprint string. Optional
 * fields are omitted and integral coordinates are written without a decimal part, both to keep the
 * string short.
 */
fun Blueprint.toJson(): String = buildJsonObject {
    putJsonObject("blueprint") {
        put("item", "blueprint")
        put("label", label)
        icon?.let {
            putJsonArray("icons") {
                addJsonObject {
                    put("index", 1)
                    put("signal", buildJsonObject { put("name", it.name) })
                }
            }
        }
        putJsonArray("entities") {
            entities.forEachIndexed { index, entity ->
                add(entity.toJson(index + 1))
            }
        }
        if (wires.isNotEmpty()) {
            putJsonArray("wires") {
                wires.forEach { wire ->
                    addJsonArray {
                        add(wire.first)
                        add(wire.firstConnector)
                        add(wire.second)
                        add(wire.secondConnector)
                    }
                }
            }
        }
    }
}
    .toString()

private fun BlueprintEntity.toJson(entityNumber: Int) = buildJsonObject {
    put("entity_number", entityNumber)
    put("name", name)
    putJsonObject("position") {
        put("x", jsonNumber(area.topLeft.x + area.width / 2.0))
        put("y", jsonNumber(area.topLeft.y + area.height / 2.0))
    }

    direction?.let { direction ->
        // the 2.0 convention doubles the cardinal values to 0/4/8/12
        put(
            "direction",
            when (direction) {
                Direction.NORTH -> 0
                Direction.EAST -> 4
                Direction.SOUTH -> 8
                Direction.WEST -> 12
            },
        )
    }
    recipe?.let { put("recipe", it) }
    type?.let { type ->
        put(
            "type",
            when (type) {
                Io.INPUT -> "input"
                Io.OUTPUT -> "output"
            },
        )
    }
    infinity?.let { infinity ->
        putJsonObject("infinity_settings") {
            put("remove_unfiltered_items", infinity.removeUnfiltered)
            infinity.item?.let { item ->
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
    }
    infinityPipe?.let { pipe ->
        putJsonObject("infinity_settings") {
            put("name", pipe.fluid.name)
            put("percentage", pipe.percentage)
            put("temperature", pipe.temperature)
            put("mode", pipe.mode)
        }
    }
    requestFilter?.let { filter ->
        putJsonObject("request_filters") {
            putJsonArray("sections") {
                addJsonObject {
                    put("index", 1)
                    putJsonArray("filters") {
                        addJsonObject {
                            put("index", 1)
                            put("name", filter.name)
                            put("quality", "normal")
                            put("comparator", "=")
                            put("count", filter.count)
                        }
                    }
                }
            }
            if (filter.trashNotRequested) put("trash_not_requested", true)
        }
    }
    bar?.let { put("bar", it) }
    if (mirror) put("mirror", true)
    if (neighbours.isNotEmpty()) {
        putJsonArray("neighbours") { neighbours.forEach { add(JsonPrimitive(it)) } }
    }
    marker?.let { put("control_behavior", markerControlBehavior(it, markerFluid)) }
    circuitCondition?.let { condition ->
        putJsonObject("control_behavior") {
            put("circuit_enabled", true)
            putJsonObject("circuit_condition") {
                putJsonObject("first_signal") { put("name", condition.name) }
                put("constant", condition.constant)
                put("comparator", condition.comparator)
            }
        }
    }
    circuitConditionEnabled?.let { enabled ->
        putJsonObject("control_behavior") { put("circuit_condition_enabled", enabled) }
    }
}

/** Integral values are written as integers so e.g. `1.0` becomes the shorter `1`. */
private fun jsonNumber(value: Double): JsonPrimitive =
    if (value == value.toLong().toDouble()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

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
