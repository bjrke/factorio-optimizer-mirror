package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.production.superRound
import de.bjrke.factorio.optimizer.common.production.validNormalizeTarget
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.service.OptimizationResult
import de.bjrke.factorio.optimizer.common.service.factoryProductionComparator
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import web.cssom.Border
import web.cssom.Color
import web.cssom.Display
import web.cssom.LineStyle
import web.cssom.VerticalAlign
import web.cssom.ch
import web.cssom.px
import web.cssom.rem
import web.cssom.rgb
import web.html.InputType
import web.html.number
import web.mouse.AUXILIARY
import web.mouse.MouseButton

external interface OptimizationResultPresenterStateProps : Props {
    var optimizationResult: OptimizationResult
    var logistics: LogisticsCalculator
    var normalizeTarget: Double
}

external interface OptimizationResultPresenterDispatchProps : Props {
    var changeMaterial: (Material) -> Unit
    var changeNormalizeTo: (Double) -> Unit
}

external interface OptimizationResultPresenterProps :
    OptimizationResultPresenterStateProps, OptimizationResultPresenterDispatchProps

/** `MouseEvent.button` value of the middle mouse button. */
private val MIDDLE_BUTTON = MouseButton.AUXILIARY

private fun ChildrenBuilder.amountInput(
    props: OptimizationResultPresenterProps,
    amount: Double,
) {
    input {
        type = InputType.number
        min = "0"
        css {
            width = 4.rem
            marginRight = 1.ch
        }
        value = superRound(amount * props.normalizeTarget).toString()
        onChange = { event ->
            event.target.value.toDoubleOrNull()?.let { value ->
                validNormalizeTarget(amount / value)?.let { props.changeNormalizeTo(it) }
            }
        }
    }
}

/**
 * A clickable icon for an amount: a left click pins the amount, a middle click (when the icon
 * stands for a [material]) selects that material as the new target.
 */
private fun ChildrenBuilder.amountIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    amount: Double,
    material: Material? = null,
) {
    a {
        css { marginRight = 1.ch }
        onClick = { props.changeNormalizeTo(amount) }
        if (material != null) {
            // a middle click selects the material and must not start the browser's autoscroll
            onMouseDown = {
                if (it.button == MIDDLE_BUTTON) {
                    it.preventDefault()
                    props.changeMaterial(material)
                }
            }
        }
        icon(name)
    }
}

private fun ChildrenBuilder.amountInputIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    amount: Double,
    material: Material? = null,
) {
    amountInput(props, amount)
    amountIcon(props, name, amount, material)
}

// fixed widths so a row is exactly as wide no matter whether it carries a belt+inserter, a
// pump+pipe or (for recipes without ingredients) nothing at all
private val LOGISTICS_INPUT_WIDTH = 4.rem
private val LOGISTICS_ITEM_WIDTH = 6.75.rem

/** Fixed input/output column width; empty and fluid columns are padded to match. */
private val COLUMN_WIDTH = 24.rem

/**
 * A bordered box around a group of input fields that belong together. [inline] boxes hug their
 * content and sit in the row (the factory), while the default boxes fill their column (materials).
 */
private fun ChildrenBuilder.groupBox(inline: Boolean = false, content: ChildrenBuilder.() -> Unit) {
    div {
        css {
            border = Border(1.px, LineStyle.solid, rgb(120, 120, 120))
            // the theme token wins; the literal is the light fallback for contexts without the
            // stylesheet tokens (e.g. component tests)
            borderColor = Color("var(--border, #787878)")
            borderRadius = 0.25.rem
            padding = 0.25.rem
            marginBottom = 0.25.rem
            marginRight = 0.25.rem
            if (inline) {
                display = Display.inlineBlock
                verticalAlign = VerticalAlign.middle
            }
        }
        content()
    }
}

/** A material's amount plus its belt and inserter counts, boxed together. */
private fun ChildrenBuilder.materialGroup(
    props: OptimizationResultPresenterProps,
    factory: Factory,
    material: Material,
    amount: Double,
    isOutput: Boolean,
) {
    groupBox {
        amountInputIcon(props, material.name, amount, material)
        materialLogistics(props, factory, material, amount, isOutput)
    }
}

/**
 * A derived logistics count (belt, inserter, pump) as an editable field next to its clickable icon.
 * Editing it or clicking the icon scales the whole plan through the normalize target, exactly like
 * the material amounts, so e.g. a belt can be pinned to one.
 */
private fun ChildrenBuilder.logisticsInputIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    unit: String,
    amount: Double,
    rotation: Int? = null,
    flipHorizontal: Boolean = false,
) {
    input {
        type = InputType.number
        min = "0"
        title = unit
        css {
            width = LOGISTICS_INPUT_WIDTH
            marginRight = 0.25.rem
        }
        value = superRound(amount * props.normalizeTarget).toString()
        onChange = {
            it.target.value.toDoubleOrNull()?.let { value ->
                validNormalizeTarget(amount / value)?.let { scale ->
                    props.changeNormalizeTo(scale)
                }
            }
        }
    }
    a {
        css { marginRight = 0.5.rem }
        if (amount > 0.0) {
            onClick = { props.changeNormalizeTo(amount) }
        }
        icon(name) {
            this.rotation = rotation
            this.flipHorizontal = flipHorizontal
        }
    }
}

private fun ChildrenBuilder.pipeMarker() {
    span {
        css {
            display = Display.inlineBlock
            width = LOGISTICS_ITEM_WIDTH
        }
        // the icon carries the "pipe" title used as the marker
        icon("pipe")
    }
}

/**
 * The logistics needed to move [material] at [rate] items/fluid per second: a belt and an inserter
 * for solids, a pump plus a pipe marker for fluids. The counts are editable like the material
 * amounts.
 *
 * The big mining drill and the recycler drop their output straight onto the belt and stack it
 * themselves, so such an output needs no inserter and always uses the belt stacking. The other
 * drills place single items, and the remaining outputs are loaded by the selected inserter, so belt
 * stacking only applies when that inserter can stack, and the output inserter count uses the slower
 * belt-limited placing rate.
 */
private fun ChildrenBuilder.materialLogistics(
    props: OptimizationResultPresenterProps,
    factory: Factory,
    material: Material,
    rate: Double,
    isOutput: Boolean,
) {
    val calculator = props.logistics
    if (calculator.isFluid(material)) {
        logisticsInputIcon(props, "pump", "pumps", calculator.pumpCount(rate))
        pipeMarker()
        return
    }

    val directOutput = isOutput && factory.outputsDirectly
    // a direct output is stacked by the machine itself, any other output by the selected inserter
    val outputStacks = if (directOutput) factory.stacksOnBelt else calculator.rates.stacksOnBelt
    val stack = if (isOutput && !outputStacks) 1 else calculator.beltStack
    logisticsInputIcon(
        props,
        calculator.belt.identifier,
        "belts",
        calculator.beltCount(rate, stack),
        // an input belt feeds the machine (clockwise), an output belt carries the product away
        rotation = if (isOutput) -90 else 90,
    )
    if (!directOutput) {
        logisticsInputIcon(
            props,
            calculator.inserter.identifier,
            "inserters",
            // an output inserter drops onto the belt, an input inserter into the machine
            calculator.inserterCount(rate, ontoBelt = isOutput),
            // the output inserter points the other way, so mirror it
            flipHorizontal = isOutput,
        )
    }
}

internal val optimizationResult =
    FC<OptimizationResultPresenterProps>("optimizationResult") { props ->
        props.optimizationResult.factories.values.sortedWith(factoryProductionComparator).forEach {
            factoryProduction ->
            div {
                css {
                    display = Display.inlineBlock
                    verticalAlign = VerticalAlign.middle
                    padding = 0.5.rem
                    // the material hue tints the block (keeping its identity) but is mixed with
                    // the themed panel so it stays muted in dark mode
                    backgroundColor =
                        Color(
                            "color-mix(in srgb, ${hashColor(factoryProduction.material.name)} " +
                                "var(--factory-tint, 70%), var(--factory-panel, #fff))",
                        )
                }
                div {
                    css {
                        display = Display.inlineBlock
                        verticalAlign = VerticalAlign.middle
                        // recipes without ingredients (mining, pumping, ...) still reserve the
                        // full column so every recipe block keeps the same width
                        width = COLUMN_WIDTH
                    }
                    factoryProduction.recipe.ingredients.keys.forEach {
                        materialGroup(
                            props,
                            factoryProduction.factory,
                            it,
                            factoryProduction.consumption(it),
                            isOutput = false,
                        )
                    }
                }

                groupBox(inline = true) {
                    amountInputIcon(
                        props,
                        factoryProduction.factory.identifier,
                        factoryProduction.amount,
                    )
                    // middle-clicking the recipe icon picks the produced material as the target
                    amountIcon(
                        props,
                        factoryProduction.recipe.name,
                        factoryProduction.amount,
                        factoryProduction.material,
                    )
                }

                div {
                    css {
                        display = Display.inlineBlock
                        verticalAlign = VerticalAlign.middle
                        // the fixed width keeps the output column aligned with the input column
                        width = COLUMN_WIDTH
                    }
                    factoryProduction.recipe.result.keys.forEach {
                        materialGroup(
                            props,
                            factoryProduction.factory,
                            it,
                            factoryProduction.production(it),
                            isOutput = true,
                        )
                    }
                }
            }
        }
    }

fun hashColor(s: String, alpha: Double = 1.0): Color {
    val hash = s.hashCode()
    val r = hash and 0x7F0000 shr 16
    val g = hash and 0x007F00 shr 8
    val b = hash and 0x00007F

    return rgb(r + 128, g + 128, b + 128, alpha)
}

fun ChildrenBuilder.urlOptimizationResultPresenter(props: FactorioOptimizerProps) {
    optimizationResult {
        optimizationResult = props.optimizationResult
        logistics = props.logistics
        normalizeTarget = props.normalizeTarget
        changeMaterial = { props.changeUrl { target = it } }
        changeNormalizeTo = { props.changeUrl { normalizeTarget = 1 / it } }
    }
}
