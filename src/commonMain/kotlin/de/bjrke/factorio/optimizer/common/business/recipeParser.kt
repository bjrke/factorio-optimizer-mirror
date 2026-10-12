package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import kotlinx.serialization.json.*

private const val DEFAULT_AMOUNT = 1.0
private const val DEFAULT_PROBABILITY = 1.0
private const val DEFAULT_CATEGORY = "crafting"

internal fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.number(name: String) = (this[name] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.boolean(name: String) = (this[name] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.table(name: String) = this[name] as? JsonObject

internal fun JsonObject.array(name: String) = this[name] as? JsonArray

internal fun JsonArray.strings() = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

internal fun JsonArray.objects() = mapNotNull { it as? JsonObject }

/**
 * The probability that a result is produced, from `probability`, `shared_probability` or
 * `independent_probability`.
 */
private fun resultProbability(result: JsonObject): Double =
    result.number("probability")
        ?: result.table("shared_probability")?.let {
            (it.number("max") ?: 1.0) - (it.number("min") ?: 0.0)
        }
        ?: result.number("independent_probability")
        ?: DEFAULT_PROBABILITY

/**
 * Parses the `data.raw` JSON dumped by `factorio --dump-data` (see the `dumpPrototypes` gradle
 * task) into the optimizer's model. Only the prototype types the optimizer needs are read;
 * everything else in the dump is ignored.
 */
fun parsePrototypes(
    json: String,
    defaultRecipesProvider: ((String) -> Material) -> List<Recipe> = { listOf() },
): ParsedRecipe {
    val root =
        Json.parseToJsonElement(json) as? JsonObject
            ?: return ParsedRecipe(emptyList(), emptyList())

    val materialsBuilder = mutableMapOf<String, Material>()
    fun material(name: String) = materialsBuilder.getOrPut(name) { Material.of(name) }

    val subgroups = mutableMapOf<String, Subgroup>()
    val noSubGroup = Subgroup()
    fun subGroup(name: String?) =
        when (name) {
            null -> noSubGroup
            else -> subgroups.getOrPut(name) { Subgroup(name) }
        }

    val recipes =
        sequence {
                for ((name, element) in root.table("recipe") ?: JsonObject(emptyMap())) {
                    val prototype = element as? JsonObject ?: continue
                    if (prototype.boolean("parameter") == true) continue
                    // hidden recipes are internal/cheat prototypes (the auto-generated lossy
                    // `*-recycling` recipes, loaders, infinity chests, ...); only `scrap-recycling`
                    // is a real production recipe, and it is not hidden.
                    if (prototype.boolean("hidden") == true) continue

                    val subgroupName = prototype.string("subgroup")
                    when (subgroupName) {
                        "fill-barrel",
                        "empty-barrel" -> continue
                    }

                    val result = buildMap {
                        prototype.string("result")?.also {
                            put(material(it), (prototype.number("result_count") ?: DEFAULT_AMOUNT))
                        }
                        for (entry in prototype.array("results")?.objects().orEmpty()) {
                            val resultName = entry.string("name") ?: continue
                            put(
                                material(resultName),
                                (entry.number("amount") ?: DEFAULT_AMOUNT) *
                                    resultProbability(entry),
                            )
                        }
                    }
                    if (result.isEmpty()) continue

                    val ingredients = buildMap {
                        for (entry in prototype.array("ingredients")?.objects().orEmpty()) {
                            val ingredientName = entry.string("name") ?: continue
                            put(
                                material(ingredientName),
                                entry.number("amount") ?: DEFAULT_AMOUNT,
                            )
                        }
                    }

                    val categories =
                        prototype
                            .array("categories")
                            ?.strings()
                            .orEmpty()
                            .ifEmpty { listOf(DEFAULT_CATEGORY) }
                            .mapNotNull(::category)
                            .toSet()
                    if (categories.isEmpty()) continue

                    yield(
                        Recipe(
                            name = name,
                            ingredients = ingredients,
                            result = result,
                            time = prototype.number("energy_required") ?: 0.5,
                            subgroup = subGroup(subgroupName),
                            categories = categories,
                            surfaceConditions = prototype.surfaceConditions(),
                        )
                    )
                }

                yieldAll(resourceRecipes(root, ::material))
                yieldAll(spoilageRecipes(root, ::material))
                yieldAll(defaultRecipesProvider(::material))
            }
            .sortedBy(Recipe::name)
            .toList()

    return ParsedRecipe(
        materials = materialsBuilder.values.sortedBy(Material::name),
        recipes = recipes,
        machineStats = machineStats(root),
    )
}

/**
 * Machine crafting categories, speed and built-in productivity, derived from the crafting machines
 * in the dump. Mining drills use `resource_categories`/`mining_speed` instead of
 * `crafting_categories`/`crafting_speed`.
 */
fun machineStats(root: JsonObject): Map<String, MachineStats> {
    val stats = mutableMapOf<String, MachineStats>()

    fun add(kind: String, categoriesKey: String, speedKey: String) {
        root.table(kind)?.forEach { (name, element) ->
            val prototype = element as? JsonObject ?: return@forEach
            val categories =
                prototype
                    .array(categoriesKey)
                    ?.strings()
                    .orEmpty()
                    .mapNotNull { miningCategory(it) ?: category(it) }
                    .toSet()
            if (categories.isEmpty()) return@forEach
            stats[name] =
                MachineStats(
                    categories = categories,
                    speed = prototype.number(speedKey) ?: 1.0,
                    productivity =
                        prototype
                            .table("effect_receiver")
                            ?.table("base_effect")
                            ?.number("productivity") ?: 0.0,
                )
        }
    }

    add("assembling-machine", "crafting_categories", "crafting_speed")
    add("furnace", "crafting_categories", "crafting_speed")
    add("rocket-silo", "crafting_categories", "crafting_speed")
    add("mining-drill", "resource_categories", "mining_speed")
    return stats
}

private fun miningCategory(identifier: String) =
    when (identifier) {
        "basic-solid" -> Category.MINING_BASIC_SOLID
        "hard-solid" -> Category.MINING_HARD_SOLID
        "basic-fluid" -> Category.MINING_FLUID
        else -> null
    }

private fun JsonObject.surfaceConditions(): List<SurfaceCondition> =
    array("surface_conditions")?.objects().orEmpty().mapNotNull { table ->
        val property = table.string("property")?.let(::surfaceProperty) ?: return@mapNotNull null
        val min = table.number("min")?.toInt() ?: Int.MIN_VALUE
        val max = table.number("max")?.toInt() ?: Int.MAX_VALUE
        SurfaceCondition(property = property, range = min..max)
    }
