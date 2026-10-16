package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
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
                """,
            )
            .let { it as JsonObject }

    private val recipes = spoilageRecipes(dump).associateBy(Recipe::name)

    private val spoilTimes =
        mapOf(
            "raw-fish-spoiling" to 7550.0,
            "bioflux-spoiling" to 7200.0,
            "jellynut-spoiling" to 3600.0,
            "yumako-spoiling" to 3600.0,
            "agricultural-science-pack-spoiling" to 3600.0,
            "nutrients-spoiling" to 300.0,
            "jelly-spoiling" to 240.0,
            "yumako-mash-spoiling" to 180.0,
        )

    @Test
    fun bacteriaSpoilIntoOre() =
        assertTableOf("iron-bacteria" to "iron-ore", "copper-bacteria" to "copper-ore") {
            (bacteria, ore) ->
            assertEquals(
                Recipe(
                    name = "$bacteria-spoiling",
                    ingredients = mapOf(Material(bacteria) to 1.0),
                    result = mapOf(Material(ore) to 1.0),
                    time = 60.0,
                    categories = setOf(Category.SPOILING),
                ),
                recipes.getValue("$bacteria-spoiling"),
            )
        }

    @Test
    fun otherItemsHaveSpoilingCategory() =
        assertTable(spoilTimes.keys) { name ->
            assertEquals(setOf(Category.SPOILING), recipes.getValue(name).categories, name)
        }

    @Test
    fun otherItemsSpoilAfterTheirSpoilTime() =
        assertTable(spoilTimes.entries) { (name, time) ->
            assertEquals(time, recipes.getValue(name).time, name)
        }

    @Test
    fun otherItemsSpoilFromThemselves() =
        assertTable(spoilTimes.keys) { name ->
            val source = name.removeSuffix("-spoiling")
            assertEquals(mapOf(Material(source) to 1.0), recipes.getValue(name).ingredients, name)
        }

    @Test
    fun otherItemsSpoilIntoSpoilage() =
        assertTable(spoilTimes.keys) { name ->
            assertEquals(mapOf(Material("spoilage") to 1.0), recipes.getValue(name).result, name)
        }

    @Test
    fun everyRecipeSpoils() =
        assertAll(
            { assertEquals(10, recipes.size) },
            {
                assertEquals(10, recipes.values.count { it.categories.contains(Category.SPOILING) })
            },
        )
}
