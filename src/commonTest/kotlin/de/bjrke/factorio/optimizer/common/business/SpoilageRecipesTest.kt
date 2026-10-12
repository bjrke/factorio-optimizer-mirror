package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SpoilageRecipesTest {

    private val dump =
        Json.parseToJsonElement(
                """
                {
                  "item": {
                    "iron-bacteria": {"spoil_ticks": 3600, "spoil_result": "iron-ore"},
                    "copper-bacteria": {"spoil_ticks": 3600, "spoil_result": "copper-ore"},
                    "agricultural-science-pack": {"spoil_ticks": 216000, "spoil_result": "spoilage"},
                    "nutrients": {"spoil_ticks": 18000, "spoil_result": "spoilage"},
                    "biter-egg": {"spoil_ticks": 108000}
                  },
                  "capsule": {
                    "raw-fish": {"spoil_ticks": 453000, "spoil_result": "spoilage"},
                    "bioflux": {"spoil_ticks": 432000, "spoil_result": "spoilage"},
                    "jellynut": {"spoil_ticks": 216000, "spoil_result": "spoilage"},
                    "yumako": {"spoil_ticks": 216000, "spoil_result": "spoilage"},
                    "jelly": {"spoil_ticks": 14400, "spoil_result": "spoilage"},
                    "yumako-mash": {"spoil_ticks": 10800, "spoil_result": "spoilage"}
                  }
                }
                """
            )
            .let { it as JsonObject }

    private val recipes = spoilageRecipes(dump, Material::of).associateBy(Recipe::name)

    @Test
    fun bacteriaRotIntoOre() {
        assertEquals(
            Recipe(
                name = "iron-bacteria-rotting",
                ingredients = mapOf(Material("iron-bacteria") to 1.0),
                result = mapOf(Material("iron-ore") to 1.0),
                time = 60.0,
                categories = setOf(Category.ROTTING),
            ),
            recipes.getValue("iron-bacteria-rotting"),
        )
        assertEquals(
            Recipe(
                name = "copper-bacteria-rotting",
                ingredients = mapOf(Material("copper-bacteria") to 1.0),
                result = mapOf(Material("copper-ore") to 1.0),
                time = 60.0,
                categories = setOf(Category.ROTTING),
            ),
            recipes.getValue("copper-bacteria-rotting"),
        )
    }

    @Test
    fun otherItemsRotIntoSpoilage() {
        val expected =
            mapOf(
                "raw-fish-rotting" to 7550.0,
                "bioflux-rotting" to 7200.0,
                "jellynut-rotting" to 3600.0,
                "yumako-rotting" to 3600.0,
                "agricultural-science-pack-rotting" to 3600.0,
                "nutrients-rotting" to 300.0,
                "jelly-rotting" to 240.0,
                "yumako-mash-rotting" to 180.0,
            )

        expected.forEach { (name, time) ->
            val recipe = recipes.getValue(name)
            val source = name.removeSuffix("-rotting")
            assertEquals(setOf(Category.ROTTING), recipe.categories, name)
            assertEquals(time, recipe.time, name)
            assertEquals(mapOf(Material(source) to 1.0), recipe.ingredients, name)
            assertEquals(mapOf(Material("spoilage") to 1.0), recipe.result, name)
        }
    }

    @Test
    fun everyRecipeRots() {
        assertEquals(10, recipes.size)
        assertEquals(10, recipes.values.count { it.categories.contains(Category.ROTTING) })
    }
}
