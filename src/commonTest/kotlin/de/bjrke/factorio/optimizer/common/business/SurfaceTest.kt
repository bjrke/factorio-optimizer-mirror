package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.SurfaceCondition
import de.bjrke.factorio.optimizer.common.model.SurfaceProperty
import de.bjrke.factorio.optimizer.common.model.forSurface
import de.bjrke.factorio.optimizer.common.model.surfaceFromUrl
import de.bjrke.factorio.optimizer.common.model.toUrlParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceTest {

    private fun pressure(min: Int, max: Int) = SurfaceCondition(SurfaceProperty.PRESSURE, min..max)

    @Test
    fun nowhereRestrictedRecipeIsAvailableEverywhere() =
        Surface.entries.forEach { assertTrue(it.accepts(emptyList())) }

    @Test
    fun pressureSelectsPlanet() {
        val vulcanusOnly = listOf(pressure(4000, 4000))
        assertTrue(Surface.VULCANUS.accepts(vulcanusOnly))
        assertFalse(Surface.NAUVIS.accepts(vulcanusOnly))
        assertFalse(Surface.GLEBA.accepts(vulcanusOnly))
    }

    @Test
    fun pressureRangeAcceptsAquilo() {
        val aquilo = listOf(pressure(100, 600))
        assertTrue(Surface.AQUILO.accepts(aquilo))
        assertFalse(Surface.NAUVIS.accepts(aquilo))
    }

    @Test
    fun missingPropertyIsRejected() {
        val magnetic = listOf(SurfaceCondition(SurfaceProperty.MAGNETIC_FIELD, 99..Int.MAX_VALUE))
        assertTrue(Surface.FULGORA.accepts(magnetic))
        assertFalse(Surface.NAUVIS.accepts(magnetic))
    }

    @Test
    fun spaceAcceptsZeroGravity() {
        val zeroGravity = listOf(SurfaceCondition(SurfaceProperty.GRAVITY, 0..0))
        assertTrue(Surface.SPACE.accepts(zeroGravity))
        assertFalse(Surface.NAUVIS.accepts(zeroGravity))
    }

    @Test
    fun subgroupSelectsPlanet() {
        assertTrue(Surface.VULCANUS.accepts(Subgroup("vulcanus-processes")))
        assertFalse(Surface.NAUVIS.accepts(Subgroup("vulcanus-processes")))
        assertTrue(Surface.GLEBA.accepts(Subgroup("agriculture-products")))
        assertTrue(Surface.SPACE.accepts(Subgroup("space-processing")))
        Surface.entries.forEach { assertTrue(it.accepts(Subgroup("fluid-recipes"))) }
    }

    @Test
    fun crushingOnlyInSpace() {
        assertTrue(Surface.SPACE.accepts(Category.CRUSHING))
        assertFalse(Surface.NAUVIS.accepts(Category.CRUSHING))
        assertFalse(Surface.VULCANUS.accepts(Category.CRUSHING))
    }

    @Test
    fun asteroidCollectionOnlyInSpace() {
        assertTrue(Surface.SPACE.accepts(Category.ASTEROID_COLLECTION))
        Surface.entries
            .filter { it != Surface.SPACE }
            .forEach {
                assertFalse(it.accepts(Category.ASTEROID_COLLECTION), "$it")
            }
    }

    @Test
    fun forSurfaceFiltersRecipes() {
        val global =
            Recipe(
                name = "copper-plate",
                ingredients = mapOf(Material("copper-ore") to 1.0),
                result = mapOf(Material("copper-plate") to 1.0),
                time = 1.0,
                category = Category.SMELTING,
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

        assertEquals(
            listOf("copper-plate"),
            parsed.forSurface(Surface.NAUVIS).recipes.map(Recipe::name),
        )
        assertEquals(
            listOf("copper-plate", "foundry", "molten-iron-from-lava"),
            parsed.forSurface(Surface.VULCANUS).recipes.map(Recipe::name),
        )
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
                category = Category.ELECTROMAGNETICS,
                subgroup = Subgroup("aquilo-processes"),
                surfaceConditions =
                    listOf(SurfaceCondition(SurfaceProperty.PRESSURE, Int.MIN_VALUE..600)),
            )
        val parsed = ParsedRecipe(materials = emptyList(), recipes = listOf(quantumProcessor))

        fun names(surface: Surface) = parsed.forSurface(surface).recipes.map(Recipe::name)

        assertEquals(listOf("quantum-processor"), names(Surface.AQUILO))
        assertEquals(listOf("quantum-processor"), names(Surface.SPACE))
        assertTrue(names(Surface.NAUVIS).isEmpty())
        assertTrue(names(Surface.VULCANUS).isEmpty())
    }

    private fun extraction(name: String, category: Category) =
        Recipe(
            name = name,
            ingredients = emptyMap(),
            result = mapOf(Material(name) to 1.0),
            time = 1.0,
            category = category,
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

        fun names(surface: Surface) = parsed.forSurface(surface).recipes.map(Recipe::name).toSet()

        assertEquals(setOf("iron-ore", "coal", "stone"), names(Surface.NAUVIS))
        assertEquals(setOf("tungsten-ore", "coal"), names(Surface.VULCANUS))
        assertEquals(setOf("stone"), names(Surface.GLEBA))
        assertEquals(emptySet(), names(Surface.FULGORA))
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

        fun names(surface: Surface) = parsed.forSurface(surface).recipes.map(Recipe::name).toSet()

        assertEquals(setOf("water"), names(Surface.NAUVIS))
        assertEquals(setOf("water"), names(Surface.GLEBA))
        assertEquals(setOf("heavy-oil"), names(Surface.FULGORA))
        assertEquals(setOf("lava"), names(Surface.VULCANUS))
        assertEquals(setOf("ammoniacal-solution"), names(Surface.AQUILO))
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

        fun names(surface: Surface) = parsed.forSurface(surface).recipes.map(Recipe::name).toSet()

        assertEquals(setOf("yumako", "jellynut"), names(Surface.GLEBA))
        assertEquals(emptySet(), names(Surface.NAUVIS))
    }

    @Test
    fun urlRoundTrip() =
        Surface.entries.forEach {
            val url = it.toUrlParam()
            assertEquals(it, surfaceFromUrl(url))
            assertEquals(it, surfaceFromUrl(it.identifier))
        }
}
