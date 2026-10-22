package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.data.resourceRecipes
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ResourceRecipesTest {

    private val dump =
        Json.parseToJsonElement(
                """
                {
                  "recipe": {
                    "tree-seed": {}
                  },
                  "resource": {
                    "iron-ore": {"minable": {"mining_time": 1, "result": "iron-ore"}},
                    "tungsten-ore": {"category": "hard-solid", "minable": {"mining_time": 5, "result": "tungsten-ore"}},
                    "crude-oil": {"category": "basic-fluid", "minable": {"mining_time": 1, "results": [{"type": "fluid", "name": "crude-oil", "amount": 10}]}},
                    "uranium-ore": {"minable": {"mining_time": 2, "result": "uranium-ore", "required_fluid": "sulfuric-acid", "fluid_amount": 10}},
                    "sulfuric-acid-geyser": {"category": "basic-fluid", "minable": {"mining_time": 1, "results": [{"type": "fluid", "name": "sulfuric-acid", "amount": 10}]}},
                    "fluorine-vent": {"category": "basic-fluid", "minable": {"mining_time": 1, "results": [{"type": "fluid", "name": "fluorine", "amount": 1}]}}
                  },
                  "tile": {
                    "water": {"fluid": "water"},
                    "lava": {"fluid": "lava"},
                    "oil-ocean": {"fluid": "heavy-oil"},
                    "ammoniacal-ocean": {"fluid": "ammoniacal-solution"}
                  }
                }
                """,
            )
            .let { it as JsonObject }

    private val recipes = resourceRecipes(dump).associateBy(Recipe::name)

    private val harvestedFruits = listOf("yumako", "jellynut")

    @Test
    fun basicSolidResource() {
        val iron = recipes.getValue("iron-ore")
        assertAll(
            { assertEquals(setOf(Category.MINING_BASIC_SOLID), iron.categories) },
            { assertEquals(1.0, iron.time) },
            { assertEquals(mapOf(Material("iron-ore") to 1.0), iron.result) },
            { assertEquals(emptyMap(), iron.ingredients) },
        )
    }

    @Test
    fun hardSolidResource() {
        val tungsten = recipes.getValue("tungsten-ore")
        assertAll(
            { assertEquals(setOf(Category.MINING_HARD_SOLID), tungsten.categories) },
            { assertEquals(5.0, tungsten.time) },
        )
    }

    @Test
    fun fluidResourceUsesPumpjackAmount() {
        val oil = recipes.getValue("crude-oil")
        assertAll(
            { assertEquals(setOf(Category.MINING_FLUID), oil.categories) },
            { assertEquals(mapOf(Material("crude-oil") to 10.0), oil.result) },
        )
    }

    @Test
    fun resourceCanRequireFluid() {
        val uranium = recipes.getValue("uranium-ore")
        assertAll(
            { assertEquals(mapOf(Material("sulfuric-acid") to 10.0), uranium.ingredients) },
            { assertEquals(2.0, uranium.time) },
        )
    }

    @Test
    fun geyserAndVentProduceDifferentFluid() =
        assertAll(
            {
                assertEquals(
                    mapOf(Material("sulfuric-acid") to 10.0),
                    recipes.getValue("sulfuric-acid-geyser").result,
                )
            },
            {
                assertEquals(
                    mapOf(Material("fluorine") to 1.0),
                    recipes.getValue("fluorine-vent").result,
                )
            },
        )

    @Test
    fun offshorePumpFluids() =
        assertTableOf("water", "heavy-oil", "lava", "ammoniacal-solution") { fluid ->
            assertEquals(setOf(Category.OFFSHORE_PUMPING), recipes.getValue(fluid).categories)
        }

    @Test
    fun harvestedFruitHasHarvestingCategory() =
        assertTable(harvestedFruits) { fruit ->
            assertEquals(setOf(Category.HARVESTING), recipes.getValue(fruit).categories, fruit)
        }

    @Test
    fun harvestedFruitTakesThreeHundredSeconds() =
        assertTable(harvestedFruits) { fruit ->
            assertEquals(300.0, recipes.getValue(fruit).time, fruit)
        }

    @Test
    fun harvestedFruitYieldsFifty() =
        assertTable(harvestedFruits) { fruit ->
            assertEquals(mapOf(Material(fruit) to 50.0), recipes.getValue(fruit).result, fruit)
        }

    @Test
    fun harvestedFruitHasNoIngredients() =
        assertTable(harvestedFruits) { fruit ->
            assertEquals(emptyMap(), recipes.getValue(fruit).ingredients, fruit)
        }

    @Test
    fun harvestedWoodIsAHarvestingRecipe() {
        val wood = recipes.getValue("wood")
        assertAll(
            { assertEquals(setOf(Category.HARVESTING), wood.categories) },
            // a tree yields the 4 wood a hand-mined tree gives
            { assertEquals(mapOf(Material("wood") to 4.0), wood.result) },
            { assertEquals(600.0, wood.time) },
            { assertEquals(emptyMap(), wood.ingredients) },
        )
    }

    /** The base game has no agricultural tower, so no wood recipe; `tree-seed` marks Space Age. */
    @Test
    fun woodIsOnlyHarvestedWithTreeSeeding() {
        val baseDump = Json.parseToJsonElement("{}") as JsonObject
        assertAll(
            { assertTrue(resourceRecipes(baseDump).none { it.name == "wood" }) },
            { assertContains(recipes, "wood") },
        )
    }

    @Test
    fun asteroidCollectorYieldsEveryChunk() {
        val recipe = recipes.getValue("asteroid-collection")
        assertAll(
            { assertEquals(setOf(Category.ASTEROID_COLLECTION), recipe.categories) },
            { assertEquals(0.2, recipe.time) },
            { assertEquals(emptyMap(), recipe.ingredients) },
            {
                assertEquals(
                    setOf(
                        Material("metallic-asteroid-chunk"),
                        Material("carbonic-asteroid-chunk"),
                        Material("oxide-asteroid-chunk"),
                        Material("promethium-asteroid-chunk"),
                    ),
                    recipe.result.keys,
                )
            },
        )
    }
}
