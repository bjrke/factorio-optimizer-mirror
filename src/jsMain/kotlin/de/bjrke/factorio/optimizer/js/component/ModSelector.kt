package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.RecipeProvider
import de.bjrke.factorio.optimizer.js.BASE_MOD
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.html.InputType
import web.html.checkbox

external interface ModSelectorStateProps : Props {
    var activeMods: Set<String>
    var availableMods: List<String>
}

external interface ModSelectorDispatchProps : Props {
    var selectMods: (Set<String>) -> Unit
}

external interface ModSelectorProps : ModSelectorStateProps, ModSelectorDispatchProps

private val modSelector =
    FC<ModSelectorProps>("modSelector") { props ->
        for (mod in props.availableMods) {
            label {
                val enabled = props.activeMods.contains(mod)
                val isBase = mod == BASE_MOD
                input {
                    type = InputType.Companion.checkbox
                    checked = enabled
                    disabled = isBase
                    onChange = { _ ->
                        props.selectMods(
                            if (enabled) {
                                props.activeMods - mod
                            } else {
                                props.activeMods + mod
                            }
                        )
                    }
                }
                +mod
            }
        }
    }

fun ChildrenBuilder.urlModSelector(props: FactorioOptimizerProps) {
    modSelector {
        availableMods = RecipeProvider.availableMods()
        activeMods = RecipeProvider.resolveMods(props.mods)
        selectMods = { props.changeUrl { mods = it } }
    }
}
