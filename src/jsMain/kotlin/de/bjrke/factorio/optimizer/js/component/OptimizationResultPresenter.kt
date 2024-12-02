package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.factoryProductionComparator
import de.bjrke.factorio.optimizer.js.ChangeMaterial
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import de.bjrke.factorio.optimizer.js.NormalizeTo
import de.bjrke.factorio.optimizer.js.OptimizerAppState
import kotlinx.css.*
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RBuilder
import react.dom.attrs
import react.dom.button
import react.dom.div
import react.fc
import react.invoke
import react.redux.rConnect
import redux.RAction
import redux.WrapperAction
import styled.css
import styled.styledA
import styled.styledDiv
import styled.styledInput

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

private fun RBuilder.optimizationResult(props: OptimizationResultPresenterProps) {
    fun RBuilder.amountInput(amount: Double) {
        styledInput(type = InputType.number) {
            css {
                width = 4.rem
                marginRight = 1.ch
            }
            attrs.onChangeFunction = { event ->
                props.changeNormalizeTo(
                    amount / (event.target as HTMLInputElement).value.toDouble()
                )
            }
            attrs.value = superRound(amount * props.normalizeTarget).toString()
        }
    }

    fun RBuilder.amountIcon(name: String, amount: Double) {
        styledA {
            css { marginRight = 1.ch }
            attrs { onClickFunction = { props.changeNormalizeTo(amount) } }
            icon(name)
        }
    }

    fun RBuilder.amountIconInput(name: String, amount: Double) {
        amountIcon(name, amount)
        amountInput(amount)
    }

    fun RBuilder.amountInputIcon(name: String, amount: Double) {
        amountInput(amount)
        amountIcon(name, amount)
    }

    props.optimizationResult.factories.entries.sortedWith(factoryProductionComparator).forEach {
        (material, factoryProduction) ->
        styledDiv {
            css {
                display = Display.inlineBlock
                verticalAlign = VerticalAlign.middle
                padding = Padding(all = 0.5.rem)
                backgroundColor = hashColor(material.name).withAlpha(0.75)
            }
            amountInputIcon(factoryProduction.factory.identifier, factoryProduction.amount)
            amountIcon(factoryProduction.recipe.name, factoryProduction.amount)

            styledDiv {
                css {
                    display = Display.inlineBlock
                    verticalAlign = VerticalAlign.middle
                }
                val ingredients = factoryProduction.recipe.ingredients
                if (ingredients.isEmpty()) {
                    // this is duplication of the recipe output to avoid breaking the layout
                    factoryProduction.recipe.result.keys.forEach {
                        div { amountInputIcon(it.name, factoryProduction.production(it)) }
                    }
                } else {
                    ingredients.keys.forEach {
                        div { amountInputIcon(it.name, factoryProduction.consumption(it)) }
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    verticalAlign = VerticalAlign.middle
                }
                factoryProduction.recipe.result.keys.forEach {
                    div { amountInputIcon(it.name, factoryProduction.production(it)) }
                }
            }

            button {
                +">"
                attrs {
                    onClickFunction = { props.changeMaterial(material) }
                    title = "select " + material.name
                }
            }
        }
    }
}

fun hashColor(s: String): Color {
    val hash = s.hashCode()
    val r = hash and 0x7F0000 shr 16
    val g = hash and 0x007F00 shr 8
    val b = hash and 0x00007F

    return Color("rgb(${r + 128}, ${g + 128}, ${b + 128})")
}

private val reduxOptimizationResultPresenter =
    rConnect<
        OptimizerAppState,
        RAction,
        WrapperAction,
        Props,
        OptimizationResultPresenterStateProps,
        OptimizationResultPresenterDispatchProps,
        OptimizationResultPresenterProps
    >(
        { state, _ ->
            optimizationResult = state.optimizationResult
            normalizeTarget = state.normalizeTarget
        },
        { dispatch, _ ->
            changeMaterial = { dispatch(ChangeMaterial(it)) }
            changeNormalizeTo = { dispatch(NormalizeTo(1 / it)) }
        }
    )(RBuilder::optimizationResult)

fun RBuilder.reduxOptimizationResultPresenter() = reduxOptimizationResultPresenter {}

fun RBuilder.urlOptimizationResultPresenter(props: FactorioOptimizerProps) {
    child(fc(RBuilder::optimizationResult)) {
        attrs {
            optimizationResult = props.optimizationResult
            normalizeTarget = props.normalizeTarget
            changeMaterial = { props.changeUrl { target = it } }
            changeNormalizeTo = { props.changeUrl { normalizeTarget = 1 / it } }
        }
    }
}
