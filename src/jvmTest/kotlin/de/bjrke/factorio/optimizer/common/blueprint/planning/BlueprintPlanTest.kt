package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.testBlueprintPlan
import de.bjrke.factorio.optimizer.common.core.model.Dimension
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.advancedOilProcessingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.castingIronRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.recipes
import de.bjrke.factorio.optimizer.common.fixtures.rocketSilo
import de.bjrke.factorio.optimizer.common.fixtures.rocketSiloRecipe
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun production(
    recipe: Recipe,
    material: Material,
    factory: Factory,
    amount: Double = 1.0,
    level: Int = 0,
) = FactoryProduction(material, recipe, factory, amount, level)

private fun result(target: Material, targetRecipe: Recipe, productions: List<FactoryProduction>) =
    OptimizationResult(
        target = target,
        recipe = targetRecipe,
        factories = productions.associateBy { it.recipe },
        logistics = testLogistics,
    )

class BlueprintPlanTest {

    private val logistics = calculator()

    private fun plan(target: Material, targetRecipe: Recipe, productions: List<FactoryProduction>) =
        testBlueprintPlan(result(target, targetRecipe, productions), logistics)

    @Test
    fun thePlanUsesTheAmountForMachinesAndSources() {
        val gearProduction =
            production(
                ironGearWheelRecipe,
                gear,
                Factory.ASSEMBLING_MACHINE_3,
                amount = 2.0,
            )
        val planned = plan(gear, ironGearWheelRecipe, listOf(gearProduction))
        val plate = planned.sources.single { it.material == ironPlate }

        assertAll(
            {
                assertEquals(
                    2,
                    planned.rows
                        .flatMap { it.machines }
                        .count {
                            it.recipe == ironGearWheelRecipe
                        },
                )
            },
            {
                assertEquals(
                    2.0 * gearProduction.inputPerMachine(ironPlate),
                    plate.rate,
                    TOLERANCE,
                )
            },
        )
    }

    @Test
    fun theAllSciencePacksTargetIsUnavailable() {
        val smelt = ironPlateSmeltingRecipe
        val outcome =
            blueprintPlan(
                result(
                    Material(ALL_SCIENCE_PACKS),
                    smelt,
                    listOf(
                        production(smelt, Material(ALL_SCIENCE_PACKS), Factory.ELECTRIC_FURNACE),
                    ),
                ),
                logistics,
            )

        assertIs<BlueprintPlanResult.Unavailable>(outcome)
    }

    @Test
    fun aMiningRecipeBecomesASourceInsteadOfABuilding() {
        val mining = ironOreMiningRecipe
        val smelt = ironPlateSmeltingRecipe
        val craft = ironGearWheelRecipe

        val planned =
            plan(
                gear,
                craft,
                listOf(
                    production(mining, ironOre, Factory.ELECTRIC_MINING_DRILL, level = 1),
                    production(smelt, ironPlate, Factory.ELECTRIC_FURNACE, level = 2),
                    production(craft, gear, Factory.ASSEMBLING_MACHINE_3, level = 3),
                ),
            )

        // the drill is place-dependent and becomes an import; the smelter is built
        assertAll(
            { assertEquals(setOf(gear, ironPlate), planned.recipes.map { it.material }.toSet()) },
            { assertEquals(listOf(ironOre), planned.sources.map { it.material }) },
        )
    }

    @Test
    fun aFoundryRecipeAndAFurnaceRecipeAreBothKept() {
        val foundry = castingIronRecipe
        val furnace = ironPlateSmeltingRecipe
        val craft = ironGearWheelRecipe

        val foundryPlan =
            testBlueprintPlan(
                result(
                    gear,
                    craft,
                    listOf(
                        production(foundry, ironPlate, Factory.FOUNDRY, level = 2),
                        production(craft, gear, Factory.ASSEMBLING_MACHINE_3, level = 3),
                    ),
                ),
                logistics,
            )
        val furnacePlan =
            plan(
                gear,
                craft,
                listOf(
                    production(craft, gear, Factory.ASSEMBLING_MACHINE_3),
                    production(furnace, ironPlate, Factory.STEEL_FURNACE),
                ),
            )

        assertAll(
            { assertContains(foundryPlan.recipes.map { it.factory }, Factory.FOUNDRY) },
            { assertContains(furnacePlan.recipes.map { it.factory }, Factory.STEEL_FURNACE) },
            { assertEquals(listOf(ironOre), furnacePlan.sources.map { it.material }) },
        )
    }

    @Test
    fun oilProcessingIsLeftOutUnlessItIsTheTarget() {
        val refine = advancedOilProcessingRecipe
        val makeSolidFuel = solidFuelFromPetroleumGasRecipe

        val ingredientPlan =
            plan(
                solidFuel,
                makeSolidFuel,
                listOf(
                    production(makeSolidFuel, solidFuel, Factory.CHEMICAL_PLANT),
                    production(refine, petroleum, Factory.OIL_REFINERY),
                ),
            )
        val targetPlan =
            testBlueprintPlan(
                result(
                    petroleum,
                    refine,
                    listOf(production(refine, petroleum, Factory.OIL_REFINERY)),
                ),
                logistics,
            )

        assertAll(
            { assertEquals(listOf(petroleum), ingredientPlan.sources.map { it.material }) },
            { assertEquals(listOf(petroleum), targetPlan.recipes.map { it.material }) },
        )
    }

    @Test
    fun aMaterialMadeAndTakenByOneRecipeIsDirectInserted() {
        val belt = transportBeltRecipe
        val craft = ironGearWheelRecipe

        val planned =
            plan(
                transportBelt,
                belt,
                listOf(
                    production(craft, gear, Factory.ASSEMBLING_MACHINE_3, level = 1),
                    production(belt, transportBelt, Factory.ASSEMBLING_MACHINE_3, level = 2),
                ),
            )

        val band = planned.rows.single { it.links.isNotEmpty() }
        assertAll(
            {
                assertEquals(
                    setOf(belt, craft),
                    band.machines.mapTo(mutableSetOf()) { it.recipe },
                )
            },
            { assertEquals(setOf(gear), band.links.mapTo(mutableSetOf()) { it.material }) },
            { assertEquals(setOf(transportBelt), planned.beltMaterials) },
        )
    }

    @Test
    fun aMaterialOnlyLinkedInsideABandIsASourceForAnotherRow() {
        val cable = production(copperCableRecipe, copperCable, Factory.ASSEMBLING_MACHINE_3)
        val circuit =
            production(electronicCircuitRecipe, electronicCircuit, Factory.ASSEMBLING_MACHINE_3)
        val advanced =
            production(advancedCircuitRecipe, advancedCircuit, Factory.ASSEMBLING_MACHINE_3)
        val processing =
            production(processingUnitRecipe, processingUnit, Factory.ASSEMBLING_MACHINE_3)
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
        val cable = production(copperCableRecipe, copperCable, Factory.ASSEMBLING_MACHINE_3)
        val circuit =
            production(electronicCircuitRecipe, electronicCircuit, Factory.ASSEMBLING_MACHINE_3)
        val advanced =
            production(advancedCircuitRecipe, advancedCircuit, Factory.ASSEMBLING_MACHINE_3)
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
        // import at its own rate; the linked circuit machine's cable must not be added
        val sources = plannedSources(listOf(band), logistics)
        val cableSource = sources.single { it.material == copperCable }

        assertAll(
            { assertEquals(advanced.inputPerMachine(copperCable), cableSource.rate, TOLERANCE) },
            { assertContainsNot(sources.map { it.material }, electronicCircuit) },
        )
    }

    @Test
    fun productionsOfTheSameRecipeCompareByValue() {
        val craft = ironGearWheelRecipe
        val first = production(craft, gear, Factory.ASSEMBLING_MACHINE_3)
        val second = production(craft, gear, Factory.ASSEMBLING_MACHINE_3)

        assertEquals(first, second)
    }

    @Test
    fun thePlanBuildsTheRequestedMachineCount() {
        val craft = rocketSiloRecipe

        val planned =
            plan(
                rocketSilo,
                craft,
                listOf(
                    production(
                        craft,
                        rocketSilo,
                        Factory.ASSEMBLING_MACHINE_3,
                        amount = 3.0,
                    ),
                ),
            )

        assertAll(
            // even though the import would need more than two belts, the plan builds all three
            { assertEquals(3, planned.rows.single().machines.size) },
            { assertEquals("rocket-silo", planned.label) },
        )
    }

    @Test
    fun aSolidTargetShrinksToTheProductLane() {
        // copper cable: 2 per 0.5 s at assembling machine 3's 1.25 speed, so 0.2 machines per 1/s
        val cable = production(copperCableRecipe, copperCable, Factory.ASSEMBLING_MACHINE_3, 0.2)

        val planned =
            testBlueprintPlan(
                result(copperCable, copperCableRecipe, listOf(cable)),
                logistics,
                20.0,
            )

        assertAll(
            // one product lane carries 15/s, so the requested 20/s is drawn at 15/s
            { assertEquals(15.0, planned.scale, TOLERANCE) },
            { assertEquals(3, planned.rows.single().machines.size) },
            { assertEquals("copper-cable (75%, place 2x for full rate)", planned.label) },
            {
                // the sources follow the drawn three machines, not a second multiplication by 15
                assertEquals(
                    3 * cable.inputPerMachine(copperPlate),
                    planned.sources.single { it.material == copperPlate }.rate,
                    TOLERANCE,
                )
            },
        )
    }

    @Test
    fun aTargetBelowTheProductLaneKeepsTheRequestedScale() {
        val cable = production(copperCableRecipe, copperCable, Factory.ASSEMBLING_MACHINE_3, 0.2)

        val planned =
            testBlueprintPlan(
                result(copperCable, copperCableRecipe, listOf(cable)),
                logistics,
                7.5,
            )

        assertAll(
            { assertEquals(7.5, planned.scale, TOLERANCE) },
            { assertEquals(2, planned.rows.single().machines.size) },
            { assertEquals("copper-cable", planned.label) },
        )
    }

    /**
     * The lane is judged by the rate the plan must deliver, not by whole-machine output: one
     * machine making more than a lane is throttled by its belt, so the requested rate still fits.
     */
    @Test
    fun aSingleMachineOverOneLaneDoesNotShrinkBelowItsDemand() {
        // synthetic: 10 gear per craft is 12.5/s per assembling machine 3, over a yellow lane
        val highOutput =
            Recipe(
                name = "high-output",
                ingredients = emptyMap(),
                result = mapOf(gear to 10.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            )
        // 12.5/s per machine, so 0.08 machines per 1/s
        val craft = production(highOutput, gear, Factory.ASSEMBLING_MACHINE_3, 0.08)
        val slowBelt = calculator(LogisticsCalculatorSelection(belt = BeltTier.TRANSPORT_BELT))

        val planned = testBlueprintPlan(result(gear, highOutput, listOf(craft)), slowBelt, 5.0)

        assertAll(
            // 5/s fits the 7.5/s yellow lane even though the one machine could make 12.5/s
            { assertEquals(5.0, planned.scale, TOLERANCE) },
            { assertEquals(1, planned.rows.single().machines.size) },
            { assertEquals("iron-gear-wheel", planned.label) },
        )
    }

    @Test
    fun anOverCapacityIntermediateIsDirectFedInsteadOfShrinking() {
        // at 6/s the circuit row needs 18/s of cable, over one 15/s lane, but a band can insert it
        val circuit =
            production(
                electronicCircuitRecipe,
                electronicCircuit,
                Factory.ASSEMBLING_MACHINE_3,
                0.4,
            )
        val cable = production(copperCableRecipe, copperCable, Factory.ASSEMBLING_MACHINE_3, 1.5)

        val planned =
            testBlueprintPlan(
                result(electronicCircuit, electronicCircuitRecipe, listOf(cable, circuit)),
                logistics,
                6.0,
            )

        assertAll(
            { assertEquals(6.0, planned.scale, TOLERANCE) },
            { assertEquals("electronic-circuit", planned.label) },
            { assertContainsNot(planned.beltMaterials, copperCable) },
            { assertContains(planned.rows.flatMap { it.links }.map { it.material }, copperCable) },
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
