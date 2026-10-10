package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Factories.factoryGroups
import de.bjrke.factorio.optimizer.common.model.Factories.removeFactory
import de.bjrke.factorio.optimizer.common.model.Factories.singleFactories
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
import web.html.radio

external interface FactorySelectorStateProps : Props {
    var selectedFactories: Set<Factory>
    var groups: List<List<Factory>>
    var singles: List<Factory>
}

external interface FactorySelectorDispatchProps : Props {
    var selectFactory: (Set<Factory>) -> Unit
}

external interface FactorySelectorProps : FactorySelectorStateProps, FactorySelectorDispatchProps

private val factorySelector =
    FC<FactorySelectorProps>("factorySelector") { props ->
        // mutually exclusive groups: radio buttons, one option always stays selected
        for (group in props.groups) {
            val groupName = group.joinToString("-") { it.name }
            for (factory in group) {
                label {
                    input {
                        type = InputType.Companion.radio
                        name = groupName
                        checked = props.selectedFactories.contains(factory)
                        onChange = { _ ->
                            props.selectFactory(props.selectedFactories.addFactory(factory))
                        }
                    }
                    icon(factory.identifier)
                }
            }
        }
        // independently toggled machines: checkboxes
        for (factory in props.singles) {
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
        groups = factoryGroups
        singles = singleFactories
        selectedFactories = props.factories
        selectFactory = { props.changeUrl { factories = it } }
    }
}
