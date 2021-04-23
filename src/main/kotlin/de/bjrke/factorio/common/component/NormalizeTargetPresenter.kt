package de.bjrke.factorio.common.component

import de.bjrke.factorio.optimizer.NormalizeTo
import de.bjrke.factorio.optimizer.OptimizerAppState
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.input
import react.redux.rConnect
import redux.RAction
import redux.WrapperAction

interface NormalizeTargetPresenterStateProps : RProps {
    var normalizeTarget: Double
}

interface NormalizeTargetPresenterDispatchProps : RProps {
    var changeNormalizeTo: (Double) -> Unit
}

interface NormalizeTargetPresenterProps : NormalizeTargetPresenterStateProps,
    NormalizeTargetPresenterDispatchProps


class NormalizeTargetPresenter(props: NormalizeTargetPresenterProps) :
    RComponent<NormalizeTargetPresenterProps, RState>(props) {

    override fun RBuilder.render() {
        input {
            attrs {
                type = InputType.number
                onChangeFunction =
                    { event -> props.changeNormalizeTo((event.target as HTMLInputElement).value.toDouble()) }
                value = props.normalizeTarget.toString()
            }
        }
    }
}

val normalizeTargetPresenter: RClass<RProps> =
    rConnect<OptimizerAppState, RAction, WrapperAction, RProps, NormalizeTargetPresenterStateProps, NormalizeTargetPresenterDispatchProps, NormalizeTargetPresenterProps>(
        { state, _ ->
            normalizeTarget = 1 / state.normalizeTarget
        },
        { dispatch, _ ->
            changeNormalizeTo = { dispatch(NormalizeTo(1 / it)) }
        }
    )(NormalizeTargetPresenter::class.rClass)
