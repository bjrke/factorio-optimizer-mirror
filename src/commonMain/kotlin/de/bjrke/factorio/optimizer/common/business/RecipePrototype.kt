package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup
import de.bjrke.factorio.optimizer.common.model.SurfaceCondition
import de.bjrke.factorio.optimizer.common.model.category
import de.bjrke.factorio.optimizer.common.model.surfaceProperty
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_AMOUNT = 1.0
private const val DEFAULT_PROBABILITY = 1.0
private const val DEFAULT_CATEGORY = "crafting"

/**
 * One entry of `data.raw.recipe`, or `null` when it is not a recipe the optimizer can model. The
 * caller owns the dump-level filtering (scrap reach); this function owns the recipe's own fields.
 */
internal fun recipePrototype(
    name: String,
    element: JsonObject,
    subgroup: (String?) -> Subgroup,
): Recipe? {
    if (element.boolean("parameter") == true) return null

    val subgroupName = element.string("subgroup")
    if (subgroupName == "fill-barrel" || subgroupName == "empty-barrel") return null

    val result = recipeResults(element)
    if (result.isEmpty()) return null

    val categories = recipeCategories(element)
    if (categories.isEmpty()) return null

    val recycling = Category.RECYCLING in categories
    // hidden recipes are internal/cheat prototypes (loaders, infinity chests, ...);
    // the auto-generated lossy `*-recycling` recipes are hidden too, but they are
    // the recycler's real production recipes and must be kept.
    if (element.boolean("hidden") == true && !recycling) return null

    val ingredients = recipeIngredients(element)
    // a self-recycling recipe (an ingredient is also a result) is a lossy raw->raw
    // conversion that would let any resource "produce itself"; drop it.
    if (recycling && ingredients.keys.any { it in result.keys }) return null

    return Recipe(
        name = name,
        ingredients = ingredients,
        result = result,
        time = element.number("energy_required") ?: 0.5,
        subgroup = subgroup(subgroupName),
        categories = categories,
        surfaceConditions = element.surfaceConditions(),
    )
}

private fun recipeResults(element: JsonObject) = buildMap {
    element.string("result")?.also {
        put(Material(it), element.number("result_count") ?: DEFAULT_AMOUNT)
    }
    for (entry in element.objects("results")) {
        val resultName = entry.string("name") ?: continue

        // recycling recipes express fractional yields via `extra_count_fraction`
        val extraCountFraction = entry.number("extra_count_fraction") ?: 0.0

        // on top of `amount` (usually 0), so they must be added, not replaced
        val amount = entry.number("amount") ?: DEFAULT_AMOUNT

        put(Material(resultName), (amount + extraCountFraction) * resultProbability(entry))
    }
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

private fun recipeIngredients(element: JsonObject) = buildMap {
    for (entry in element.objects("ingredients")) {
        entry.string("name")?.also {
            put(Material(it), entry.number("amount") ?: DEFAULT_AMOUNT)
        }
    }
}

private fun recipeCategories(element: JsonObject) =
    element
        .strings("categories")
        .ifEmpty { listOf(DEFAULT_CATEGORY) }
        .mapNotNull(::category)
        .toSet()

private fun JsonObject.surfaceConditions() =
    objects("surface_conditions").mapNotNull { table ->
        val property = table.string("property")?.let(::surfaceProperty) ?: return@mapNotNull null
        val min = table.number("min")?.toInt() ?: Int.MIN_VALUE
        val max = table.number("max")?.toInt() ?: Int.MAX_VALUE
        SurfaceCondition(property = property, range = min..max)
    }
