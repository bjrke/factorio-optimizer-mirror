package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import kotlinx.serialization.json.*

private const val DEFAULT_AMOUNT = 1.0
private const val DEFAULT_PROBABILITY = 1.0
private const val DEFAULT_CATEGORY = "crafting"

internal fun JsonObject?.primitive(name: String) = this?.get(name) as? JsonPrimitive

internal fun JsonObject?.string(name: String) = primitive(name)?.contentOrNull

internal fun JsonObject?.number(name: String) = primitive(name)?.doubleOrNull

internal fun JsonObject?.boolean(name: String) = primitive(name)?.booleanOrNull

internal fun JsonObject?.table(name: String) =
    (this?.get(name) as? JsonObject) ?: JsonObject(emptyMap())

internal fun JsonObject?.array(name: String) =
    (this?.get(name) as? JsonArray) ?: JsonArray(emptyList())

internal fun JsonObject?.objects(name: String) = array(name).filterIsInstance<JsonObject>()

internal fun JsonObject?.strings(name: String) =
    array(name).filterIsInstance<JsonPrimitive>().mapNotNull(JsonPrimitive::contentOrNull)

inline fun <reified R> JsonObject.filterValueIsInstance(): List<Pair<String, R>> =
    mapNotNull { (k, v) ->
        if (v is R) (k to v) else null
    }

/**
 * The probability that a result is produced, from `probability`, `shared_probability` or
 * `independent_probability`.
 */
private fun resultProbability(result: JsonObject): Double =
    result.number("probability")
        ?: result.table("shared_probability").let {
            if (it.isEmpty()) null else (it.number("max") ?: 1.0) - (it.number("min") ?: 0.0)
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

    val parsedRecipes =
        sequence {
                for ((name, element) in root.table("recipe").filterValueIsInstance<JsonObject>()) {
                    if (element.boolean("parameter") == true) continue

                    val subgroupName = element.string("subgroup")
                    when (subgroupName) {
                        "fill-barrel",
                        "empty-barrel" -> continue
                    }

                    val result = buildMap {
                        element.string("result")?.also {
                            put(material(it), (element.number("result_count") ?: DEFAULT_AMOUNT))
                        }
                        for (entry in element.objects("results")) {
                            val resultName = entry.string("name") ?: continue

                            // recycling recipes express fractional yields via
                            // `extra_count_fraction`
                            val extraCountFraction = entry.number("extra_count_fraction") ?: 0.0

                            // on top of `amount` (usually 0), so they must be added, not replaced
                            val amount = entry.number("amount") ?: DEFAULT_AMOUNT

                            put(
                                material(resultName),
                                (amount + extraCountFraction) * resultProbability(entry),
                            )
                        }
                    }
                    if (result.isEmpty()) continue

                    val ingredients = buildMap {
                        for (entry in element.objects("ingredients")) {
                            entry.string("name")?.also {
                                put(material(it), entry.number("amount") ?: DEFAULT_AMOUNT)
                            }
                        }
                    }

                    val categories =
                        element
                            .strings("categories")
                            .ifEmpty { listOf(DEFAULT_CATEGORY) }
                            .mapNotNull(::category)
                            .toSet()
                    if (categories.isEmpty()) continue

                    val recycling = Category.RECYCLING in categories
                    // hidden recipes are internal/cheat prototypes (loaders, infinity chests, ...);
                    // the auto-generated lossy `*-recycling` recipes are hidden too, but they are
                    // the recycler's real production recipes and must be kept.
                    if (element.boolean("hidden") == true && !recycling) continue
                    // a self-recycling recipe (an ingredient is also a result) is a lossy raw->raw
                    // conversion that would let any resource "produce itself"; drop it.
                    if (recycling && ingredients.keys.intersect(result.keys).isNotEmpty()) continue

                    yield(
                        Recipe(
                            name = name,
                            ingredients = ingredients,
                            result = result,
                            time = element.number("energy_required") ?: 0.5,
                            subgroup = subGroup(subgroupName),
                            categories = categories,
                            surfaceConditions = element.surfaceConditions(),
                        )
                    )
                }

                yieldAll(resourceRecipes(root, ::material))
                yieldAll(spoilageRecipes(root, ::material))
                yieldAll(defaultRecipesProvider(::material))
            }
            .associateBy { it.name }

    // Only scrap and what scrap-recycling yields may enter the recycler. Keeping recycling recipes
    // one step past scrap avoids reverse chains (e.g. an accumulator recycling back into the
    // circuits that used to make it) that would otherwise beat the forward recipes.
    val scrapReach = buildSet {
        add(material("scrap"))
        parsedRecipes["scrap-recycling"]?.run {
            addAll(result.keys)
        }
    }

    val recipes =
        parsedRecipes.values
            .filter { recipe ->
                Category.RECYCLING !in recipe.categories ||
                    scrapReach.containsAll(recipe.ingredients.keys)
            }
            .sortedBy(Recipe::name)

    return ParsedRecipe(
        materials = materialsBuilder.values.sortedBy(Material::name),
        recipes = recipes,
        machineStats = machineStats(root),
        logistics = logisticsStats(root),
    )
}

/**
 * Belt speeds (tiles per tick), inserter stats, the fastest pump rate (fluid per second) and the
 * set of fluid materials, all derived from the dump.
 */
fun logisticsStats(root: JsonObject): Logistics {
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
                        bulk = bulk,
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

    val fluids = root.table("fluid").keys.mapTo(mutableSetOf(), Material::of)

    return Logistics(
        belts = belts,
        inserters = inserters,
        pumpRate = pumpRate,
        fluids = fluids,
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

private fun miningCategory(identifier: String) =
    when (identifier) {
        "basic-solid" -> Category.MINING_BASIC_SOLID
        "hard-solid" -> Category.MINING_HARD_SOLID
        "basic-fluid" -> Category.MINING_FLUID
        else -> null
    }

private fun JsonObject.surfaceConditions() =
    objects("surface_conditions").mapNotNull { table ->
        val property = table.string("property")?.let(::surfaceProperty) ?: return@mapNotNull null
        val min = table.number("min")?.toInt() ?: Int.MIN_VALUE
        val max = table.number("max")?.toInt() ?: Int.MAX_VALUE
        SurfaceCondition(property = property, range = min..max)
    }
