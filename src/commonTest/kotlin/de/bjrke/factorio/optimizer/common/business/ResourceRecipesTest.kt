package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ResourceRecipesTest {

    private val dump =
        Json.parseToJsonElement(
                """
                {
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
                """
            )
            .let { it as JsonObject }

    private val recipes = resourceRecipes(dump, Material::of).associateBy(Recipe::name)

    @Test
    fun basicSolidResource() {
        val iron = recipes.getValue("iron-ore")
        assertEquals(Category.MINING_BASIC_SOLID, iron.category)
        assertEquals(1.0, iron.time)
        assertEquals(mapOf(Material("iron-ore") to 1.0), iron.result)
        assertTrue(iron.ingredients.isEmpty())
    }

    @Test
    fun hardSolidResource() {
        val tungsten = recipes.getValue("tungsten-ore")
        assertEquals(Category.MINING_HARD_SOLID, tungsten.category)
        assertEquals(5.0, tungsten.time)
    }

    @Test
    fun fluidResourceUsesPumpjackAmount() {
        val oil = recipes.getValue("crude-oil")
        assertEquals(Category.MINING_FLUID, oil.category)
        assertEquals(mapOf(Material("crude-oil") to 10.0), oil.result)
    }

    @Test
    fun resourceCanRequireFluid() {
        val uranium = recipes.getValue("uranium-ore")
        assertEquals(mapOf(Material("sulfuric-acid") to 10.0), uranium.ingredients)
        assertEquals(2.0, uranium.time)
    }

    @Test
    fun geyserAndVentProduceDifferentFluid() {
        assertEquals(
            mapOf(Material("sulfuric-acid") to 10.0),
            recipes.getValue("sulfuric-acid-geyser").result,
        )
        assertEquals(mapOf(Material("fluorine") to 1.0), recipes.getValue("fluorine-vent").result)
    }

    @Test
    fun offshorePumpFluids() {
        listOf("water", "heavy-oil", "lava", "ammoniacal-solution").forEach {
            assertEquals(Category.OFFSHORE_PUMPING, recipes.getValue(it).category)
        }
    }

    @Test
    fun harvestedFruit() {
        listOf("yumako", "jellynut").forEach { fruit ->
            val recipe = recipes.getValue(fruit)
            assertEquals(Category.HARVESTING, recipe.category)
            assertEquals(300.0, recipe.time)
            assertEquals(mapOf(Material(fruit) to 50.0), recipe.result)
            assertTrue(recipe.ingredients.isEmpty())
        }
    }

    @Test
    fun asteroidCollectorYieldsEveryChunk() {
        val recipe = recipes.getValue("asteroid-collection")
        assertEquals(Category.ASTEROID_COLLECTION, recipe.category)
        assertEquals(0.2, recipe.time)
        assertTrue(recipe.ingredients.isEmpty())
        assertEquals(
            setOf(
                Material("metallic-asteroid-chunk"),
                Material("carbonic-asteroid-chunk"),
                Material("oxide-asteroid-chunk"),
                Material("promethium-asteroid-chunk"),
            ),
            recipe.result.keys,
        )
    }
}
