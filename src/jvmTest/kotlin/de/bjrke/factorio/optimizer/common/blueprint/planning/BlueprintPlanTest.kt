package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.plan.FluidPort
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.MAX_BLUEPRINT_RECIPES
import de.bjrke.factorio.optimizer.common.plan.PlannedRecipe
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import de.bjrke.factorio.optimizer.common.service.OptimizationResult
import de.bjrke.factorio.optimizer.common.service.fixtures.testLogistics
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val ore = Material("iron-ore")
private val plate = Material("iron-plate")
private val gear = Material("iron-gear-wheel")
private val petroleum = Material("petroleum-gas")
private val discovery = Material("utility-science-pack")

private fun recipe(
    name: String,
    category: Category,
    result: Map<Material, Double>,
    ingredients: Map<Material, Double> = emptyMap(),
    time: Double = 1.0,
) = Recipe(name, ingredients, result, time, setOf(category))

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

    private val logistics =
        LogisticsCalculator(
            testLogistics,
            BeltTier.EXPRESS_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 1,
            beltStack = 1,
            inserterStack = 1,
        )

    private fun plan(target: Material, targetRecipe: Recipe, productions: List<FactoryProduction>) =
        blueprintPlan(result(target, targetRecipe, productions), logistics)

    @Test
    fun theAllSciencePacksTargetIsUnavailable() {
        val smelt = recipe("iron-plate", Category.SMELTING, mapOf(plate to 1.0))
        val outcome =
            plan(
                Material(ALL_SCIENCE_PACKS),
                smelt,
                listOf(production(smelt, Material(ALL_SCIENCE_PACKS), Factory.ELECTRIC_FURNACE)),
            )
        assertIs<BlueprintPlanResult.Unavailable>(outcome)
    }

    @Test
    fun aMiningRecipeBecomesASourceInsteadOfABuilding() {
        val mining = recipe("iron-ore", Category.MINING_BASIC_SOLID, mapOf(ore to 1.0))
        val smelt = recipe("iron-plate", Category.SMELTING, mapOf(plate to 1.2), mapOf(ore to 1.0))
        val craft =
            recipe("iron-gear-wheel", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 2.0))

        val outcome =
            plan(
                gear,
                craft,
                listOf(
                    production(mining, ore, Factory.ELECTRIC_MINING_DRILL, level = 1),
                    production(smelt, plate, Factory.ELECTRIC_FURNACE, level = 2),
                    production(craft, gear, Factory.ASSEMBLING_MACHINE_3, level = 3),
                ),
            )

        val planned = assertIs<BlueprintPlanResult.Planned>(outcome).plan
        // the drill is place-dependent and the smelter is left out; both become imports
        assertAll(
            { assertEquals(listOf(gear), planned.recipes.map { it.production.material }) },
            { assertEquals(listOf(plate), planned.sources.map { it.material }) },
        )
    }

    @Test
    fun aFoundryRecipeIsKeptWhileAFurnaceRecipeIsNot() {
        val foundry =
            recipe("molten-iron", Category.METALLURGY, mapOf(plate to 1.0), mapOf(ore to 1.0))
        val furnace = recipe("iron-plate", Category.SMELTING, mapOf(plate to 1.0))
        val craft =
            recipe("iron-gear-wheel", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 2.0))

        val kept =
            blueprintPlan(
                result(
                    gear,
                    craft,
                    listOf(
                        production(foundry, plate, Factory.FOUNDRY, level = 2),
                        production(craft, gear, Factory.ASSEMBLING_MACHINE_3, level = 3),
                    ),
                ),
                logistics,
            )
        val excluded =
            plan(
                gear,
                craft,
                listOf(
                    production(craft, gear, Factory.ASSEMBLING_MACHINE_3),
                    production(furnace, plate, Factory.STEEL_FURNACE),
                ),
            )

        val keptPlan = assertIs<BlueprintPlanResult.Planned>(kept).plan
        val excludedPlan = assertIs<BlueprintPlanResult.Planned>(excluded).plan
        assertAll(
            { assertContains(keptPlan.recipes.map { it.factory }, Factory.FOUNDRY) },
            // the furnace plan has only the gear recipe left; the plate is imported
            { assertEquals(listOf(plate), excludedPlan.sources.map { it.material }) },
        )
    }

    @Test
    fun oilProcessingIsLeftOutUnlessItIsTheTarget() {
        val refine =
            recipe("advanced-oil-processing", Category.OIL_PROCESSING, mapOf(petroleum to 1.0))
        val plastic =
            recipe("plastic-bar", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(petroleum to 2.0))

        val asIngredient =
            plan(
                gear,
                plastic,
                listOf(
                    production(plastic, gear, Factory.CHEMICAL_PLANT),
                    production(refine, petroleum, Factory.OIL_REFINERY),
                ),
            )
        val asTarget =
            blueprintPlan(
                result(
                    petroleum,
                    refine,
                    listOf(production(refine, petroleum, Factory.OIL_REFINERY)),
                ),
                logistics,
            )

        val ingredientPlan = assertIs<BlueprintPlanResult.Planned>(asIngredient).plan
        val targetPlan = assertIs<BlueprintPlanResult.Planned>(asTarget).plan
        assertAll(
            { assertEquals(listOf(petroleum), ingredientPlan.sources.map { it.material }) },
            { assertEquals(listOf(petroleum), targetPlan.recipes.map { it.production.material }) },
        )
    }

    @Test
    fun atMostTheRecipeLimitIsBuilt() {
        val materials = (0..12).map { Material("step-$it") }
        val targetRecipe =
            recipe("target", Category.CRAFTING, mapOf(discovery to 1.0), mapOf(materials[0] to 1.0))
        val chain =
            (0 until 12).map { index ->
                production(
                    recipe(
                        "step-$index",
                        Category.CRAFTING,
                        mapOf(materials[index] to 1.0),
                        mapOf(materials[index + 1] to 1.0),
                    ),
                    materials[index],
                    Factory.ASSEMBLING_MACHINE_3,
                    level = 12 - index,
                )
            }

        val outcome =
            plan(
                discovery,
                targetRecipe,
                chain + production(targetRecipe, discovery, Factory.SCIENCE_LAB, level = 99),
            )

        val planned = assertIs<BlueprintPlanResult.Planned>(outcome).plan
        // the cap counts every built recipe, not the planned recipes a recipe may be split into
        assertEquals(MAX_BLUEPRINT_RECIPES, planned.recipes.map { it.recipe }.distinct().size)
    }

    @Test
    fun aMaterialMadeAndTakenByOneRecipeIsDirectInserted() {
        val smelt = recipe("iron-plate", Category.SMELTING, mapOf(plate to 1.0), mapOf(ore to 1.0))
        val craft =
            recipe("iron-gear-wheel", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 2.0))

        val outcome =
            plan(
                gear,
                craft,
                listOf(
                    production(smelt, plate, Factory.ASSEMBLING_MACHINE_3, level = 1),
                    production(craft, gear, Factory.ASSEMBLING_MACHINE_3, level = 2),
                ),
            )

        val planned = assertIs<BlueprintPlanResult.Planned>(outcome).plan
        val band = planned.bands.single()
        assertAll(
            // the one gear machine takes its plates straight from the two smelters beside it
            {
                assertEquals(
                    setOf(smelt, craft),
                    band.machines.mapTo(mutableSetOf()) { it.recipe },
                )
            },
            { assertEquals(setOf(plate), band.links.mapTo(mutableSetOf()) { it.material }) },
            // the plate only moves machine to machine, so it never becomes a belt source
            { assertEquals(listOf(gear), planned.beltMaterials) },
        )
    }

    @Test
    fun plannedRecipesOfTheSameRecipeCompareByValue() {
        val craft =
            recipe("iron-gear-wheel", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 2.0))
        val first = PlannedRecipe(production(craft, gear, Factory.ASSEMBLING_MACHINE_3))
        val second = PlannedRecipe(production(craft, gear, Factory.ASSEMBLING_MACHINE_3))

        assertEquals(first, second)
    }

    @Test
    fun thePlanBuildsTheRequestedMachineCount() {
        val craft =
            recipe("iron-gear-wheel", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 100.0))

        val outcome =
            plan(
                gear,
                craft,
                listOf(
                    production(
                        craft,
                        gear,
                        Factory.ASSEMBLING_MACHINE_3,
                        amount = 3.0,
                    ),
                ),
            )

        val planned = assertIs<BlueprintPlanResult.Planned>(outcome).plan
        assertAll(
            // even though the import would need more than two belts, the plan builds all three
            { assertEquals(3, planned.rows.single().machines.size) },
            { assertEquals("iron-gear-wheel", planned.label) },
        )
    }

    @Test
    fun machineFootprintsMatchThePrototypes() =
        assertTableOf(
            "recycler" to Dimension(4, 2),
            "electromagnetic-plant" to Dimension(4, 4),
            "foundry" to Dimension(5, 5),
            "assembling-machine-3" to Dimension(3, 3),
            "stone-furnace" to Dimension(2, 2),
        ) { (identifier, expected) ->
            assertEquals(expected, blueprintMachineFootprint(identifier))
        }

    @Test
    fun fluidPortsMatchThePrototypes() =
        assertTableOf(
            // the electromagnetic plant's pipes sit on every side; its west port is on the third
            // row, its east port on the second, and the north/south ports on the top/bottom edge
            "electromagnetic-plant" to
                listOf(
                    FluidPort(Coordinate(-1, 2), PortFlow.BOTH),
                    FluidPort(Coordinate(4, 1), PortFlow.BOTH),
                    FluidPort(Coordinate(2, 4), PortFlow.BOTH),
                    FluidPort(Coordinate(1, -1), PortFlow.BOTH),
                ),
            // the chemical plant takes its fluids on the two top corners, gives them on the bottom
            "chemical-plant" to
                listOf(
                    FluidPort(Coordinate(0, -1), PortFlow.INPUT),
                    FluidPort(Coordinate(2, -1), PortFlow.INPUT),
                    FluidPort(Coordinate(0, 3), PortFlow.OUTPUT),
                    FluidPort(Coordinate(2, 3), PortFlow.OUTPUT),
                ),
            // the foundry takes its fluid on the bottom and gives it on the top
            "foundry" to
                listOf(
                    FluidPort(Coordinate(1, 5), PortFlow.INPUT),
                    FluidPort(Coordinate(3, 5), PortFlow.INPUT),
                    FluidPort(Coordinate(1, -1), PortFlow.OUTPUT),
                    FluidPort(Coordinate(3, -1), PortFlow.OUTPUT),
                ),
            // a machine without pipes has no ports
            "electric-furnace" to emptyList(),
        ) { (identifier, expected) ->
            assertEquals(expected, blueprintMachineFluidPorts(identifier))
        }
}
