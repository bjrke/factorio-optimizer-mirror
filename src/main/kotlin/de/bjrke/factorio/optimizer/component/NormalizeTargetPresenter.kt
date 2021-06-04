package de.bjrke.factorio.optimizer.component

import de.bjrke.factorio.optimizer.FactorioOptimizerProps
import de.bjrke.factorio.optimizer.NormalizeTo
import de.bjrke.factorio.optimizer.OptimizerAppState
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RProps
import react.dom.input
import react.invoke
import react.rFunction
import react.redux.rConnect
import redux.RAction
import redux.WrapperAction

interface NormalizeTargetPresenterStateProps : RProps {
    var normalizeTarget: Double
}

interface NormalizeTargetPresenterDispatchProps : RProps {
    var changeNormalizeTo: (Double) -> Unit
}

interface NormalizeTargetPresenterProps :
    NormalizeTargetPresenterStateProps, NormalizeTargetPresenterDispatchProps

val normalizeTargetPresenter =
    rFunction<NormalizeTargetPresenterProps>("normalizeTargetPresenter") { props ->
        input(type = InputType.number) {
            attrs.onChangeFunction =
                { event ->
                    props.changeNormalizeTo((event.target as HTMLInputElement).value.toDouble())
                }
            attrs.value = props.normalizeTarget.toString()
        }
    }

private val reduxNormalizeTargetPresenter =
    rConnect<
        OptimizerAppState,
        RAction,
        WrapperAction,
        RProps,
        NormalizeTargetPresenterStateProps,
        NormalizeTargetPresenterDispatchProps,
        NormalizeTargetPresenterProps>(
        { state, _ -> normalizeTarget = state.normalizeTarget },
        { dispatch, _ -> changeNormalizeTo = { dispatch(NormalizeTo(it)) } }
    )(normalizeTargetPresenter)

fun RBuilder.reduxNormalizeTargetPresenter() = reduxNormalizeTargetPresenter {}

fun RBuilder.urlNormalizeTargetPresenter(props: FactorioOptimizerProps) {
    normalizeTargetPresenter {
        attrs {
            normalizeTarget = props.normalizeTarget
            changeNormalizeTo = { props.changeUrl { normalizeTarget = it } }
        }
    }
}
