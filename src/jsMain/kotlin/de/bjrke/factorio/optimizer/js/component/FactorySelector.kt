package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Props
import web.html.InputType
import web.html.checkbox
import web.html.radio

external interface FactorySelectorStateProps : Props {
    var selectedFactories: Set<Factory>
    var config: FactoryConfig
}

external interface FactorySelectorDispatchProps : Props {
    var selectFactory: (Set<Factory>) -> Unit
}

external interface FactorySelectorProps : FactorySelectorStateProps, FactorySelectorDispatchProps

internal val factorySelector =
    FC<FactorySelectorProps>("factorySelector") { props ->
        // mutually exclusive groups: a single icon option always stays selected
        for (group in props.config.groups) {
            val groupName = group.joinToString("-") { it.name }
            for (factory in group) {
                selectableIcon(
                    name = factory.identifier,
                    selected = props.selectedFactories.contains(factory),
                    inputType = InputType.radio,
                    groupName = groupName,
                    onChange = {
                        props.selectFactory(props.config.add(props.selectedFactories, factory))
                    },
                )
            }
        }
        // independently toggled machines
        for (factory in props.config.singles) {
            val enabled = props.selectedFactories.contains(factory)
            selectableIcon(
                name = factory.identifier,
                selected = enabled,
                inputType = InputType.checkbox,
                onChange = {
                    props.selectFactory(
                        if (enabled) {
                            props.config.remove(props.selectedFactories, factory)
                        } else {
                            props.config.add(props.selectedFactories, factory)
                        },
                    )
                },
            )
        }
    }

fun ChildrenBuilder.urlFactorySelector(props: FactorioOptimizerProps) {
    factorySelector {
        config = props.factoryConfig
        selectedFactories = props.factories
        selectFactory = { props.changeUrl { factories = it } }
    }
}
