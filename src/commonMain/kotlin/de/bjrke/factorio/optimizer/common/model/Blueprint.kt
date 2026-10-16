package de.bjrke.factorio.optimizer.common.model

import kotlinx.serialization.json.*

/** The stock an infinity chest keeps available, matching what the game writes into a blueprint. */
private const val INFINITY_STOCK = 100

/**
 * One entity of a blueprint. [direction] is only set for entities that face a way; the 2.0
 * convention doubles the cardinal values to 0/4/8/12.
 *
 * [type] is `input`/`output` for an underground belt (or pipe-to-ground), [recipe] the recipe an
 * assembling machine runs and [marker] the material a constant-combinator marker carries.
 */
data class BlueprintEntity(
    val name: String,
    val area: Area,
    val direction: Int? = null,
    val recipe: String? = null,
    val type: String? = null,
    val marker: Material? = null,
    /**
     * Whether [marker] is a fluid; a constant combinator needs an explicit signal type for those.
     */
    val markerFluid: Boolean = false,
    /**
     * The `entity_number`s this entity is wired to (power poles connect this way; a blueprint does
     * not rebuild those wires on its own).
     */
    val neighbours: List<Int> = emptyList(),
    /** Set on an infinity chest: the item it provides or the fact that it voids everything. */
    val infinity: InfinitySettings? = null,
) {

    constructor(
        name: String,
        topLeft: Coordinate,
        direction: Int? = null,
        recipe: String? = null,
        type: String? = null,
        marker: Material? = null,
        markerFluid: Boolean = false,
        neighbours: List<Int> = emptyList(),
        /** Set on an infinity chest: the item it provides or the fact that it voids everything. */
        infinity: InfinitySettings? = null,
    ) : this(
        name = name,
        area = topLeft.area(1, 1),
        direction = direction,
        recipe = recipe,
        type = type,
        marker = marker,
        markerFluid = markerFluid,
        neighbours = neighbours,
        infinity = infinity,
    )

    fun isPowered(): Boolean = recipe != null || name.endsWith("inserter")
}

/**
 * An infinity chest's settings. With an [item] the chest keeps [item] available (a test input);
 * without one and [removeUnfiltered] it simply deletes whatever reaches it (a test output).
 */
data class InfinitySettings(val item: Material? = null, val removeUnfiltered: Boolean = false)

/** A whole blueprint: its [label], an optional [icon] signal and its [entities]. */
data class Blueprint(
    val label: String,
    val icon: Material? = null,
    val entities: List<BlueprintEntity> = emptyList(),
)

/**
 * Serializes the blueprint to the compact JSON Factorio embeds in a blueprint string. Optional
 * fields are omitted and integral coordinates are written without a decimal part, both to keep the
 * string short.
 */
fun Blueprint.toJson(): String =
    buildJsonObject {
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

    direction?.let { put("direction", it) }
    recipe?.let { put("recipe", it) }
    type?.let { put("type", it) }
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
    if (neighbours.isNotEmpty()) {
        putJsonArray("neighbours") { neighbours.forEach { add(JsonPrimitive(it)) } }
    }
    marker?.let { put("control_behavior", markerControlBehavior(it, markerFluid)) }
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
