package de.bjrke.factorio.optimizer.component

import de.bjrke.factorio.optimizer.OptimizerAppState
import de.bjrke.factorio.optimizer.SelectFactory
import de.bjrke.factorio.optimizer.model.Factory
import de.bjrke.factorio.optimizer.model.selectFactory
import de.bjrke.factorio.optimizer.model.selectableFactories
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import react.*
import react.dom.input
import react.dom.label
import react.redux.rConnect
import redux.WrapperAction

interface FactorySelectorStateProps : RProps {
    var selectedFactories: Set<Factory>
    var availableFactories: List<Factory>
}

interface FactorySelectorDispatchProps : RProps {
    var selectFactory: (Set<Factory>) -> Unit
}

interface FactorySelectorProps : FactorySelectorStateProps, FactorySelectorDispatchProps

private val factorySelector = rFunction<FactorySelectorProps>("factorySelector") { props ->
    for (factory in props.availableFactories) {
        label {
            input(type = InputType.checkBox) {
                val enabled = props.selectedFactories.contains(factory)
                attrs.onChangeFunction = { _ ->
                    props.selectFactory(
                        selectFactory(
                            props.selectedFactories,
                            factory,
                            !enabled
                        )
                    )
                }
                // work around for https://github.com/JetBrains/kotlin-wrappers/issues/35
                attrs["checked"] = enabled
            }
            icon(factory.identifier)
        }
    }
}

private val reduxFactorySelector: RClass<RProps> =
    rConnect<OptimizerAppState, SelectFactory, WrapperAction, RProps, FactorySelectorStateProps, FactorySelectorDispatchProps, FactorySelectorProps>(
        { state, _ ->
            availableFactories = selectableFactories
            selectedFactories = state.factories
        },
        { dispatch, _ ->
            selectFactory = { dispatch(SelectFactory(it)) }
        }
    )(factorySelector)

fun RBuilder.reduxFactorySelector() = reduxFactorySelector {}
