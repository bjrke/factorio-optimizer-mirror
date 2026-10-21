package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import kotlinx.serialization.json.*

/**
 * Parses the `data.raw` JSON dumped by `factorio --dump-data` (see the `dumpPrototypes` gradle
 * task) into the optimizer's model. Only the prototype types the optimizer needs are read;
 * everything else in the dump is ignored.
 */
fun parsePrototypes(json: String, customRecipes: Iterable<Recipe> = emptyList()): ParsedRecipe {
    val root =
        Json.parseToJsonElement(json) as? JsonObject
            ?: return ParsedRecipe(emptyList(), emptyList())

    val subgroups = mutableMapOf<String, Subgroup>()
    val noSubGroup = Subgroup()
    fun subGroup(name: String?) =
        when (name) {
            null -> noSubGroup
            else -> subgroups.getOrPut(name) { Subgroup(name) }
        }

    val parsedRecipes = buildList {
        for ((name, element) in root.table("recipe").filterValueIsInstance<JsonObject>()) {
            recipePrototype(name, element, ::subGroup)?.let(::add)
        }

        addAll(resourceRecipes(root))
        addAll(spoilageRecipes(root))
        addAll(customRecipes)
    }
        .associateBy(Recipe::name)

    // Only scrap and what scrap-recycling yields may enter the recycler. Keeping recycling recipes
    // one step past scrap avoids reverse chains (e.g. an accumulator recycling back into the
    // circuits that used to make it) that would otherwise beat the forward recipes.
    val scrapReach = parsedRecipes.scrapReach()
    val recipes =
        parsedRecipes.values
            .filter { recipe ->
                Category.RECYCLING !in recipe.categories ||
                    scrapReach.containsAll(recipe.ingredients.keys)
            }
            .sortedBy(Recipe::name)

    return ParsedRecipe(
        // Only materials a kept recipe produces: a hidden recipe's items (loaders, cheat entities,
        // ...) are dropped above but would otherwise leak in, and a material that merely appears as
        // an ingredient (with no recipe yielding it) has no surface or factory to be produced on.
        materials =
            recipes
                .flatMapTo(mutableSetOf()) {
                    it.result.keys
                }
                .sortedBy(Material::name),
        recipes = recipes,
        machineStats = machineStats(root),
        logistics = logisticsStats(root),
    )
}

/** `scrap` and everything the scrap-recycling recipe yields. */
private fun Map<String, Recipe>.scrapReach() = buildSet {
    add(Material("scrap"))
    this@scrapReach["scrap-recycling"]?.run {
        addAll(result.keys)
    }
}
