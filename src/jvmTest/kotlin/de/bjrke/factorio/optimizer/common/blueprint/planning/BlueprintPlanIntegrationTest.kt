package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertOneBeltSourcePerMaterial
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.heavyOil
import de.bjrke.factorio.optimizer.common.fixtures.lightOil
import de.bjrke.factorio.optimizer.common.fixtures.moltenIron
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.recipes
import de.bjrke.factorio.optimizer.common.fixtures.stone
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which recipes a real optimization result reduces to, and which materials it imports. */
class BlueprintPlanIntegrationTest {

    @Test
    fun advancedCircuitsBuildTheirPlasticAndCopperCable() {
        // plastic is a simple chemistry recipe, so it is built; its oil chain stays imported as
        // petroleum gas instead of planning an oil refinery
        val planned = Layout("advanced-circuit").plan
        val categories = planned.recipes.flatMap { it.recipe.categories }.toSet()
        val sourceMaterials = planned.sources.map { it.material }
        val recipeNames = planned.recipes.map { it.recipe.name }

        assertAll(
            {
                assertContains(
                    recipeNames,
                    "plastic-bar",
                    "plastic should be built from the imported petroleum gas",
                )
            },
            {
                assertContains(
                    recipeNames,
                    "copper-cable",
                    "copper cable should be built",
                )
            },
            {
                assertContainsNot(
                    sourceMaterials,
                    copperCable,
                    "copper cable should not be imported",
                )
            },
            {
                assertContains(
                    sourceMaterials,
                    copperPlate,
                    "copper plate should be imported instead",
                )
            },
            { assertContains(sourceMaterials, petroleum, "plastic's fluid input is imported") },
            {
                assertTrue(
                    categories.none { it == Category.OIL_PROCESSING },
                    "the oil chain should be cut off, categories: $categories",
                )
            },
        )
    }

    @Test
    fun lubricantIsBuiltAndItsHeavyOilIsImported() {
        val planned = Layout("lubricant").plan

        assertAll(
            { assertContains(planned.recipes.map { it.recipe.name }, "lubricant") },
            {
                assertTrue(
                    planned.sources.any { it.material == heavyOil && it.fluid },
                    "sources: ${planned.sources.map { it.material }}",
                )
            },
        )
    }

    @Test
    fun aSolidFuelTargetStillImportsItsLightOil() {
        // only the light-oil solid-fuel recipe is planned, and its fluid input stays an import
        val planned = Layout("solid-fuel").plan

        assertContains(
            planned.sources.map { it.material },
            lightOil,
        )
    }

    @Test
    fun uranium235IsBuiltByProcessingNotKovarex() {
        // kovarex is discarded even as the target's producer; uranium-processing is the allowed way
        val planned = Layout("uranium-235").plan
        val recipes = planned.recipes.map { it.recipe.name }

        assertAll(
            { assertContains(recipes, "uranium-processing") },
            { assertContainsNot(recipes, "kovarex-enrichment-process") },
        )
    }

    @Test
    fun nutrientsAreNotBredFromFish() {
        // both fish recipes are banned in the optimizer, so the real plan must not contain the
        // cycle
        val recipes = Layout("nutrients").plan.recipes.map { it.recipe.name }

        assertAll(
            { assertContainsNot(recipes, "fish-breeding") },
            { assertContainsNot(recipes, "nutrients-from-fish") },
        )
    }

    @Test
    fun vulcanusStoneIsImported() {
        // stone is a byproduct of the molten-metal recipes, so it is not a buildable row: a
        // stone-brick plan imports it instead of running a lava foundry
        val planned = Layout("stone-brick", surface = Surface.VULCANUS).plan

        assertAll(
            {
                assertContainsNot(
                    planned.recipes.map { it.recipe.name },
                    "molten-copper-from-lava",
                )
            },
            { assertContains(planned.sources.map { it.material }, stone) },
        )
    }

    @Test
    fun rocketFuelUsesTheLightOilSolidFuelRecipe() {
        // only the light-oil recipe is planned, so solid fuel is built and light oil is the single
        // source instead of solid fuel being direct-fed from one of several producers
        val planned = Layout("rocket-fuel").plan

        assertAll(
            {
                assertContains(
                    planned.recipes.map { it.recipe.name },
                    "solid-fuel-from-light-oil",
                )
            },
            {
                assertContainsNot(
                    planned.recipes.map { it.recipe.name },
                    "solid-fuel-from-heavy-oil",
                )
            },
            {
                assertContainsNot(
                    planned.recipes.map { it.recipe.name },
                    "solid-fuel-from-petroleum-gas",
                )
            },
            {
                assertEquals(
                    listOf(lightOil),
                    planned.sources.map { it.material },
                )
            },
        )
    }

    @Test
    fun aquiloSolidFuelUsesAmmonia() {
        // Aquilo is the one surface where the ammonia recipe replaces the oil-based source
        val planned = Layout("solid-fuel", surface = Surface.AQUILO).plan

        assertAll(
            {
                assertContains(
                    planned.recipes.map { it.recipe.name },
                    "solid-fuel-from-ammonia",
                )
            },
            {
                assertContainsNot(
                    planned.recipes.map { it.recipe.name },
                    "solid-fuel-from-light-oil",
                )
            },
            {
                assertContainsNot(
                    planned.recipes.map { it.recipe.name },
                    "solid-fuel-from-petroleum-gas",
                )
            },
        )
    }

    @Test
    fun anUnbuiltMultiOutputRecipeStillReportsItsFluidAsASource() {
        // molten-iron-from-lava yields stone as well; this plan does not consume the stone, so no
        // row is built for it, but the layout still imports its molten iron. The reported sources
        // must follow the rows that are actually built, not the optimizer's producer index.
        val layout = Layout("iron-plate", surface = Surface.VULCANUS)

        assertAll(
            { assertContains(layout.plan.sources.map { it.material }, moltenIron) },
            {
                assertContains(
                    layout.inputPipes.values,
                    moltenIron,
                    "the layout imports molten iron",
                )
            },
        )
    }

    @Test
    fun theReportedLogisticSciencePackUrlBuildsNoRedundantFactory() {
        val plan =
            layoutFromUrl("http://localhost:8080/#/2.5/logistic-science-pack/CFSXbmr/1/1").plan
        val machines = plan.rows.flatMap { it.machines }.groupingBy { it.recipe.name }.eachCount()

        assertAll(
            { assertOneBeltSourcePerMaterial(plan) },
            // cable + EC and gear + inserter are the direct-feed bands; the inserter band's output
            // already feeds the science row, so no second EC or inserter factory is built, and the
            // foundry takes a single belt-fed gear machine instead of one per whole consumer
            // machine
            {
                assertEquals(
                    mapOf(
                        "copper-cable" to 1,
                        "electronic-circuit" to 1,
                        "iron-gear-wheel" to 2,
                        "inserter" to 1,
                        "transport-belt" to 1,
                        "logistic-science-pack" to 9,
                    ),
                    machines,
                )
            },
        )
    }
}
