package de.bjrke.factorio.optimizer.buildlogic

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * The prototype categories and the fields within them that are worth keeping in the dumped
 * `data.raw`. Everything else — graphics, sounds, particles, autoplace, ... — is dropped to keep
 * the resources (and the web bundle that inlines them) small. Nested tables such as `ingredients`,
 * `results`, `minable` or `effect_receiver` are kept whole.
 */
private val prototypeFields =
    mapOf(
        "recipe" to
            setOf(
                "type",
                "name",
                "ingredients",
                "results",
                "result",
                "result_count",
                "energy_required",
                "categories",
                "subgroup",
                "hidden",
                "parameter",
                "allow_productivity",
                "allow_quality",
                "surface_conditions",
                "main_product",
                "additional_categories",
            ),
        "resource" to setOf("type", "name", "category", "minable", "order", "map_color", "flags"),
        "tile" to
            setOf(
                "type",
                "name",
                "fluid",
                "hidden",
                "order",
                "layer",
                "layer_group",
                "map_color",
                "collision_mask",
                "item_to_place",
            ),
        "item" to
            setOf(
                "type",
                "name",
                "hidden",
                "subgroup",
                "order",
                "stack_size",
                "spoil_ticks",
                "spoil_result",
                "fuel_value",
                "fuel_category",
                "burnt_result",
                "weight",
                "default_request_amount",
            ),
        "capsule" to
            setOf(
                "type",
                "name",
                "hidden",
                "subgroup",
                "order",
                "stack_size",
                "spoil_ticks",
                "spoil_result",
                "weight",
                "capsule_action",
            ),
        "assembling-machine" to
            setOf(
                "type",
                "name",
                "crafting_categories",
                "crafting_speed",
                "effect_receiver",
                "allowed_effects",
                "module_slots",
                "energy_usage",
                "surface_conditions",
                "fixed_recipe",
                "rocket_parts_required",
                "result_inventory_size",
                "source_inventory_size",
            ),
        "furnace" to
            setOf(
                "type",
                "name",
                "crafting_categories",
                "crafting_speed",
                "effect_receiver",
                "allowed_effects",
                "module_slots",
                "energy_usage",
                "surface_conditions",
                "result_inventory_size",
                "source_inventory_size",
            ),
        "rocket-silo" to
            setOf(
                "type",
                "name",
                "crafting_categories",
                "crafting_speed",
                "effect_receiver",
                "allowed_effects",
                "module_slots",
                "energy_usage",
                "surface_conditions",
                "fixed_recipe",
                "rocket_parts_required",
                "result_inventory_size",
                "source_inventory_size",
            ),
        "mining-drill" to
            setOf(
                "type",
                "name",
                "resource_categories",
                "mining_speed",
                "effect_receiver",
                "module_slots",
                "energy_usage",
                "resource_searching_radius",
                "vector_to_place_result",
                "mining_area",
            ),
        "transport-belt" to setOf("type", "name", "speed"),
        "inserter" to
            setOf(
                "type",
                "name",
                "rotation_speed",
                "bulk",
                "max_belt_stack_size",
                "stack_size_bonus",
            ),
        "pump" to setOf("type", "name", "pumping_speed"),
        "offshore-pump" to setOf("type", "name", "pumping_speed"),
        "fluid" to
            setOf(
                "type",
                "name",
                "default_temperature",
                "max_temperature",
                "base_color",
                "flow_color",
                "auto_barrel",
                "hidden",
                "subgroup",
                "order",
            ),
        "module" to
            setOf(
                "type",
                "name",
                "category",
                "effect",
                "tier",
                "requires_beacon_alt_mode",
                "stack_size",
                "subgroup",
                "order",
                "weight",
                "art_style",
            ),
        "technology" to
            setOf(
                "type",
                "name",
                "prerequisites",
                "effects",
                "unit",
                "research_trigger",
                "max_level",
                "upgrade",
            ),
        "tool" to
            setOf(
                "type",
                "name",
                "subgroup",
                "order",
                "stack_size",
                "durability",
                "weight",
                "hidden",
            ),
    )

private val json = Json { prettyPrint = true }

/**
 * Rewrites a `data.raw` dump in place, keeping only the [prototypeFields] the optimizer cares
 * about. Idempotent, so it can be run on already-sanitized files.
 */
fun sanitizePrototypeDump(file: File) {
    val root =
        json.parseToJsonElement(file.readText()) as? JsonObject
            ?: error("$file doesn't contain a JSON object")

    val kept = buildJsonObject {
        for ((category, fields) in prototypeFields) {
            val prototypes = root[category] as? JsonObject ?: continue

            put(
                category,
                JsonObject(
                    prototypes.mapValues { (_, prototype) ->
                        if (prototype is JsonObject)
                            JsonObject(prototype.filterKeys(fields::contains))
                        else prototype
                    },
                ),
            )
        }
    }

    file.writeText(json.encodeToString(JsonElement.serializer(), kept) + "\n")
}
