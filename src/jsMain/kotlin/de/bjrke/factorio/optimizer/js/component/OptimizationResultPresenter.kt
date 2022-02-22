package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.roundN
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.js.ChangeMaterial
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import de.bjrke.factorio.optimizer.js.NormalizeTo
import de.bjrke.factorio.optimizer.js.OptimizerAppState
import kotlinx.css.Display
import kotlinx.css.ch
import kotlinx.css.display
import kotlinx.css.marginRight
import kotlinx.css.rem
import kotlinx.css.width
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RBuilder
import react.dom.attrs
import react.dom.button
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
                width = 5.rem
                marginRight = 1.ch
            }
            attrs.onChangeFunction =
                { event ->
                    props.changeNormalizeTo(
                        amount / (event.target as HTMLInputElement).value.toDouble()
                    )
                }
            attrs.value = roundN(amount * props.normalizeTarget).toString()
        }
    }

    fun RBuilder.amountIcon(name: String, amount: Double) {
        styledA {
            css { marginRight = 1.ch }
            attrs { onClickFunction = { props.changeNormalizeTo(amount) } }
            icon(name)
        }
    }

    val sortedEntries =
        props.optimizationResult.factories.entries.sortedWith(
            compareBy({ it.value.factory }, { it.value.recipe.name })
        )
    sortedEntries.forEach { (material, factoryProduction) ->
        styledDiv {
            css {
                display = Display.inlineBlock
                marginRight = 2.rem
            }
            amountInput(factoryProduction.amount)
            amountIcon(factoryProduction.factory.identifier, factoryProduction.amount)

            val amount = factoryProduction.production(material)
            amountIcon(material.name, amount)
            amountInput(amount)
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

private val reduxOptimizationResultPresenter =
    rConnect<
        OptimizerAppState,
        RAction,
        WrapperAction,
        Props,
        OptimizationResultPresenterStateProps,
        OptimizationResultPresenterDispatchProps,
        OptimizationResultPresenterProps>(
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
