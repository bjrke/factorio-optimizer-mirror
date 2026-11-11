package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertOneBeltSourcePerMaterial
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
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
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
                assertEquals(
                    emptyList(),
                    categories.filter { it == Category.OIL_PROCESSING },
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
                assertNotNull(
                    planned.sources.firstOrNull { it.material == heavyOil && it.fluid },
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
    fun theReportedUtilityScienceUrlsKeepCircuitAsABandOutput() =
        assertTableOf(
            "http://localhost:8080/#/1/utility-science-pack/CFSXbmr/1/1",
            "http://localhost:8080/#/0.66666/processing-unit/CFSXbmr/1/1",
        ) { url ->
            val layout = layoutFromUrl(url)
            val plan = layout.plan
            val plainCableOrCircuit =
                plan.rows.filter { row ->
                    row.links.isEmpty() &&
                        row.machines.first().recipe.name in
                            setOf("copper-cable", "electronic-circuit")
                }
            assertAll(
                { assertEquals(emptyList(), layout.failures, "$url failures: ${layout.failures}") },
                {
                    assertEquals(
                        emptyList(),
                        plainCableOrCircuit.map { it.machines.first().recipe.name },
                        "$url still has plain cable/circuit rows",
                    )
                },
                {
                    // cable feeds circuit directly, and the circuit machines stay an output so the
                    // robot frames can draw them
                    assertTrue(
                        plan.rows.any { row ->
                            row.links.any { it.material == copperCable } &&
                                row.machines.any { it.recipe.name == "electronic-circuit" }
                        },
                        "$url has no cable-to-circuit band",
                    )
                },
                {
                    assertTrue(
                        plan.rows.any { row -> row.links.any { it.material == advancedCircuit } },
                        "$url has no advanced-circuit-to-processing-unit band",
                    )
                },
                { assertContains(plan.beltMaterials, electronicCircuit) },
                { assertContainsNot(plan.sourceMaterials, electronicCircuit) },
                { assertOneBeltSourcePerMaterial(plan) },
            )
        }

    @Test
    fun theReportedChemicalScienceUrlKeepsCableOutOfTheBand() {
        val layout = layoutFromUrl("http://localhost:8080/#/1/chemical-science-pack/CFSXbmr/1/1")
        val plan = layout.plan
        val cableBands =
            plan.rows.filter { row ->
                row.links.isNotEmpty() && row.machines.any { copperCable in it.recipe.result.keys }
            }

        assertAll(
            { assertEquals(emptyList(), layout.failures, layout.failures.toString()) },
            // cable is produced and consumed in the belt row, not inside a band that would still
            // need a cable belt for another machine
            {
                assertEquals(emptyList(), cableBands.map { it.machines.map { m -> m.recipe.name } })
            },
            { assertContains(plan.beltMaterials, copperCable) },
            { assertContainsNot(plan.sourceMaterials, copperCable) },
            { assertOneBeltSourcePerMaterial(plan) },
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
