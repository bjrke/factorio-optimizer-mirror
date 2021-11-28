package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Factories.removeFactory
import de.bjrke.factorio.optimizer.common.model.Factories.selectableFactories
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.addFactory
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import de.bjrke.factorio.optimizer.js.OptimizerAppState
import de.bjrke.factorio.optimizer.js.SelectFactory
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import react.Props
import react.RBuilder
import react.dom.input
import react.dom.label
import react.fc
import react.invoke
import react.redux.rConnect
import redux.WrapperAction

external interface FactorySelectorStateProps : Props {
    var selectedFactories: Set<Factory>
    var availableFactories: List<Factory>
}

external interface FactorySelectorDispatchProps : Props {
    var selectFactory: (Set<Factory>) -> Unit
}

external interface FactorySelectorProps : FactorySelectorStateProps, FactorySelectorDispatchProps

private fun RBuilder.facorySelector(props: FactorySelectorProps) {
    for (factory in props.availableFactories) {
        label {
            input(type = InputType.checkBox) {
                val enabled = props.selectedFactories.contains(factory)
                attrs.onChangeFunction =
                    { _ ->
                        props.selectFactory(
                            if (enabled) {
                                props.selectedFactories.removeFactory(factory)
                            } else {
                                props.selectedFactories.addFactory(factory)
                            }
                        )
                    }
                // work around for https://github.com/JetBrains/kotlin-wrappers/issues/35
                attrs["checked"] = enabled
            }
            icon(factory.identifier)
        }
    }
}

private val reduxFactorySelector =
    rConnect<
        OptimizerAppState,
        SelectFactory,
        WrapperAction,
        Props,
        FactorySelectorStateProps,
        FactorySelectorDispatchProps,
        FactorySelectorProps>(
        { state, _ ->
            availableFactories = selectableFactories
            selectedFactories = state.factories
        },
        { dispatch, _ -> selectFactory = { dispatch(SelectFactory(it)) } }
    )(RBuilder::facorySelector)

fun RBuilder.reduxFactorySelector() = reduxFactorySelector {}

fun RBuilder.urlFactorySelector(props: FactorioOptimizerProps) {
    child(fc(RBuilder::facorySelector)) {
        attrs {
            availableFactories = selectableFactories
            selectedFactories = props.factories
            selectFactory = { props.changeUrl { factories = it } }
        }
    }
}
