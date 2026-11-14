package de.bjrke.factorio.optimizer.common.game.prototype

import de.bjrke.factorio.optimizer.common.fixtures.ammoniacalSolutionPumpingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.coalMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.foundryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.heavyOilPumpingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironBacteriaRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironBacteriaSpoilingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecyclingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.jellynutHarvestingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lavaPumpingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.moltenIronFromLavaRecipe
import de.bjrke.factorio.optimizer.common.fixtures.quantumProcessorRecipe
import de.bjrke.factorio.optimizer.common.fixtures.stoneMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.tungstenOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.waterPumpingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.woodHarvestingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.yumakoHarvestingRecipe
import de.bjrke.factorio.optimizer.common.game.selection.surfaceFromPlanet
import de.bjrke.factorio.optimizer.common.game.selection.surfaceFromUrl
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurfaceTest {

    private fun pressure(min: Int, max: Int) = SurfaceCondition(SurfaceProperty.PRESSURE, min..max)

    @Test
    fun nowhereRestrictedRecipeIsAvailableEverywhere() =
        assertTable(Surface.entries) { assertTrue(it.accepts(emptyList()), "$it") }

    @Test
    fun pressureSelectsPlanet() {
        val vulcanusOnly = listOf(pressure(4000, 4000))
        assertTableOf(
            Surface.VULCANUS to true,
            Surface.NAUVIS to false,
            Surface.GLEBA to false,
        ) { (surface, expected) ->
            assertEquals(expected, surface.accepts(vulcanusOnly))
        }
    }

    @Test
    fun pressureRangeAcceptsAquilo() {
        val aquilo = listOf(pressure(100, 600))
        assertTableOf(Surface.AQUILO to true, Surface.NAUVIS to false) { (surface, expected) ->
            assertEquals(expected, surface.accepts(aquilo))
        }
    }

    @Test
    fun missingPropertyIsRejected() {
        val magnetic = listOf(SurfaceCondition(SurfaceProperty.MAGNETIC_FIELD, 99..Int.MAX_VALUE))
        assertTableOf(Surface.FULGORA to true, Surface.NAUVIS to false) { (surface, expected) ->
            assertEquals(expected, surface.accepts(magnetic))
        }
    }

    @Test
    fun spaceAcceptsZeroGravity() {
        val zeroGravity = listOf(SurfaceCondition(SurfaceProperty.GRAVITY, 0..0))
        assertTableOf(Surface.SPACE to true, Surface.NAUVIS to false) { (surface, expected) ->
            assertEquals(expected, surface.accepts(zeroGravity))
        }
    }

    @Test
    fun suppliedPropertyValuesOverrideTheSurface() {
        val stillOneThousand = listOf(pressure(1000, 1000))
        assertAll(
            {
                assertTrue(
                    Surface.FULGORA.accepts(
                        stillOneThousand,
                        mapOf(SurfaceProperty.PRESSURE to 1000),
                    ),
                )
            },
            {
                assertFalse(
                    Surface.FULGORA.accepts(
                        stillOneThousand,
                        mapOf(SurfaceProperty.PRESSURE to 800),
                    ),
                )
            },
        )
    }

    @Test
    fun forSurfaceUsesTheParsedProperties() {
        val nauvisOnly =
            copperPlateSmeltingRecipe.copy(
                name = "nauvis-only",
                surfaceConditions = listOf(pressure(1000, 1000)),
            )
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes = listOf(nauvisOnly),
                surfaceProperties =
                    mapOf(Surface.FULGORA to mapOf(SurfaceProperty.PRESSURE to 800)),
            )

        assertAll(
            {
                assertEquals(
                    listOf("nauvis-only"),
                    parsed.forSurface(Surface.NAUVIS).recipes.map { it.name },
                )
            },
            { assertEquals(emptyList(), parsed.forSurface(Surface.FULGORA).recipes) },
        )
    }

    @Test
    fun subgroupSelectsPlanet() =
        assertTableOf(
            Triple(Surface.VULCANUS, Subgroup("vulcanus-processes"), true),
            Triple(Surface.NAUVIS, Subgroup("vulcanus-processes"), false),
            Triple(Surface.GLEBA, Subgroup("agriculture-products"), true),
            Triple(Surface.SPACE, Subgroup("space-processing"), true),
        ) { (surface, subgroup, expected) ->
            assertEquals(expected, surface.accepts(subgroup))
        }

    @Test
    fun fluidRecipesAreAvailableOnEverySurface() =
        assertTable(Surface.entries) { surface ->
            assertTrue(surface.accepts(Subgroup("fluid-recipes")), "$surface")
        }

    @Test
    fun crushingOnlyInSpace() =
        assertTableOf(
            Surface.SPACE to true,
            Surface.NAUVIS to false,
            Surface.VULCANUS to false,
        ) { (surface, expected) ->
            assertEquals(expected, surface.accepts(Category.CRUSHING))
        }

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
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes =
                    listOf(
                        copperPlateSmeltingRecipe,
                        foundryRecipe,
                        moltenIronFromLavaRecipe,
                    ),
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
        val parsed = ParsedRecipe(materials = emptyList(), recipes = listOf(quantumProcessorRecipe))

        assertTableOf(
            Surface.AQUILO to listOf("quantum-processor"),
            Surface.SPACE to listOf("quantum-processor"),
            Surface.NAUVIS to emptyList(),
            Surface.VULCANUS to emptyList(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name))
        }
    }

    @Test
    fun miningResourceIsGatedBySurface() {
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes =
                    listOf(
                        ironOreMiningRecipe,
                        tungstenOreMiningRecipe,
                        coalMiningRecipe,
                        stoneMiningRecipe,
                    ),
            )

        assertTableOf(
            Surface.NAUVIS to setOf("iron-ore", "coal", "stone"),
            Surface.VULCANUS to setOf("tungsten-ore", "coal"),
            Surface.GLEBA to setOf("stone"),
            Surface.FULGORA to emptySet(),
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
                        waterPumpingRecipe,
                        heavyOilPumpingRecipe,
                        lavaPumpingRecipe,
                        ammoniacalSolutionPumpingRecipe,
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
                recipes = listOf(yumakoHarvestingRecipe, jellynutHarvestingRecipe),
            )

        assertTableOf(
            Surface.GLEBA to setOf("yumako", "jellynut"),
            Surface.NAUVIS to emptySet(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name).toSet())
        }
    }

    @Test
    fun harvestedWoodIsGatedToNauvis() {
        val parsed = ParsedRecipe(materials = emptyList(), recipes = listOf(woodHarvestingRecipe))

        assertTableOf(
            Surface.NAUVIS to setOf("wood"),
            Surface.GLEBA to emptySet(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name).toSet())
        }
    }

    @Test
    fun recyclingRecipesOnlySurviveOnFulgora() {
        val parsed =
            ParsedRecipe(materials = emptyList(), recipes = listOf(ironGearWheelRecyclingRecipe))

        assertContains(
            parsed.forSurface(Surface.FULGORA).recipes.map(Recipe::name),
            "iron-gear-wheel-recycling",
        )
        assertContainsNot(
            parsed.forSurface(Surface.NAUVIS).recipes.map(Recipe::name),
            "iron-gear-wheel-recycling",
        )
    }

    @Test
    fun spoilingRecipesNeedTheirSourceToBeProduced() {
        val parsed =
            ParsedRecipe(
                materials = emptyList(),
                recipes = listOf(ironBacteriaRecipe, ironBacteriaSpoilingRecipe),
            )

        assertTableOf(
            Surface.GLEBA to setOf("iron-bacteria", "iron-bacteria-spoiling"),
            Surface.FULGORA to emptySet(),
            Surface.NAUVIS to emptySet(),
        ) { (surface, expected) ->
            assertEquals(expected, parsed.forSurface(surface).recipes.map(Recipe::name).toSet())
        }
    }

    @Test
    fun urlRoundTrip() =
        assertTable(Surface.entries.filter { it.code != null }) { surface ->
            assertEquals(surface, surfaceFromUrl(surface.code.toString()), "$surface")
        }

    @Test
    fun surfaceCodeIsFoundInsideACombinedSelectionString() =
        assertTable(Surface.entries.filter { it.code != null }) { surface ->
            val selection = "3eE${surface.code}xyzSL"
            assertEquals(surface, surfaceFromUrl(selection), "$surface")
        }

    @Test
    fun urlParsingIsNotSensitiveToPlanetIdentifiers() =
        assertAll(
            { assertNull(surfaceFromUrl(null)) },
            { assertNull(surfaceFromUrl("")) },
            { assertNull(surfaceFromUrl("xyz")) },
            // only the code characters are scanned, so Fulgora's identifier resolves to its 'g'
            { assertEquals(Surface.GLEBA, surfaceFromUrl(Surface.FULGORA.identifier)) },
        )

    @Test
    fun planetRoundTrip() =
        assertTable(Surface.entries) { surface ->
            assertEquals(surface, surfaceFromPlanet(surface.identifier), "$surface")
        }

    @Test
    fun planetAcceptsACodeToo() =
        assertTable(Surface.entries.filter { it.code != null }) { surface ->
            assertEquals(surface, surfaceFromPlanet(surface.code.toString()), "$surface")
        }

    @Test
    fun theCliNamesNauvisExplicitly() =
        assertAll(
            { assertEquals(Surface.NAUVIS, surfaceFromPlanet(Surface.NAUVIS.identifier)) },
            // Nauvis carries no URL code, but the CLI accepts the single character for it
            { assertEquals(Surface.NAUVIS, surfaceFromPlanet("n")) },
            { assertNull(surfaceFromPlanet("mars")) },
            { assertNull(surfaceFromPlanet("vu")) },
        )
}
