package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lightOil
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromLightOilRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.js.component.fixtures.OptimizationResultTestBase
import de.bjrke.factorio.optimizer.js.component.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.js.component.fixtures.production
import de.bjrke.factorio.optimizer.js.component.fixtures.twoProductions
import de.bjrke.test.assertAll
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import web.dom.Element

/** What the result block renders: the boxes, the material rows, the order and the icons. */
class OptimizationResultPresenterTest : OptimizationResultTestBase() {

    @Test
    fun recipeWithoutIngredientsRendersTheFactoryOutputAndLogisticsInputs() {
        val result = optimizationResult(ironOreMiningRecipe, ironOre, Factory.ELECTRIC_MINING_DRILL)

        val rendered = renderResult(result)

        // factory amount, output amount and the output's belt (a drill needs no inserter)
        assertEquals(3, rendered.container.querySelectorAll("input[type=number]").length)
    }

    @Test
    fun recipeWithIngredientsRendersIngredientAndOutputInputs() {
        val result = optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        val rendered = renderResult(result)

        // factory amount; then per material row the amount plus its belt and inserter counts
        assertEquals(7, rendered.container.querySelectorAll("input[type=number]").length)
    }

    @Test
    fun solidIngredientAndOutputShowBeltAndInserterCounts() {
        val result = optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        renderResult(result)

        // one belt/inserter indicator for the ingredient and one of each for the output
        assertEquals(2, screen.getAllByTitle("belts").size)
        assertEquals(2, screen.getAllByTitle("inserters").size)
    }

    @Test
    fun eachMaterialIsGroupedInABorderedBox() {
        val rendered =
            renderResult(
                optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
            )

        val groups = materialGroups(rendered.container)
        // one box for the ingredient and one for the output
        assertEquals(2, groups.length)
        for (index in 0 until groups.length) {
            assertEquals(
                "solid",
                computedStyle(groups.item(index)!!, "borderTopStyle"),
                "group $index is not boxed",
            )
        }
    }

    @Test
    fun themedFactoryBlocksAndGroupBoxesKeepTheirBackgroundAndBorder() {
        val rendered =
            renderResult(
                optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
            )

        val block = rendered.container.querySelector(":scope > div")!!
        val group = materialGroups(rendered.container).item(0)!!

        // the colours come from theme tokens; the light fallbacks must still resolve on the bare
        // test page, which defines no stylesheet tokens
        assertAll(
            { assertNotEquals("rgba(0, 0, 0, 0)", computedStyle(block, "backgroundColor")) },
            { assertNotEquals("rgba(0, 0, 0, 0)", computedStyle(group, "borderTopColor")) },
        )
    }

    @Test
    fun theFactoryAndRecipeAreBoxedTogetherInTheRow() {
        renderResult(optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        // the factory icon hangs in an <a> inside the factory group box
        val box =
            screen
                .getByTitle(Factory.ASSEMBLING_MACHINE_1.identifier)
                .parentElement!!
                .parentElement!!

        assertAll(
            { assertEquals("solid", computedStyle(box, "borderTopStyle")) },
            { assertEquals("inline-block", computedStyle(box, "display")) },
        )
    }

    @Test
    fun ingredientsComeBeforeTheFactoryAndOutputsAfterIt() {
        val rendered =
            renderResult(
                optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
            )

        val titles = imageTitles(rendered.container)
        val ingredient = titles.indexOf(ironPlate.name)
        val factory = titles.indexOf(Factory.ASSEMBLING_MACHINE_1.identifier)
        // the recipe icon shares the output material's title, so the output column is the last
        // match
        val output = titles.lastIndexOf(gear.name)

        assertAll(
            {
                assertContains(
                    0 until factory,
                    ingredient,
                    "ingredient at $ingredient, factory at $factory",
                )
            },
            { assertTrue(factory < output, "factory at $factory, output at $output") },
        )
    }

    @Test
    fun theMaterialPickerIsReplacedByMiddleClicks() {
        val rendered =
            renderResult(
                optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
            )

        assertEquals(0, rendered.container.querySelectorAll("button").length)
    }

    @Test
    fun fluidIngredientsShowAPumpAndAPipeMarker() {
        val calculator = LogisticsCalculator(testLogistics.copy(fluids = setOf(lightOil)))

        renderResult(
            optimizationResult(solidFuelFromLightOilRecipe, solidFuel, Factory.CHEMICAL_PLANT),
            calculator,
        )

        // the fluid ingredient gets a pump + pipe, the solid output a belt + inserter
        assertEquals(1, screen.getAllByTitle("pumps").size)
        assertEquals(1, screen.getAllByTitle("pipe").size)
        assertEquals(1, screen.getAllByTitle("belts").size)
        assertEquals(1, screen.getAllByTitle("inserters").size)
    }

    private val importRecipe =
        Recipe(
            name = ironPlate.name,
            ingredients = emptyMap(),
            result = mapOf(ironPlate to 1.0),
            time = 1.0,
            categories = setOf(Category.IMPORT),
        )

    private fun resultWithImport() =
        OptimizationResult(
            target = gear,
            recipe = ironGearWheelRecipe,
            factories =
                mapOf(
                    ironGearWheelRecipe to
                        production(
                            Triple(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                        ),
                    importRecipe to production(Triple(importRecipe, ironPlate, Factory.NO_FACTORY)),
                ),
        )

    @Test
    fun everyBuiltRowOffersAnExclusionCheckbox() {
        renderResult(
            twoProductions(
                Triple(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                Triple(ironOreMiningRecipe, ironOre, Factory.ELECTRIC_MINING_DRILL),
            ),
        )

        val checkbox = exclusionCheckbox("Exclude ${ironOre.name} from the blueprint")

        assertAll(
            { assertEquals(false, checkbox.checked) },
            { assertEquals(false, checkbox.disabled) },
            { assertEquals("visible", computedStyle(checkbox, "visibility")) },
        )
    }

    @Test
    fun theTargetRowKeepsAHiddenExclusionCheckbox() {
        renderResult(optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val checkbox = exclusionCheckbox("The target cannot be excluded from the blueprint")

        assertAll(
            { assertTrue(checkbox.disabled) },
            { assertEquals(false, checkbox.checked) },
            { assertEquals("hidden", computedStyle(checkbox, "visibility")) },
        )
    }

    @Test
    fun aPlainImportRowKeepsAHiddenExclusionCheckbox() {
        renderResult(resultWithImport())

        val checkbox =
            exclusionCheckbox(
                "${ironPlate.name} is already imported, so there is nothing to exclude",
            )

        assertAll(
            { assertTrue(checkbox.disabled) },
            { assertEquals(false, checkbox.checked) },
            { assertEquals("hidden", computedStyle(checkbox, "visibility")) },
        )
    }

    @Test
    fun anExcludedImportRowShowsACheckedCheckbox() {
        renderResult(resultWithImport(), excludedMaterials = setOf(ironPlate))

        val checkbox = exclusionCheckbox("Include ${ironPlate.name} in the blueprint")

        assertEquals(true, checkbox.checked)
    }

    @Test
    fun anExcludedMaterialWithoutARowKeepsACheckedCheckbox() {
        renderResult(
            optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
            excludedMaterials = setOf(ironPlate),
        )

        val checkbox = exclusionCheckbox("Include ${ironPlate.name} in the blueprint")

        assertEquals(true, checkbox.checked)
    }

    @Test
    fun aRowKeepsItsPlaceWhenItsPlanRowTurnsIntoAnImport() {
        val rendered = renderResult(rowOrderResult(circuitImport = false))
        assertEquals(
            listOf(
                Factory.ASSEMBLING_MACHINE_2.identifier,
                Factory.ASSEMBLING_MACHINE_1.identifier,
                Factory.ELECTRIC_FURNACE.identifier,
            ),
            factoryOrder(rendered.container),
        )

        rendered.rerender(resultElement(rowOrderResult(circuitImport = true)))

        // the material keeps its alphabetical slot even though the row is now an import
        assertEquals(
            listOf(
                Factory.NO_FACTORY.identifier,
                Factory.ASSEMBLING_MACHINE_1.identifier,
                Factory.ELECTRIC_FURNACE.identifier,
            ),
            factoryOrder(rendered.container),
        )
    }

    private fun rowOrderResult(circuitImport: Boolean): OptimizationResult {
        val circuitRecipe = if (circuitImport) circuitImportRecipe else electronicCircuitRecipe
        val circuitFactory = if (circuitImport) Factory.NO_FACTORY else Factory.ASSEMBLING_MACHINE_2
        return OptimizationResult(
            target = gear,
            recipe = ironGearWheelRecipe,
            factories =
                mapOf(
                    ironGearWheelRecipe to
                        production(
                            Triple(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                        ),
                    ironPlateSmeltingRecipe to
                        production(
                            Triple(
                                ironPlateSmeltingRecipe,
                                ironPlate,
                                Factory.ELECTRIC_FURNACE,
                            ),
                        ),
                    circuitRecipe to
                        production(
                            Triple(circuitRecipe, electronicCircuit, circuitFactory),
                        ),
                ),
        )
    }

    private val circuitImportRecipe =
        Recipe(
            name = electronicCircuit.name,
            ingredients = emptyMap(),
            result = mapOf(electronicCircuit to 1.0),
            time = 1.0,
            categories = setOf(Category.IMPORT),
        )

    /** The row order as the factory icons render it. */
    private fun factoryOrder(container: Element): List<String?> {
        val identifiers =
            listOf(
                    Factory.ASSEMBLING_MACHINE_1,
                    Factory.ASSEMBLING_MACHINE_2,
                    Factory.ELECTRIC_FURNACE,
                    Factory.NO_FACTORY,
                )
                .map { it.identifier }
        return imageTitles(container).filter { it in identifiers }
    }
}
