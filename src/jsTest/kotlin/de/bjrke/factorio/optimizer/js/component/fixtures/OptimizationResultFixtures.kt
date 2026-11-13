package de.bjrke.factorio.optimizer.js.component.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.js.component.optimizationResult
import de.bjrke.test.ComponentTest
import de.bjrke.test.render
import de.bjrke.test.screen
import react.create
import web.dom.Element
import web.html.HTMLInputElement

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

/** Two recipe blocks so their widths and stacking can be compared. */
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
    protected val changedExclusions = mutableListOf<Pair<Material, Boolean>>()

    protected val logistics = calculator()

    protected fun resultElement(
        result: OptimizationResult,
        calculator: LogisticsCalculator = logistics,
        normalizeTarget: Double = 1.0,
        excludedMaterials: Set<Material> = emptySet(),
    ) = optimizationResult.create {
        optimizationResult = result
        logistics = calculator
        this.normalizeTarget = normalizeTarget
        this.excludedMaterials = excludedMaterials
        changeMaterial = { changedMaterials += it }
        changeNormalizeTo = { normalizedTo += it }
        editNormalizeTo = { normalizedTo += it }
        changeExcluded = { material, exclude -> changedExclusions += material to exclude }
    }

    protected fun renderResult(
        result: OptimizationResult,
        calculator: LogisticsCalculator = logistics,
        normalizeTarget: Double = 1.0,
        excludedMaterials: Set<Material> = emptySet(),
    ) = render(resultElement(result, calculator, normalizeTarget, excludedMaterials))

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

    /** The exclusion box under [title]; the title sits on its wrapper, which outlives hiding. */
    protected fun exclusionCheckbox(title: String): HTMLInputElement =
        screen.getByTitle(title).querySelector("input") as HTMLInputElement

    protected fun beltInput(index: Int): Double =
        (screen.getAllByTitle("belts")[index] as HTMLInputElement).value.toDouble()

    protected fun inserterInput(index: Int): Double =
        (screen.getAllByTitle("inserters")[index] as HTMLInputElement).value.toDouble()
}
