package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.BeltStats
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.game.prototype.Logistics
import de.bjrke.factorio.optimizer.common.game.prototype.MachineStats
import de.bjrke.factorio.optimizer.common.game.prototype.PoleStats
import de.bjrke.factorio.optimizer.common.game.prototype.category
import kotlinx.serialization.json.JsonObject

/**
 * Belt, inserter, pole and pump stats plus the set of fluid materials, all derived from the dump.
 */
internal fun logisticsStats(root: JsonObject) =
    Logistics(
        belts = beltStats(root),
        inserters = inserterStats(root),
        poles = poleStats(root),
        pumpRate = pumpRate(root),
        fluids = root.table("fluid").keys.mapTo(mutableSetOf()) { Material(it) },
        fluidTemperatures =
            root
                .table("fluid")
                .filterValueIsInstance<JsonObject>()
                .mapNotNull { (name, element) ->
                    element.number("default_temperature")?.toInt()?.let { Material(name) to it }
                }
                .toMap(),
    )

/**
 * The speed of every transport belt and the reach of its underground counterpart. The reach is not
 * on the belt prototype itself: `related_underground_belt` names the underground prototype that
 * carries the `max_distance`. A belt without both is not usable and is left out.
 */
private fun beltStats(root: JsonObject): Map<String, BeltStats> {
    val undergroundRanges =
        root
            .table("underground-belt")
            .filterValueIsInstance<JsonObject>()
            .mapNotNull { (name, element) ->
                element.number("max_distance")?.toInt()?.let { name to it }
            }
            .toMap()

    return root
        .table("transport-belt")
        .filterValueIsInstance<JsonObject>()
        .mapNotNull { (name, element) ->
            val speed = element.number("speed") ?: return@mapNotNull null
            val underground = element.string("related_underground_belt") ?: return@mapNotNull null
            val range = undergroundRanges[underground] ?: return@mapNotNull null
            name to BeltStats(speed = speed, undergroundRange = range)
        }
        .toMap()
}

/**
 * The rotation speed and hand sizes of every inserter. The inserter capacity bonus research tops up
 * the hand size: a bulk inserter (and the stack inserter, which is a bulk inserter) gets the bulk
 * inserter capacity bonus, a plain inserter the generic stack size bonus.
 */
private fun inserterStats(root: JsonObject): Map<String, InserterStats> {
    val capacityTechs =
        root
            .table("technology")
            .filterValueIsInstance<JsonObject>()
            .filter { (name, _) -> name.startsWith("inserter-capacity-bonus") }
            .map { (_, tech) -> tech }
    val bulkCapacityBonus = capacityTechs.sumOf { it.effectTotal("bulk-inserter-capacity-bonus") }
    val stackSizeBonus = capacityTechs.sumOf { it.effectTotal("inserter-stack-size-bonus") }

    return root
        .table("inserter")
        .filterValueIsInstance<JsonObject>()
        .mapNotNull { (name, element) ->
            val rotationSpeed = element.number("rotation_speed") ?: return@mapNotNull null
            val bulk = element.boolean("bulk") ?: false
            // a bulk inserter starts at two items, the stack inserter adds its prototype
            // stack_size_bonus on top; every other inserter carries a single item
            val minStack = (if (bulk) 2 else 1) + (element.number("stack_size_bonus")?.toInt() ?: 0)
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
}

/** The supply area and wire reach of every electric pole. */
private fun poleStats(root: JsonObject): Map<String, PoleStats> =
    root
        .table("electric-pole")
        .filterValueIsInstance<JsonObject>()
        .mapNotNull { (name, element) ->
            val supplyArea = element.number("supply_area_distance") ?: return@mapNotNull null
            val wireReach = element.number("maximum_wire_distance") ?: return@mapNotNull null
            // 2.5 covers a 5x5 area, i.e. two tiles in each direction
            name to PoleStats(supplyRadius = supplyArea.toInt(), wireReach = wireReach)
        }
        .toMap()

/** The fastest pump's fluid per second; the fluid system is limited by it, not by the pipes. */
private fun pumpRate(root: JsonObject) =
    listOf("pump", "offshore-pump")
        .flatMap { root.table(it).values.filterIsInstance<JsonObject>() }
        .mapNotNull { it.number("pumping_speed") }
        .maxOrNull()
        ?.times(60.0) ?: 1200.0

/**
 * Machine crafting categories, speed, built-in productivity and energy use, derived from the
 * crafting machines in the dump. Mining drills use `resource_categories`/`mining_speed` instead of
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
                        energyUsage = element.energyValue("energy_usage") ?: 0.0,
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
