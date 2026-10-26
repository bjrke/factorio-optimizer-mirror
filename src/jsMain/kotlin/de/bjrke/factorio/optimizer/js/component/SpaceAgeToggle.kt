// React convention: the file is named after the component, the props type keeps its suffix.
@file:Suppress("MatchingDeclarationName")

package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Props
import web.html.InputType
import web.html.checkbox

external interface SpaceAgeToggleProps : Props {
    var enabled: Boolean
    var toggle: (Boolean) -> Unit
}

/** Space Age is a plain on/off switch: the logo carries the label via its `alt` text. */
internal val spaceAgeToggle =
    FC<SpaceAgeToggleProps>("spaceAgeToggle") { props ->
        selectableIcon(
            name = "space-age",
            alt = "Space Age",
            selected = props.enabled,
            inputType = InputType.checkbox,
            onChange = { props.toggle(!props.enabled) },
        )
    }

fun ChildrenBuilder.urlSpaceAgeToggle(props: FactorioOptimizerProps) {
    spaceAgeToggle {
        enabled = props.spaceAgeEnabled
        toggle = { enabled -> props.changeUrl { spaceAgeEnabled = enabled } }
    }
}
