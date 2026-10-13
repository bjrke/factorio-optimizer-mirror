package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.business.validNormalizeTarget
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.factoryProductionComparator
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import web.cssom.*
import web.html.InputType
import web.html.number

external interface OptimizationResultPresenterStateProps : Props {
    var optimizationResult: OptimizationResult
    var normalizeTarget: Double
}

external interface OptimizationResultPresenterDispatchProps : Props {
    var changeMaterial: (Material) -> Unit
    var changeNormalizeTo: (Double) -> Unit
}

external interface OptimizationResultPresenterProps :
    OptimizationResultPresenterStateProps, OptimizationResultPresenterDispatchProps

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
        onChange = {
            it.target.value.toDoubleOrNull()?.let { value ->
                validNormalizeTarget(amount / value)?.let { props.changeNormalizeTo(it) }
            }
        }
    }
}

private fun ChildrenBuilder.amountIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    amount: Double,
) {
    a {
        css { marginRight = 1.ch }
        onClick = { props.changeNormalizeTo(amount) }
        icon(name)
    }
}

private fun ChildrenBuilder.amountInputIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    amount: Double,
) {
    amountInput(props, amount)
    amountIcon(props, name, amount)
}

private val optimizationResult =
    FC<OptimizationResultPresenterProps>("optimizationResult") { props ->
        props.optimizationResult.factories.entries
            .sortedWith(factoryProductionComparator)
            .forEach { (material, factoryProduction) ->
                div {
                    css {
                        display = Display.inlineBlock
                        verticalAlign = VerticalAlign.middle
                        padding = 0.5.rem
                        backgroundColor = hashColor(material.name, 0.75)
                    }
                    amountInputIcon(
                        props,
                        factoryProduction.factory.identifier,
                        factoryProduction.amount,
                    )
                    amountIcon(props, factoryProduction.recipe.name, factoryProduction.amount)

                    div {
                        css {
                            display = Display.inlineBlock
                            verticalAlign = VerticalAlign.middle
                            // recipes without ingredients (mining, pumping, ...) still reserve a
                            // cell here so every recipe block keeps the same width
                            minWidth = 7.5.rem
                        }
                        factoryProduction.recipe.ingredients.keys.forEach {
                            div {
                                amountInputIcon(
                                    props,
                                    it.name,
                                    factoryProduction.consumption(it),
                                )
                            }
                        }
                    }

                    div {
                        css {
                            display = Display.inlineBlock
                            verticalAlign = VerticalAlign.middle
                        }
                        factoryProduction.recipe.result.keys.forEach {
                            div {
                                amountInputIcon(props, it.name, factoryProduction.production(it))
                            }
                        }
                    }

                    button {
                        +">"
                        onClick = { props.changeMaterial(material) }
                        title = "select " + material.name
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
        normalizeTarget = props.normalizeTarget
        changeMaterial = { props.changeUrl { target = it } }
        changeNormalizeTo = { props.changeUrl { normalizeTarget = 1 / it } }
    }
}
