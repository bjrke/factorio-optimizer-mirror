package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.business.testLogistics
import de.bjrke.factorio.optimizer.common.model.Belt
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Inserter
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import react.create
import web.dom.Element
import web.html.HTMLInputElement

private val ore = Material("iron-ore")
private val plate = Material("iron-plate")
private val gear = Material("iron-gear-wheel")

private val miningRecipe =
    Recipe(
        name = "iron-ore",
        ingredients = emptyMap(),
        result = mapOf(ore to 1.0),
        time = 1.0,
        categories = setOf(Category.MINING_BASIC_SOLID),
    )

private val craftingRecipe =
    Recipe(
        name = "iron-gear-wheel",
        ingredients = mapOf(plate to 2.0),
        result = mapOf(gear to 1.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

private fun optimizationResult(recipe: Recipe, target: Material, factory: Factory) =
    OptimizationResult(
        target = target,
        recipe = recipe,
        factories =
            mapOf(
                recipe to
                    FactoryProduction(
                        material = target,
                        recipe = recipe,
                        factory = factory,
                        amount = 1.0,
                        level = 0,
                    )
            ),
    )

private fun production(entry: Triple<Recipe, Material, Factory>) =
    FactoryProduction(
        material = entry.second,
        recipe = entry.first,
        factory = entry.third,
        amount = 1.0,
        level = 0,
    )

/** Two recipe blocks rendered side by side so their widths can be compared. */
private fun twoProductions(
    first: Triple<Recipe, Material, Factory>,
    second: Triple<Recipe, Material, Factory>,
) =
    OptimizationResult(
        target = first.second,
        recipe = first.first,
        factories = mapOf(first.first to production(first), second.first to production(second)),
    )

class OptimizationResultPresenterTest : ComponentTest() {
    private val normalizedTo = mutableListOf<Double>()
    private val changedMaterials = mutableListOf<Material>()

    private val logistics =
        LogisticsCalculator(
            testLogistics,
            Belt.EXPRESS_TRANSPORT_BELT,
            Inserter.FAST_INSERTER,
            lanes = 1,
            beltStack = 1,
            inserterStack = 1,
        )

    private fun resultElement(
        result: OptimizationResult,
        calculator: LogisticsCalculator = logistics,
        normalizeTarget: Double = 1.0,
    ) = optimizationResult.create {
        optimizationResult = result
        logistics = calculator
        this.normalizeTarget = normalizeTarget
        changeMaterial = { changedMaterials += it }
        changeNormalizeTo = { normalizedTo += it }
    }

    private fun renderResult(
        result: OptimizationResult,
        calculator: LogisticsCalculator = logistics,
        normalizeTarget: Double = 1.0,
    ) = render(resultElement(result, calculator, normalizeTarget))

    private fun blockWidths(container: Element): List<Double> {
        val blocks = container.querySelectorAll(":scope > div")
        return (0 until blocks.length).map { blocks.item(it)!!.getBoundingClientRect().width }
    }

    private fun computedStyle(element: Element, property: String): String =
        js("(function (el, prop) { return getComputedStyle(el)[prop]; })")(element, property)

    /** The bordered group boxes: `block > column > material group`. */
    private fun materialGroups(container: Element) =
        container.querySelectorAll(":scope > div > div > div")

    private fun imageTitles(container: Element): List<String?> {
        val images = container.querySelectorAll("img")
        return (0 until images.length).map { images.item(it)!!.getAttribute("title") }
    }

    /** `{ button: 1 }` as expected by `fireEvent.mouseDown` for the middle button. */
    private fun middleButton(): dynamic {
        val options = js("({})")
        options.button = 1
        return options
    }

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
    fun clickingTheFactoryIconNormalizesToTheFactoryAmount() {
        val result = optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL)
        renderResult(result)

        fireEvent.click(screen.getByTitle(Factory.ELECTRIC_MINING_DRILL.identifier))

        assertEquals(listOf(1.0), normalizedTo)
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
                assertTrue(
                    ingredient in 0 until factory,
                    "ingredient at $ingredient, factory at $factory",
                )
            },
            { assertTrue(factory < output, "factory at $factory, output at $output") },
        )
    }

    @Test
    fun ingredientAndOutputBeltsAreRotatedOppositely() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val belts = screen.getAllByTitle(Belt.EXPRESS_TRANSPORT_BELT.identifier)

        assertAll(
            { assertEquals("matrix(0, 1, -1, 0, 0, 0)", computedStyle(belts[0], "transform")) },
            { assertEquals("matrix(0, -1, 1, 0, 0, 0)", computedStyle(belts[1], "transform")) },
        )
    }

    @Test
    fun onlyTheOutputInserterIsMirroredHorizontally() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val inserters = screen.getAllByTitle(Inserter.FAST_INSERTER.identifier)

        assertAll(
            { assertEquals("none", computedStyle(inserters[0], "transform")) },
            { assertEquals("matrix(-1, 0, 0, 1, 0, 0)", computedStyle(inserters[1], "transform")) },
        )
    }

    @Test
    fun beltAndInserterIconsAreAsLargeAsTheMaterialIcons() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val materialSize = computedStyle(screen.getByTitle(plate.name), "width")

        assertAll(
            {
                assertEquals(
                    materialSize,
                    computedStyle(
                        screen.getAllByTitle(Belt.EXPRESS_TRANSPORT_BELT.identifier)[0],
                        "width",
                    ),
                )
            },
            {
                assertEquals(
                    materialSize,
                    computedStyle(
                        screen.getAllByTitle(Inserter.FAST_INSERTER.identifier)[0],
                        "width",
                    ),
                )
            },
        )
    }

    @Test
    fun everyNumberInputHasTheSameWidth() {
        val rendered =
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val inputs = rendered.container.querySelectorAll("input[type=number]")
        val widths = (0 until inputs.length).map { computedStyle(inputs.item(it)!!, "width") }

        // factory, ingredient and output amounts plus their belt/inserter counts
        assertEquals(7, widths.size)
        assertEquals(listOf("64px"), widths.distinct())
    }

    @Test
    fun middleClickingAnIngredientIconSelectsThatMaterial() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.mouseDown(screen.getByTitle(plate.name), middleButton())

        assertEquals(listOf(plate), changedMaterials)
    }

    @Test
    fun middleClickingTheRecipeIconSelectsTheProducedMaterial() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        // the recipe icon comes before the output icon, which shares its title
        fireEvent.mouseDown(screen.getAllByTitle(gear.name)[0], middleButton())

        assertEquals(listOf(gear), changedMaterials)
    }

    @Test
    fun middleClickingTheFactoryIconSelectsNothing() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.mouseDown(
            screen.getByTitle(Factory.ASSEMBLING_MACHINE_1.identifier),
            middleButton(),
        )

        assertEquals(emptyList<Material>(), changedMaterials)
    }

    @Test
    fun leftClickingAMaterialIconOnlyNormalizesTheAmount() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.click(screen.getByTitle(plate.name))

        assertAll(
            { assertEquals(emptyList<Material>(), changedMaterials) },
            // the ingredient's consumption, exactly like clicking the amount icon
            { assertEquals(listOf(2.0), normalizedTo) },
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
                Belt.EXPRESS_TRANSPORT_BELT,
                Inserter.FAST_INSERTER,
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

    private fun firstBeltInput(): HTMLInputElement =
        screen.getAllByTitle("belts").first() as HTMLInputElement

    @Test
    fun logisticsCountsScaleWithTheNormalizeTarget() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        val rendered = renderResult(result, normalizeTarget = 1.0)
        val atOne = firstBeltInput().value.toDouble()

        rendered.rerender(resultElement(result, normalizeTarget = 2.0))
        val atTwo = firstBeltInput().value.toDouble()

        assertEquals(atOne * 2, atTwo, 0.01)
    }

    @Test
    fun editingTheBeltCountPinsIt() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        renderResult(result)

        val count = firstBeltInput().value.toDouble()
        fireEvent.change(firstBeltInput(), changeTo("1"))

        // changeNormalizeTo(count) makes the displayed count one
        assertEquals(count, normalizedTo.single(), 0.01)
    }

    @Test
    fun clickingTheBeltIconPinsItToOne() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        renderResult(result)

        fireEvent.click(screen.getAllByTitle(Belt.EXPRESS_TRANSPORT_BELT.identifier).first())

        assertEquals(firstBeltInput().value.toDouble(), normalizedTo.single(), 0.01)
    }

    private fun beltInput(index: Int): Double =
        (screen.getAllByTitle("belts")[index] as HTMLInputElement).value.toDouble()

    private fun inserterInput(index: Int): Double =
        (screen.getAllByTitle("inserters")[index] as HTMLInputElement).value.toDouble()

    @Test
    fun aStackInserterNeedsFewerOutputInsertersThanABulkInserter() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        val rendered =
            renderResult(
                result,
                calculator(Inserter.BULK_INSERTER, beltStack = 4, inserterStack = 8),
            )
        val bulkOutput = inserterInput(1)

        rendered.rerender(
            resultElement(
                result,
                calculator(Inserter.STACK_INSERTER, beltStack = 4, inserterStack = 8),
            )
        )
        val stackOutput = inserterInput(1)

        // same hand size; the stack inserter places whole belt stacks while the bulk places
        // one item per belt slot
        assertTrue(stackOutput < bulkOutput)
    }

    private fun calculator(
        inserter: Inserter,
        beltStack: Int,
        inserterStack: Int = 1,
    ) =
        LogisticsCalculator(
            testLogistics,
            Belt.EXPRESS_TRANSPORT_BELT,
            inserter,
            lanes = 1,
            beltStack = beltStack,
            inserterStack = inserterStack,
        )

    @Test
    fun miningOutputNeedsNoInserter() {
        renderResult(optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL))

        assertNotNull(screen.queryByTitle("belts"))
        assertNull(screen.queryByTitle("inserters"))
    }

    @Test
    fun theBigMiningDrillHonorsTheBeltStackWithoutAnInserter() {
        val result = optimizationResult(miningRecipe, ore, Factory.BIG_MINING_DRILL)
        val rendered = renderResult(result, calculator(Inserter.FAST_INSERTER, beltStack = 1))
        val atSingle = beltInput(0)

        rendered.rerender(resultElement(result, calculator(Inserter.FAST_INSERTER, beltStack = 4)))
        val atStacked = beltInput(0)

        // the big mining drill stacks the belt itself, so stacking means a quarter of the belts
        assertEquals(atSingle, atStacked * 4, 0.01)
    }

    @Test
    fun theOtherMiningDrillsIgnoreTheBeltStack() {
        val result = optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL)
        val rendered = renderResult(result, calculator(Inserter.FAST_INSERTER, beltStack = 1))
        val atSingle = beltInput(0)

        rendered.rerender(resultElement(result, calculator(Inserter.FAST_INSERTER, beltStack = 4)))

        // an electric mining drill places single items, so stacking does not change its belts
        assertEquals(atSingle, beltInput(0), 0.01)
    }

    @Test
    fun otherOutputsOnlyStackWithAStackingInserter() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        val rendered = renderResult(result, calculator(Inserter.STACK_INSERTER, beltStack = 4))
        val inputStacked = beltInput(0)
        val outputStacked = beltInput(1)

        rendered.rerender(resultElement(result, calculator(Inserter.FAST_INSERTER, beltStack = 4)))
        val inputUnstacked = beltInput(0)
        val outputUnstacked = beltInput(1)

        // inputs always use the stacked belt; a non-stacking inserter leaves the output unstacked
        assertEquals(inputStacked, inputUnstacked, 0.01)
        assertEquals(outputUnstacked, outputStacked * 4, 0.01)
    }

    @Test
    fun materialRowsDoNotOverflowTheirColumn() {
        val rendered =
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val columns = rendered.container.querySelectorAll(":scope > div > div")
        assertTrue(columns.length >= 2)
        for (index in 0 until columns.length) {
            val column = columns.item(index)!!
            assertTrue(
                column.scrollWidth <= column.clientWidth + 1,
                "column $index overflows (${column.scrollWidth} > ${column.clientWidth})",
            )
        }
    }

    @Test
    fun recipesWithAndWithoutIngredientsHaveTheSameWidth() {
        val rendered =
            renderResult(
                twoProductions(
                    Triple(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL),
                    Triple(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                )
            )

        val widths = blockWidths(rendered.container)
        assertEquals(2, widths.size)
        assertEquals(widths[0], widths[1], 0.5)
    }

    @Test
    fun solidAndFluidRowsHaveTheSameWidth() {
        val water = Material("water")
        val fluidRecipe =
            Recipe(
                name = "heavy-oil",
                ingredients = mapOf(water to 10.0),
                result = mapOf(gear to 1.0),
                time = 1.0,
                categories = setOf(Category.CHEMISTRY),
            )
        val calculator =
            LogisticsCalculator(
                testLogistics.copy(fluids = setOf(water)),
                Belt.EXPRESS_TRANSPORT_BELT,
                Inserter.FAST_INSERTER,
                lanes = 1,
                beltStack = 1,
                inserterStack = 1,
            )

        val rendered =
            renderResult(
                twoProductions(
                    Triple(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                    Triple(fluidRecipe, gear, Factory.CHEMICAL_PLANT),
                ),
                calculator,
            )

        val widths = blockWidths(rendered.container)
        assertEquals(2, widths.size)
        assertEquals(widths[0], widths[1], 0.5)
    }
}
