package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertOneBeltSourcePerMaterial
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.recipes
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
    fun advancedCircuitsImportPlasticButBuildTheCopperCable() {
        val planned = Layout("advanced-circuit").plan
        val categories = planned.recipes.flatMap { it.recipe.categories }.toSet()
        val sourceMaterials = planned.sources.map { it.material }
        val recipeNames = planned.recipes.map { it.recipe.name }

        assertAll(
            {
                assertContains(
                    sourceMaterials,
                    Material("plastic-bar"),
                    "plastic should be imported",
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
                    Material("copper-plate"),
                    "copper plate should be imported instead",
                )
            },
            {
                assertTrue(
                    categories.none {
                        it in setOf(Category.OIL_PROCESSING, Category.ORGANIC, Category.CRYOGENICS)
                    },
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
                    planned.sources.any { it.material == Material("heavy-oil") && it.fluid },
                    "sources: ${planned.sources.map { it.material }}",
                )
            },
        )
    }

    @Test
    fun aTargetMaterialWithTwoProducersStillImportsItsInputs() {
        // the optimizer splits solid fuel between the light-oil and petroleum-gas recipes, so the
        // target has no single producer; the built target recipe must still keep its fluid input
        val planned = Layout("solid-fuel").plan
        val target = planned.recipes.first { it.material == planned.target }

        assertContains(
            planned.sources.map { it.material },
            Material("light-oil"),
            "the target recipe ${target.recipe.name} consumes light oil",
        )
    }

    @Test
    fun theKovarexTargetIsBuiltOnce() {
        // kovarex consumes its own product, so treating the target as an in-plan producer would
        // add a second, belted kovarex row: 18 machines instead of the MAX_COPIES-capped 9
        val planned = Layout("uranium-235").plan
        val rows =
            planned.rows.filter { row ->
                row.machines.any { it.recipe.name == "kovarex-enrichment-process" }
            }

        assertEquals(1, rows.size)
    }

    @Test
    fun vulcanusStoneUsesTheLavaFoundryRecipe() {
        // one solid plus one fluid result passes `buildable`, so stone is now planned from
        // molten-copper-from-lava instead of being imported; pin that change so it stays visible
        val planned = Layout("stone", surface = Surface.VULCANUS).plan

        assertAll(
            { assertContains(planned.recipes.map { it.recipe.name }, "molten-copper-from-lava") },
            { assertContains(planned.sources.map { it.material }, Material("lava")) },
        )
    }

    @Test
    fun anUnbuiltMultiOutputRecipeStillReportsItsFluidAsASource() {
        // molten-iron-from-lava yields stone as well; this plan does not consume the stone, so no
        // row is built for it, but the layout still imports its molten iron. The reported sources
        // must follow the rows that are actually built, not the optimizer's producer index.
        val layout = Layout("iron-plate", surface = Surface.VULCANUS)
        val moltenIron = Material("molten-iron")

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
