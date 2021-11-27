package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.roundN
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.js.ChangeMaterial
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import de.bjrke.factorio.optimizer.js.NormalizeTo
import de.bjrke.factorio.optimizer.js.OptimizerAppState
import kotlinx.css.Display
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
import react.dom.a
import react.dom.attrs
import react.dom.button
import react.fc
import react.invoke
import react.redux.rConnect
import redux.RAction
import redux.WrapperAction
import styled.css
import styled.styledDiv
import styled.styledInput

interface OptimizationResultPresenterStateProps : Props {
    var optimizationResult: OptimizationResult
    var normalizeTarget: Double
}

interface OptimizationResultPresenterDispatchProps : Props {
    var changeMaterial: (Material) -> Unit
    var changeNormalizeTo: (Double) -> Unit
}

interface OptimizationResultPresenterProps :
    OptimizationResultPresenterStateProps, OptimizationResultPresenterDispatchProps

private fun RBuilder.optimizationResult(props: OptimizationResultPresenterProps) {
    fun RBuilder.amount(name: String, amount: Double) {
        a {
            attrs { onClickFunction = { props.changeNormalizeTo(amount) } }
            icon(name)
            +" x "
        }
        styledInput(type = InputType.number) {
            css { width = 5.rem }
            attrs.onChangeFunction =
                { event ->
                    props.changeNormalizeTo(
                        amount / (event.target as HTMLInputElement).value.toDouble()
                    )
                }
            attrs.value = roundN(amount * props.normalizeTarget).toString()
        }
    }

    props.optimizationResult.factories.forEach { (material, factoryProduction) ->
        styledDiv {
            css {
                display = Display.inlineBlock
                marginRight = 1.rem
            }
            amount(factoryProduction.factory.identifier, factoryProduction.amount)
            +" "
            amount(material.name, factoryProduction.production(material))
            +" "
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
