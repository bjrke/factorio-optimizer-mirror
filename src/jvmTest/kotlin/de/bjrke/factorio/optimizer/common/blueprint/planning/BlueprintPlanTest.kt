package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.testBlueprintPlan
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.recipes
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.utilitySciencePack
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.plan.MAX_BLUEPRINT_RECIPES
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import de.bjrke.factorio.optimizer.common.service.OptimizationResult
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    private val logistics = calculator()

    private fun plan(target: Material, targetRecipe: Recipe, productions: List<FactoryProduction>) =
        testBlueprintPlan(result(target, targetRecipe, productions), logistics)

    @Test
    fun theAllSciencePacksTargetIsUnavailable() {
        val smelt = recipe("iron-plate", Category.SMELTING, mapOf(ironPlate to 1.0))
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
                scale = 1.0,
            )

        assertIs<BlueprintPlanResult.Unavailable>(outcome)
    }

    @Test
    fun aMiningRecipeBecomesASourceInsteadOfABuilding() {
        val mining = recipe("iron-ore", Category.MINING_BASIC_SOLID, mapOf(ironOre to 1.0))
        val smelt =
            recipe("iron-plate", Category.SMELTING, mapOf(ironPlate to 1.2), mapOf(ironOre to 1.0))
        val craft =
            recipe(
                "iron-gear-wheel",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ironPlate to 2.0),
            )

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

        // the drill is place-dependent and the smelter is left out; both become imports
        assertAll(
            { assertEquals(listOf(gear), planned.recipes.map { it.material }) },
            { assertEquals(listOf(ironPlate), planned.sources.map { it.material }) },
        )
    }

    @Test
    fun aFoundryRecipeIsKeptWhileAFurnaceRecipeIsNot() {
        val foundry =
            recipe(
                "molten-iron",
                Category.METALLURGY,
                mapOf(ironPlate to 1.0),
                mapOf(ironOre to 1.0),
            )
        val furnace = recipe("iron-plate", Category.SMELTING, mapOf(ironPlate to 1.0))
        val craft =
            recipe(
                "iron-gear-wheel",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ironPlate to 2.0),
            )

        val keptPlan =
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
        val excludedPlan =
            plan(
                gear,
                craft,
                listOf(
                    production(craft, gear, Factory.ASSEMBLING_MACHINE_3),
                    production(furnace, ironPlate, Factory.STEEL_FURNACE),
                ),
            )

        assertAll(
            { assertContains(keptPlan.recipes.map { it.factory }, Factory.FOUNDRY) },
            // the furnace plan has only the gear recipe left; the plate is imported
            { assertEquals(listOf(ironPlate), excludedPlan.sources.map { it.material }) },
        )
    }

    @Test
    fun oilProcessingIsLeftOutUnlessItIsTheTarget() {
        val refine =
            recipe("advanced-oil-processing", Category.OIL_PROCESSING, mapOf(petroleum to 1.0))
        val plastic =
            recipe("plastic-bar", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(petroleum to 2.0))

        val ingredientPlan =
            plan(
                gear,
                plastic,
                listOf(
                    production(plastic, gear, Factory.CHEMICAL_PLANT),
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
    fun atMostTheRecipeLimitIsBuilt() {
        val materials = (0..12).map { Material("step-$it") }
        val targetRecipe =
            recipe(
                "target",
                Category.CRAFTING,
                mapOf(utilitySciencePack to 1.0),
                mapOf(materials[0] to 1.0),
            )
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

        val planned =
            plan(
                utilitySciencePack,
                targetRecipe,
                chain +
                    production(targetRecipe, utilitySciencePack, Factory.SCIENCE_LAB, level = 99),
            )

        // the cap counts every built recipe, not the productions a recipe may be split into
        assertEquals(MAX_BLUEPRINT_RECIPES, planned.recipes.map { it.recipe }.distinct().size)
    }

    @Test
    fun aMaterialMadeAndTakenByOneRecipeIsDirectInserted() {
        val belt =
            recipe(
                "transport-belt",
                Category.CRAFTING,
                mapOf(transportBelt to 1.0),
                mapOf(gear to 1.0, ironPlate to 1.0),
            )
        val craft =
            recipe(
                "iron-gear-wheel",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ironPlate to 2.0),
            )

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
    fun productionsOfTheSameRecipeCompareByValue() {
        val craft =
            recipe(
                "iron-gear-wheel",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ironPlate to 2.0),
            )
        val first = production(craft, gear, Factory.ASSEMBLING_MACHINE_3)
        val second = production(craft, gear, Factory.ASSEMBLING_MACHINE_3)

        assertEquals(first, second)
    }

    @Test
    fun thePlanBuildsTheRequestedMachineCount() {
        val craft =
            recipe(
                "iron-gear-wheel",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ironPlate to 100.0),
            )

        val planned =
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

        assertAll(
            // even though the import would need more than two belts, the plan builds all three
            { assertEquals(3, planned.rows.single().machines.size) },
            { assertEquals("iron-gear-wheel", planned.label) },
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
