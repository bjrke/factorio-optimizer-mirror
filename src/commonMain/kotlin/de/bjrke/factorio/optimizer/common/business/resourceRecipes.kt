package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlinx.serialization.json.JsonObject

/**
 * Extraction recipes representing raw resources taken from the map. The solid/fluid resources and
 * the offshore pump fluids are derived from the `data.raw` dump (`resource.minable` and the tiles
 * that carry a `fluid`); only the two special extraction mechanics that have no prototype recipe
 * are hardcoded below (harvesting trees and collecting asteroid chunks).
 */
fun resourceRecipes(root: JsonObject, material: (String) -> Material): List<Recipe> =
    minedResourceRecipes(root, material) +
        offshorePumpFluids(root, material) +
        harvestedFruit(material) +
        asteroidCollectionRecipes(material)

/** `data.raw.resource`: the resource the drill/pumpjack extracts and how long a unit takes. */
private fun minedResourceRecipes(root: JsonObject, material: (String) -> Material): List<Recipe> =
    root.table("resource").filterValueIsInstance<JsonObject>().mapNotNull { (name, element) ->
        val minable = element.table("minable")
        val category =
            when (element.string("category")) {
                null,
                "basic-solid" -> Category.MINING_BASIC_SOLID
                "hard-solid" -> Category.MINING_HARD_SOLID
                "basic-fluid" -> Category.MINING_FLUID
                else -> return@mapNotNull null
            }
        val result = buildMap {
            minable.string("result")?.also {
                put(material(it), minable.number("count") ?: 1.0)
            }
            for (entry in minable.objects("results")) {
                val resultName = entry.string("name") ?: continue
                put(material(resultName), entry.number("amount") ?: 1.0)
            }
        }
        if (result.isEmpty()) return@mapNotNull null

        val ingredients =
            minable.string("required_fluid")?.let {
                mapOf(material(it) to (minable.number("fluid_amount") ?: 1.0))
            } ?: emptyMap()

        Recipe(
            name = name,
            ingredients = ingredients,
            result = result,
            time = minable.number("mining_time") ?: 1.0,
            categories = setOf(category),
        )
    }

/**
 * The offshore pump extracts whatever fluid the tile it is placed on carries. The dump's tiles
 * reference water, heavy oil, lava and ammoniacal solution.
 */
private fun offshorePumpFluids(root: JsonObject, material: (String) -> Material): List<Recipe> =
    root
        .table("tile")
        .values
        .mapNotNull { (it as? JsonObject)?.string("fluid") }
        .distinct()
        .map { fluid ->
            Recipe(
                name = fluid,
                ingredients = emptyMap(),
                result = mapOf(material(fluid) to 1.0),
                time = 1.0,
                categories = setOf(Category.OFFSHORE_PUMPING),
            )
        }

/** Trees tend to a fruit every 5 minutes; the agricultural tower harvests 50 fruit per tree. */
private fun harvestedFruit(material: (String) -> Material): List<Recipe> =
    listOf("yumako", "jellynut").map { fruit ->
        Recipe(
            name = fruit,
            ingredients = emptyMap(),
            result = mapOf(material(fruit) to 50.0),
            time = 300.0,
            categories = setOf(Category.HARVESTING),
        )
    }

/**
 * Asteroid chunks are collected by the asteroid collector in space. The collector prototype has a
 * 0.2s mining time; the in-game chunk mix depends on where the platform is, so all chunk types are
 * assumed to drop at the same rate. Modelled as one recipe so a single collector serves every
 * chunk.
 */
private fun asteroidCollectionRecipes(material: (String) -> Material) =
    listOf(
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
                    .associate { material(it) to 1.0 },
            time = 0.2,
            categories = setOf(Category.ASTEROID_COLLECTION),
        )
    )
