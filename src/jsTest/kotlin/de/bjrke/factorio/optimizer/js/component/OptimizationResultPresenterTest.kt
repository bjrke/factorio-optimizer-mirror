package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.business.testLogistics
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.js.component.fixtures.*
import de.bjrke.test.assertAll
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** What the result block renders: the boxes, the material rows, the order and the icons. */
class OptimizationResultPresenterTest : OptimizationResultTestBase() {

    @Test
    fun recipeWithoutIngredientsRendersTheFactoryOutputAndLogisticsInputs() {
        val result = optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL)

        val rendered = renderResult(result)

        // factory amount, output amount and the output's belt (a drill needs no inserter)
        assertEquals(3, rendered.container.querySelectorAll("input[type=number]").length)
    }

    @Test
    fun recipeWithIngredientsRendersIngredientAndOutputInputs() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        val rendered = renderResult(result)

        // factory amount; then per material row the amount plus its belt and inserter counts
        assertEquals(7, rendered.container.querySelectorAll("input[type=number]").length)
    }

    @Test
    fun solidIngredientAndOutputShowBeltAndInserterCounts() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        renderResult(result)

        // one belt/inserter indicator for the ingredient and one of each for the output
        assertEquals(2, screen.getAllByTitle("belts").size)
        assertEquals(2, screen.getAllByTitle("inserters").size)
    }

    @Test
    fun eachMaterialIsGroupedInABorderedBox() {
        val rendered =
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

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
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

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
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

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
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val titles = imageTitles(rendered.container)
        val ingredient = titles.indexOf(plate.name)
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
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        assertEquals(0, rendered.container.querySelectorAll("button").length)
    }

    @Test
    fun fluidIngredientsShowAPumpAndAPipeMarker() {
        val water = Material("water")
        val fluidRecipe =
            Recipe(
                name = "sulfuric-acid",
                ingredients = mapOf(water to 10.0),
                result = mapOf(gear to 1.0),
                time = 1.0,
                categories = setOf(Category.CHEMISTRY),
            )
        val calculator =
            LogisticsCalculator(
                testLogistics.copy(fluids = setOf(water)),
                BeltTier.EXPRESS_TRANSPORT_BELT,
                InserterTier.FAST_INSERTER,
                lanes = 1,
                beltStack = 1,
                inserterStack = 1,
            )

        renderResult(
            optimizationResult(fluidRecipe, gear, Factory.CHEMICAL_PLANT),
            calculator,
        )

        // the fluid ingredient gets a pump + pipe, the solid output a belt + inserter
        assertEquals(1, screen.getAllByTitle("pumps").size)
        assertEquals(1, screen.getAllByTitle("pipe").size)
        assertEquals(1, screen.getAllByTitle("belts").size)
        assertEquals(1, screen.getAllByTitle("inserters").size)
    }
}
