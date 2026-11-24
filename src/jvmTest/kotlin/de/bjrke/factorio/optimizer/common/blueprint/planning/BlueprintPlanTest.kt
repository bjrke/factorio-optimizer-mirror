package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.testBlueprintPlan
import de.bjrke.factorio.optimizer.common.core.model.Dimension
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.advancedOilProcessingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calcite
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.castingIronRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.recipes
import de.bjrke.factorio.optimizer.common.fixtures.rocketSiloRecipe
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class BlueprintPlanTest : BaseGameCtxTest() {

    private val logistics = calculator()

    private fun plan(vararg productions: FactoryProduction) =
        testBlueprintPlan(optimizationResult(*productions, logistics = testLogistics), logistics)

    @Test
    fun thePlanUsesTheAmountForMachinesAndSources() {
        val gearProduction = production(ironGearWheelRecipe, amount = 2.0)
        val planned = plan(gearProduction)

        assertAll(
            {
                assertEquals(
                    2,
                    planned.rows
                        .flatMap { it.machines }
                        .count { it.recipe.recipe == ironGearWheelRecipe },
                )
            },
            { assertContains(planned.sources.map { it.material }, ironPlate) },
        )
    }

    @Test
    fun theAllSciencePacksTargetIsUnavailable() {
        val smelt = ironPlateSmeltingRecipe
        val allSciencePacks = Material(ALL_SCIENCE_PACKS)
        val outcome =
            blueprintPlan(
                optimizationResult(
                    production(smelt, Factory.ELECTRIC_FURNACE, allSciencePacks),
                    logistics = testLogistics,
                ),
                logistics,
            )

        assertIs<BlueprintPlanResult.Unavailable>(outcome)
    }

    /**
     * A material no recipe produces is the optimizer's `Category.IMPORT` pseudo source. Laying it
     * out would put an infinity chest in the target row with the material as its recipe.
     */
    @Test
    fun anImportTargetIsUnavailable() {
        val imported =
            Recipe(
                name = calcite.name,
                ingredients = emptyMap(),
                result = mapOf(calcite to 1.0),
                time = 1.0,
                categories = setOf(Category.IMPORT),
            )

        val outcome =
            blueprintPlan(
                optimizationResult(
                    production(imported, Factory.NO_FACTORY),
                    logistics = testLogistics,
                ),
                logistics,
            )

        val unavailable = assertIs<BlueprintPlanResult.Unavailable>(outcome)
        assertContains(unavailable.reason, calcite.name)
    }

    /** Only the target recipe decides availability; an imported ingredient is still a source. */
    @Test
    fun anImportedIngredientDoesNotMakeTheTargetUnavailable() {
        val oreImport =
            Recipe(
                name = ironOre.name,
                ingredients = emptyMap(),
                result = mapOf(ironOre to 1.0),
                time = 1.0,
                categories = setOf(Category.IMPORT),
            )

        val planned =
            plan(
                production(ironGearWheelRecipe),
                production(oreImport, Factory.NO_FACTORY),
                production(ironPlateSmeltingRecipe, Factory.ELECTRIC_FURNACE),
            )

        assertContains(planned.sources.map { it.material }, ironOre)
    }

    @Test
    fun aMiningRecipeBecomesASourceInsteadOfABuilding() {
        val planned =
            plan(
                production(ironGearWheelRecipe, level = 3),
                production(ironOreMiningRecipe, level = 1),
                production(ironPlateSmeltingRecipe, level = 2),
            )

        // the drill is place-dependent and becomes an import; the smelter is built
        assertAll(
            { assertEquals(setOf(gear, ironPlate), planned.recipes.map { it.material }.toSet()) },
            { assertEquals(listOf(ironOre), planned.sources.map { it.material }) },
        )
    }

    @Test
    fun aFoundryRecipeAndAFurnaceRecipeAreBothKept() {

        val foundryPlan =
            testBlueprintPlan(
                optimizationResult(
                    production(ironGearWheelRecipe, level = 3),
                    production(castingIronRecipe, Factory.FOUNDRY, level = 2),
                    logistics = testLogistics,
                ),
                logistics,
            )
        val furnacePlan =
            plan(
                production(ironGearWheelRecipe),
                production(ironPlateSmeltingRecipe, Factory.STEEL_FURNACE),
            )

        assertAll(
            { assertContains(foundryPlan.recipes.map { it.factory }, Factory.FOUNDRY) },
            { assertContains(furnacePlan.recipes.map { it.factory }, Factory.STEEL_FURNACE) },
            { assertEquals(listOf(ironOre), furnacePlan.sources.map { it.material }) },
        )
    }

    @Test
    fun oilProcessingIsLeftOutUnlessItIsTheTarget() {
        val advOilProcessing =
            production(advancedOilProcessingRecipe, Factory.OIL_REFINERY, petroleum)
        val ingredientPlan =
            plan(
                production(solidFuelFromPetroleumGasRecipe, Factory.CHEMICAL_PLANT),
                advOilProcessing,
            )
        val targetPlan =
            testBlueprintPlan(
                optimizationResult(advOilProcessing, logistics = testLogistics),
                logistics,
            )

        assertAll(
            { assertEquals(listOf(petroleum), ingredientPlan.sources.map { it.material }) },
            { assertEquals(listOf(petroleum), targetPlan.recipes.map { it.material }) },
        )
    }

    @Test
    fun aMaterialOnlyLinkedInsideABandIsASourceForAnotherRow() {
        val cable = production(copperCableRecipe)
        val circuit = production(electronicCircuitRecipe)
        val advanced = production(advancedCircuitRecipe)
        val processing = production(processingUnitRecipe)
        val band =
            PlannedRow(
                machines = listOf(cable, circuit, advanced),
                links =
                    listOf(
                        PlannedLink(0, copperCable, 1.0, producerIsLeft = true),
                        PlannedLink(1, electronicCircuit, 1.0, producerIsLeft = true),
                    ),
            )

        // the band inserts circuit internally; the processing row still needs a belt source
        val sources = plannedSources(listOf(band, PlannedRow.copies(processing, 1)), logistics)

        assertContains(sources.map { it.material }, electronicCircuit)
    }

    @Test
    fun aPartialLinkStillImportsTheMaterialItsOtherMachinesNeed() {
        val cable = production(copperCableRecipe)
        val circuit = production(electronicCircuitRecipe)
        val advanced = production(advancedCircuitRecipe)
        val band =
            PlannedRow(
                machines = listOf(advanced, circuit, cable),
                links =
                    listOf(
                        PlannedLink(0, electronicCircuit, 1.0, producerIsLeft = false),
                        PlannedLink(1, copperCable, 1.0, producerIsLeft = false),
                    ),
            )

        // the cable link reaches only the circuit machine, so the advanced machine still needs an
        // import of its own; the linked circuit machine's cable must not be added
        val sources = plannedSources(listOf(band), logistics)

        assertAll(
            { assertContains(sources.map { it.material }, copperCable) },
            { assertContainsNot(sources.map { it.material }, electronicCircuit) },
        )
    }

    @Test
    fun productionsOfTheSameRecipeCompareByValue() {
        val first = production(ironGearWheelRecipe)
        val second = production(ironGearWheelRecipe)

        assertNotSame(first, second)
        assertEquals(first, second)
    }

    @Test
    fun thePlanBuildsTheRequestedMachineCount() {
        val planned = plan(production(rocketSiloRecipe, amount = 3.0))

        assertAll(
            // even though the import would need more than two belts, the plan builds all three
            { assertEquals(3, planned.rows.single().machines.size) },
            { assertEquals("rocket-silo", planned.label) },
        )
    }

    @Test
    fun machineFootprintsMatchThePrototypes() =
        assertTableOf(
            Factory.RECYCLER to Dimension(4, 2),
            Factory.ELECTROMAGNETIC_PLANT to Dimension(4, 4),
            Factory.FOUNDRY to Dimension(5, 5),
            Factory.ASSEMBLING_MACHINE_3 to Dimension(3, 3),
            Factory.STONE_FURNACE to Dimension(2, 2),
        ) { (factory, expected) ->
            assertEquals(expected, blueprintMachineFootprint(factory))
        }
}
