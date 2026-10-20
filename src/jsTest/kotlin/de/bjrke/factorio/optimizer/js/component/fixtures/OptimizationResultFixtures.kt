package de.bjrke.factorio.optimizer.js.component.fixtures

import de.bjrke.factorio.optimizer.common.business.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.business.testLogistics
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.js.component.optimizationResult
import de.bjrke.test.ComponentTest
import de.bjrke.test.render
import de.bjrke.test.screen
import react.create
import web.dom.Element
import web.html.HTMLInputElement

/** The recipes, result fixtures and render helpers the `OptimizationResult*` suites share. */
internal val ore = Material("iron-ore")
internal val plate = Material("iron-plate")
internal val gear = Material("iron-gear-wheel")

internal val miningRecipe =
    Recipe(
        name = "iron-ore",
        ingredients = emptyMap(),
        result = mapOf(ore to 1.0),
        time = 1.0,
        categories = setOf(Category.MINING_BASIC_SOLID),
    )

internal val craftingRecipe =
    Recipe(
        name = "iron-gear-wheel",
        ingredients = mapOf(plate to 2.0),
        result = mapOf(gear to 1.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

internal fun optimizationResult(recipe: Recipe, target: Material, factory: Factory) =
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
                    ),
            ),
    )

internal fun production(entry: Triple<Recipe, Material, Factory>) =
    FactoryProduction(
        material = entry.second,
        recipe = entry.first,
        factory = entry.third,
        amount = 1.0,
        level = 0,
    )

/** Two recipe blocks rendered side by side so their widths can be compared. */
internal fun twoProductions(
    first: Triple<Recipe, Material, Factory>,
    second: Triple<Recipe, Material, Factory>,
) =
    OptimizationResult(
        target = first.second,
        recipe = first.first,
        factories = mapOf(first.first to production(first), second.first to production(second)),
    )

abstract class OptimizationResultTestBase : ComponentTest() {
    protected val normalizedTo = mutableListOf<Double>()
    protected val changedMaterials = mutableListOf<Material>()

    protected val logistics =
        LogisticsCalculator(
            testLogistics,
            BeltTier.EXPRESS_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 1,
            beltStack = 1,
            inserterStack = 1,
        )

    protected fun resultElement(
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

    protected fun renderResult(
        result: OptimizationResult,
        calculator: LogisticsCalculator = logistics,
        normalizeTarget: Double = 1.0,
    ) = render(resultElement(result, calculator, normalizeTarget))

    protected fun blockWidths(container: Element): List<Double> {
        val blocks = container.querySelectorAll(":scope > div")
        return (0 until blocks.length).map { blocks.item(it)!!.getBoundingClientRect().width }
    }

    protected fun computedStyle(element: Element, property: String): String =
        js("(function (el, prop) { return getComputedStyle(el)[prop]; })")(element, property)

    /** The bordered group boxes: `block > column > material group`. */
    protected fun materialGroups(container: Element) =
        container.querySelectorAll(":scope > div > div > div")

    protected fun imageTitles(container: Element): List<String?> {
        val images = container.querySelectorAll("img")
        return (0 until images.length).map { images.item(it)!!.getAttribute("title") }
    }

    /** `{ button: 1 }` as expected by `fireEvent.mouseDown` for the middle button. */
    protected fun middleButton(): dynamic {
        val options = js("({})")
        options.button = 1
        return options
    }

    protected fun firstBeltInput(): HTMLInputElement =
        screen.getAllByTitle("belts").first() as HTMLInputElement

    protected fun beltInput(index: Int): Double =
        (screen.getAllByTitle("belts")[index] as HTMLInputElement).value.toDouble()

    protected fun inserterInput(index: Int): Double =
        (screen.getAllByTitle("inserters")[index] as HTMLInputElement).value.toDouble()

    protected fun calculator(
        inserter: InserterTier,
        beltStack: Int,
        inserterStack: Int = 1,
    ) =
        LogisticsCalculator(
            testLogistics,
            BeltTier.EXPRESS_TRANSPORT_BELT,
            inserter,
            lanes = 1,
            beltStack = beltStack,
            inserterStack = inserterStack,
        )
}
