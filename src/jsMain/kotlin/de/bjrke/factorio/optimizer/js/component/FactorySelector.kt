package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Factories.removeFactory
import de.bjrke.factorio.optimizer.common.model.Factories.selectableFactories
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.addFactory
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.html.InputType
import web.html.checkbox

external interface FactorySelectorStateProps : Props {
    var selectedFactories: Set<Factory>
    var availableFactories: List<Factory>
}

external interface FactorySelectorDispatchProps : Props {
    var selectFactory: (Set<Factory>) -> Unit
}

external interface FactorySelectorProps : FactorySelectorStateProps, FactorySelectorDispatchProps

private val factorySelector =
    FC<FactorySelectorProps>("factorySelector") { props ->
        for (factory in props.availableFactories) {
            label {
                val enabled = props.selectedFactories.contains(factory)
                input {
                    type = InputType.Companion.checkbox
                    checked = enabled
                    onChange = { _ ->
                        props.selectFactory(
                            if (enabled) {
                                props.selectedFactories.removeFactory(factory)
                            } else {
                                props.selectedFactories.addFactory(factory)
                            }
                        )
                    }
                }
                icon(factory.identifier)
            }
        }
    }

fun ChildrenBuilder.urlFactorySelector(props: FactorioOptimizerProps) {
    factorySelector {
        availableFactories = selectableFactories
        selectedFactories = props.factories
        selectFactory = { props.changeUrl { factories = it } }
    }
}
