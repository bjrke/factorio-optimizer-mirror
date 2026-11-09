package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.carbonicAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.crudeOil
import de.bjrke.factorio.optimizer.common.fixtures.flourine
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.metallicAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.oxideAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.promethiumAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.wood
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
            { assertEquals(mapOf(ironOre to 1.0), iron.result) },
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
        val oilRecipe = recipes.getValue("crude-oil")
        assertAll(
            { assertEquals(setOf(Category.MINING_FLUID), oilRecipe.categories) },
            { assertEquals(mapOf(crudeOil to 10.0), oilRecipe.result) },
        )
    }

    @Test
    fun resourceCanRequireFluid() {
        val uranium = recipes.getValue("uranium-ore")
        assertAll(
            { assertEquals(mapOf(sulfuricAcid to 10.0), uranium.ingredients) },
            { assertEquals(2.0, uranium.time) },
        )
    }

    @Test
    fun geyserAndVentProduceDifferentFluid() =
        assertAll(
            {
                assertEquals(
                    mapOf(sulfuricAcid to 10.0),
                    recipes.getValue("sulfuric-acid-geyser").result,
                )
            },
            {
                assertEquals(
                    mapOf(flourine to 1.0),
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
        val woodRecipe = recipes.getValue("wood")
        assertAll(
            { assertEquals(setOf(Category.HARVESTING), woodRecipe.categories) },
            // a tree yields the 4 wood a hand-mined tree gives
            { assertEquals(mapOf(wood to 4.0), woodRecipe.result) },
            { assertEquals(600.0, woodRecipe.time) },
            { assertEquals(emptyMap(), woodRecipe.ingredients) },
        )
    }

    /** The base game has no agricultural tower, so no wood recipe; `tree-seed` marks Space Age. */
    @Test
    fun woodIsOnlyHarvestedWithTreeSeeding() {
        val baseDump = Json.parseToJsonElement("{}") as JsonObject
        assertAll(
            { assertEquals(emptyList(), resourceRecipes(baseDump).filter { it.name == "wood" }) },
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
                        metallicAsteroidChunk,
                        carbonicAsteroidChunk,
                        oxideAsteroidChunk,
                        promethiumAsteroidChunk,
                    ),
                    recipe.result.keys,
                )
            },
        )
    }
}
