package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceRecipesTest {

    private val recipes = resourceRecipes(Material::of).associateBy(Recipe::name)

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
