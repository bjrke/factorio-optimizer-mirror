package de.bjrke.factorio.optimizer.common.production

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.prototype.Logistics
import de.bjrke.factorio.optimizer.common.prototype.MachineStats
import de.bjrke.factorio.optimizer.common.prototype.category
import kotlinx.serialization.json.JsonObject

/**
 * Belt speeds (tiles per tick), inserter stats, the fastest pump rate (fluid per second) and the
 * set of fluid materials, all derived from the dump.
 */
internal fun logisticsStats(root: JsonObject): Logistics {
    val belts =
        root
            .table("transport-belt")
            .filterValueIsInstance<JsonObject>()
            .mapNotNull { (name, element) ->
                element.number("speed")?.let { name to it }
            }
            .toMap()

    // The inserter capacity bonus research tops up the hand size. A bulk inserter (and the stack
    // inserter, which is a bulk inserter) also gets the bulk inserter capacity bonus, a plain
    // inserter the generic stack size bonus.
    val capacityTechs =
        root
            .table("technology")
            .filterValueIsInstance<JsonObject>()
            .filter { (name, _) -> name.startsWith("inserter-capacity-bonus") }
            .map { (_, tech) -> tech }
    val bulkCapacityBonus = capacityTechs.sumOf { it.effectTotal("bulk-inserter-capacity-bonus") }
    val stackSizeBonus = capacityTechs.sumOf { it.effectTotal("inserter-stack-size-bonus") }

    val inserters =
        root
            .table("inserter")
            .filterValueIsInstance<JsonObject>()
            .mapNotNull { (name, element) ->
                val rotationSpeed = element.number("rotation_speed") ?: return@mapNotNull null
                val bulk = element.boolean("bulk") ?: false
                // a bulk inserter starts at two items, the stack inserter adds its prototype
                // stack_size_bonus on top; every other inserter carries a single item
                val minStack =
                    (if (bulk) 2 else 1) + (element.number("stack_size_bonus")?.toInt() ?: 0)
                name to
                    InserterStats(
                        rotationSpeed = rotationSpeed,
                        minStack = minStack,
                        maxStack = minStack + if (bulk) bulkCapacityBonus else stackSizeBonus,
                        // only the stack inserter carries max_belt_stack_size; the bulk inserter
                        // cannot stack items on a belt
                        stacksOnBelt = element.number("max_belt_stack_size") != null,
                    )
            }
            .toMap()

    // the fluid system is limited by the fastest pump, not by the pipes; both pumps are 1200/s
    val pumpRate =
        listOf("pump", "offshore-pump")
            .flatMap { root.table(it).values.filterIsInstance<JsonObject>() }
            .mapNotNull { it.number("pumping_speed") }
            .maxOrNull()
            ?.times(60.0)

    val fluids = root.table("fluid").keys.mapTo(mutableSetOf()) { Material(it) }
    val fluidTemperatures =
        root
            .table("fluid")
            .filterValueIsInstance<JsonObject>()
            .mapNotNull { (name, element) ->
                element.number("default_temperature")?.toInt()?.let { Material(name) to it }
            }
            .toMap()

    return Logistics(
        belts = belts,
        inserters = inserters,
        pumpRate = pumpRate,
        fluids = fluids,
        fluidTemperatures = fluidTemperatures,
    )
}

/**
 * Machine crafting categories, speed and built-in productivity, derived from the crafting machines
 * in the dump. Mining drills use `resource_categories`/`mining_speed` instead of
 * `crafting_categories`/`crafting_speed`.
 */
fun machineStats(root: JsonObject) = buildMap {
    fun addKind(kind: String, categoriesKey: String, speedKey: String) {
        root.table(kind).filterValueIsInstance<JsonObject>().forEach { (name, element) ->
            val categories =
                element
                    .strings(categoriesKey)
                    .mapNotNull { miningCategory(it) ?: category(it) }
                    .toSet()
            if (categories.isNotEmpty()) {
                put(
                    name,
                    MachineStats(
                        categories = categories,
                        speed = element.number(speedKey) ?: 1.0,
                        productivity =
                            element
                                .table("effect_receiver")
                                .table("base_effect")
                                .number("productivity") ?: 0.0,
                    ),
                )
            }
        }
    }

    addKind("assembling-machine", "crafting_categories", "crafting_speed")
    addKind("furnace", "crafting_categories", "crafting_speed")
    addKind("rocket-silo", "crafting_categories", "crafting_speed")
    addKind("mining-drill", "resource_categories", "mining_speed")
}

/** The summed `modifier` of every [effectType] effect of a technology. */
private fun JsonObject.effectTotal(effectType: String) =
    objects("effects")
        .filter { it.string("type") == effectType }
        .sumOf { it.number("modifier")?.toInt() ?: 1 }

internal fun miningCategory(identifier: String) =
    when (identifier) {
        "basic-solid" -> Category.MINING_BASIC_SOLID
        "hard-solid" -> Category.MINING_HARD_SOLID
        "basic-fluid" -> Category.MINING_FLUID
        else -> null
    }
