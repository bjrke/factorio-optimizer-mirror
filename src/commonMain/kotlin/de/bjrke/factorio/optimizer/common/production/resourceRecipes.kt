package de.bjrke.factorio.optimizer.common.production

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import kotlinx.serialization.json.JsonObject

/**
 * Extraction recipes representing raw resources taken from the map. The solid/fluid resources and
 * the offshore pump fluids are derived from the `data.raw` dump (`resource.minable` and the tiles
 * that carry a `fluid`); only the two special extraction mechanics that have no prototype recipe
 * are hardcoded below (harvesting trees and collecting asteroid chunks).
 */
fun resourceRecipes(root: JsonObject) = buildList {
    minedResourceRecipes(root)
    offshorePumpFluids(root)
    harvestedFruit()
    harvestedWood(root)
    asteroidCollectionRecipes()
}

/** `data.raw.resource`: the resource the drill/pumpjack extracts and how long a unit takes. */
private fun MutableList<Recipe>.minedResourceRecipes(root: JsonObject) {
    for ((name, element) in root.table("resource").filterValueIsInstance<JsonObject>()) {
        add(minedResourceRecipe(name, element) ?: continue)
    }
}

private fun minedResourceRecipe(name: String, element: JsonObject): Recipe? {
    val category = resourceCategory(element.string("category")) ?: return null
    val minable = element.table("minable")
    val result = minableResults(minable)
    if (result.isEmpty()) return null

    val ingredients =
        minable
            .string("required_fluid")
            ?.let { mapOf(Material(it) to (minable.number("fluid_amount") ?: 1.0)) }
            .orEmpty()

    return Recipe(
        name = name,
        ingredients = ingredients,
        result = result,
        time = minable.number("mining_time") ?: 1.0,
        categories = setOf(category),
    )
}

private fun resourceCategory(identifier: String?) =
    when (identifier) {
        null -> Category.MINING_BASIC_SOLID
        else -> miningCategory(identifier)
    }

private fun minableResults(minable: JsonObject) = buildMap {
    minable.string("result")?.also {
        put(Material(it), minable.number("count") ?: 1.0)
    }
    for (entry in minable.objects("results")) {
        val resultName = entry.string("name") ?: continue
        put(Material(resultName), entry.number("amount") ?: 1.0)
    }
}

/**
 * The offshore pump extracts whatever fluid the tile it is placed on carries. The dump's tiles
 * reference water, heavy oil, lava and ammoniacal solution.
 */
private fun MutableList<Recipe>.offshorePumpFluids(root: JsonObject) {
    val fluids =
        root.table("tile").values.mapNotNull { (it as? JsonObject)?.string("fluid") }.distinct()
    for (fluid in fluids) {
        add(
            Recipe(
                name = fluid,
                ingredients = emptyMap(),
                result = mapOf(Material(fluid) to 1.0),
                time = 1.0,
                categories = setOf(Category.OFFSHORE_PUMPING),
            ),
        )
    }
}

/** Trees tend to a fruit every 5 minutes; the agricultural tower harvests 50 fruit per tree. */
private fun MutableList<Recipe>.harvestedFruit() {
    for (fruit in listOf("yumako", "jellynut")) {
        add(
            Recipe(
                name = fruit,
                ingredients = emptyMap(),
                result = mapOf(Material(fruit) to 50.0),
                time = 300.0,
                categories = setOf(Category.HARVESTING),
            ),
        )
    }
}

/**
 * On Nauvis the agricultural tower plants tree seeds and harvests the grown trees for wood. A tree
 * takes 10 minutes to reach full maturity and yields the 4 wood a hand-mined tree gives. Only Space
 * Age ships the tower, marked by the `tree-seed` recipe being present in the dump; the base game
 * has no way to produce wood, so it must not be offered as producible there.
 */
private fun MutableList<Recipe>.harvestedWood(root: JsonObject) {
    if (root.table("recipe").containsKey("tree-seed"))
        add(
            Recipe(
                name = "wood",
                ingredients = emptyMap(),
                result = mapOf(Material("wood") to 4.0),
                time = 600.0,
                categories = setOf(Category.HARVESTING),
            ),
        )
}

/**
 * Asteroid chunks are collected by the asteroid collector in space. The collector prototype has a
 * 0.2s mining time; the in-game chunk mix depends on where the platform is, so all chunk types are
 * assumed to drop at the same rate. Modelled as one recipe so a single collector serves every
 * chunk.
 */
private fun MutableList<Recipe>.asteroidCollectionRecipes() {
    add(
        Recipe(
            name = "asteroid-collection",
            ingredients = emptyMap(),
            result =
                listOf(
                        "metallic-asteroid-chunk",
                        "carbonic-asteroid-chunk",
                        "oxide-asteroid-chunk",
                        "promethium-asteroid-chunk",
                    )
                    .associate { Material(it) to 1.0 },
            time = 0.2,
            categories = setOf(Category.ASTEROID_COLLECTION),
        ),
    )
}
