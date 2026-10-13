package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceTest {

    private fun pressure(min: Int, max: Int) = SurfaceCondition(SurfaceProperty.PRESSURE, min..max)

    @Test
    fun nowhereRestrictedRecipeIsAvailableEverywhere() =
        assertTable(Surface.entries) { assertTrue(it.accepts(emptyList()), "$it") }

    @Test
    fun pressureSelectsPlanet() {
        val vulcanusOnly = listOf(pressure(4000, 4000))
        assertAll(
            { assertTrue(Surface.VULCANUS.accepts(vulcanusOnly)) },
            { assertFalse(Surface.NAUVIS.accepts(vulcanusOnly)) },
            { assertFalse(Surface.GLEBA.accepts(vulcanusOnly)) },
        )
    }

    @Test
    fun pressureRangeAcceptsAquilo() {
        val aquilo = listOf(pressure(100, 600))
        assertAll(
            { assertTrue(Surface.AQUILO.accepts(aquilo)) },
            { assertFalse(Surface.NAUVIS.accepts(aquilo)) },
        )
    }

    @Test
    fun missingPropertyIsRejected() {
        val magnetic = listOf(SurfaceCondition(SurfaceProperty.MAGNETIC_FIELD, 99..Int.MAX_VALUE))
        assertAll(
            { assertTrue(Surface.FULGORA.accepts(magnetic)) },
            { assertFalse(Surface.NAUVIS.accepts(magnetic)) },
        )
    }

    @Test
    fun spaceAcceptsZeroGravity() {
        val zeroGravity = listOf(SurfaceCondition(SurfaceProperty.GRAVITY, 0..0))
        assertAll(
            { assertTrue(Surface.SPACE.accepts(zeroGravity)) },
            { assertFalse(Surface.NAUVIS.accepts(zeroGravity)) },
        )
    }

    @Test
    fun subgroupSelectsPlanet() =
        assertAll(
            { assertTrue(Surface.VULCANUS.accepts(Subgroup("vulcanus-processes"))) },
            { assertFalse(Surface.NAUVIS.accepts(Subgroup("vulcanus-processes"))) },
            { assertTrue(Surface.GLEBA.accepts(Subgroup("agriculture-products"))) },
            { assertTrue(Surface.SPACE.accepts(Subgroup("space-processing"))) },
        )

    @Test
    fun fluidRecipesAreAvailableOnEverySurface() =
        assertTable(Surface.entries) { surface ->
            assertTrue(surface.accepts(Subgroup("fluid-recipes")), "$surface")
        }

    @Test
    fun crushingOnlyInSpace() =
        assertAll(
            { assertTrue(Surface.SPACE.accepts(Category.CRUSHING)) },
            { assertFalse(Surface.NAUVIS.accepts(Category.CRUSHING)) },
            { assertFalse(Surface.VULCANUS.accepts(Category.CRUSHING)) },
        )

    @Test
    fun asteroidCollectionIsAvailableInSpace() =
        assertTrue(Surface.SPACE.accepts(Category.ASTEROID_COLLECTION))

    @Test
    fun asteroidCollectionIsNotAvailableOnPlanets() =
        assertTable(Surface.entries.filter { it != Surface.SPACE }) { surface ->
            assertFalse(surface.accepts(Category.ASTEROID_COLLECTION), "$surface")
        }

    @Test
    fun forSurfaceFiltersRecipes() {
        val global =
            Recipe(
                name = "copper-plate",
                ingredients = mapOf(Material("copper-ore") to 1.0),
                result = mapOf(Material("copper-plate") to 1.0),
                time = 1.0,
                categories = setOf(Category.SMELTING),
            )
        val conditioned =
            global.copy(
                name = "foundry",
                result = mapOf(Material("foundry") to 1.0),
                surfaceConditions = listOf(pressure(4000, 4000)),
            )
        val subgroupOnly =
            global.copy(
                name = "molten-iron-from-lava",
                result = mapOf(Material("molten-iron") to 1.0),
                subgroup = Subgroup("vulcanus-processes"),
            )
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes = listOf(global, conditioned, subgroupOnly),
            )

        assertTableOf(
            Surface.NAUVIS to listOf("copper-plate"),
            Surface.VULCANUS to listOf("copper-plate", "foundry", "molten-iron-from-lava"),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name))
        }
    }

    @Test
    fun explicitConditionsOverridePlanetSubgroup() {
        // quantum-processor is in the Aquilo subgroup but its pressure condition also allows space
        val quantumProcessor =
            Recipe(
                name = "quantum-processor",
                ingredients = emptyMap(),
                result = mapOf(Material("quantum-processor") to 1.0),
                time = 1.0,
                categories = setOf(Category.ELECTROMAGNETICS),
                subgroup = Subgroup("aquilo-processes"),
                surfaceConditions =
                    listOf(SurfaceCondition(SurfaceProperty.PRESSURE, Int.MIN_VALUE..600)),
            )
        val parsed = ParsedRecipe(materials = emptyList(), recipes = listOf(quantumProcessor))

        assertTableOf(
            Surface.AQUILO to listOf("quantum-processor"),
            Surface.SPACE to listOf("quantum-processor"),
            Surface.NAUVIS to emptyList<String>(),
            Surface.VULCANUS to emptyList<String>(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name))
        }
    }

    private fun extraction(name: String, category: Category) =
        Recipe(
            name = name,
            ingredients = emptyMap(),
            result = mapOf(Material(name) to 1.0),
            time = 1.0,
            categories = setOf(category),
        )

    @Test
    fun miningResourceIsGatedBySurface() {
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes =
                    listOf(
                        extraction("iron-ore", Category.MINING_BASIC_SOLID),
                        extraction("tungsten-ore", Category.MINING_HARD_SOLID),
                        extraction("coal", Category.MINING_BASIC_SOLID),
                        extraction("stone", Category.MINING_BASIC_SOLID),
                    ),
            )

        assertTableOf(
            Surface.NAUVIS to setOf("iron-ore", "coal", "stone"),
            Surface.VULCANUS to setOf("tungsten-ore", "coal"),
            Surface.GLEBA to setOf("stone"),
            Surface.FULGORA to emptySet<String>(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name).toSet())
        }
    }

    @Test
    fun offshorePumpFluidsAreGatedBySurface() {
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes =
                    listOf(
                        extraction("water", Category.OFFSHORE_PUMPING),
                        extraction("heavy-oil", Category.OFFSHORE_PUMPING),
                        extraction("lava", Category.OFFSHORE_PUMPING),
                        extraction("ammoniacal-solution", Category.OFFSHORE_PUMPING),
                    ),
            )

        assertTableOf(
            Surface.NAUVIS to setOf("water"),
            Surface.GLEBA to setOf("water"),
            Surface.FULGORA to setOf("heavy-oil"),
            Surface.VULCANUS to setOf("lava"),
            Surface.AQUILO to setOf("ammoniacal-solution"),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name).toSet())
        }
    }

    @Test
    fun harvestedFruitIsGatedToGleba() {
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes =
                    listOf(
                        extraction("yumako", Category.HARVESTING),
                        extraction("jellynut", Category.HARVESTING),
                    ),
            )

        assertTableOf(
            Surface.GLEBA to setOf("yumako", "jellynut"),
            Surface.NAUVIS to emptySet<String>(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name).toSet())
        }
    }

    @Test
    fun recyclingRecipesOnlySurviveOnFulgora() {
        val recycling =
            Recipe(
                name = "scrap-recycling",
                ingredients = mapOf(Material("scrap") to 1.0),
                result = mapOf(Material("iron-gear-wheel") to 1.0),
                time = 1.0,
                categories = setOf(Category.RECYCLING),
            )
        val parsed = ParsedRecipe(materials = emptyList(), recipes = listOf(recycling))

        assertAll(
            {
                assertTrue(
                    parsed.forSurface(Surface.FULGORA).recipes.any { it.name == "scrap-recycling" }
                )
            },
            {
                assertFalse(
                    parsed.forSurface(Surface.NAUVIS).recipes.any { it.name == "scrap-recycling" }
                )
            },
        )
    }

    @Test
    fun spoilingRecipesNeedTheirSourceToBeProduced() {
        val bacteria =
            Recipe(
                name = "iron-bacteria",
                ingredients = mapOf(Material("yumako-mash") to 1.0),
                result = mapOf(Material("iron-bacteria") to 1.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
                surfaceConditions = listOf(pressure(2000, 2000)),
            )
        val spoiling =
            Recipe(
                name = "iron-bacteria-spoiling",
                ingredients = mapOf(Material("iron-bacteria") to 1.0),
                result = mapOf(Material("iron-ore") to 1.0),
                time = 1.0,
                categories = setOf(Category.SPOILING),
            )
        val parsed = ParsedRecipe(materials = emptyList(), recipes = listOf(bacteria, spoiling))

        assertTableOf(
            Surface.GLEBA to setOf("iron-bacteria", "iron-bacteria-spoiling"),
            Surface.FULGORA to emptySet<String>(),
            Surface.NAUVIS to emptySet<String>(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name).toSet())
        }
    }

    @Test
    fun urlRoundTrip() =
        assertTable(Surface.entries) { surface ->
            assertEquals(surface, surfaceFromUrl(surface.toUrlParam()), "$surface")
        }

    @Test
    fun identifierRoundTrip() =
        assertTable(Surface.entries) { surface ->
            assertEquals(surface, surfaceFromUrl(surface.identifier), "$surface")
        }
}
