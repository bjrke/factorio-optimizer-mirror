package de.bjrke.factorio.optimizer.js.component

import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.cssom.ClassName
import web.html.InputType

external interface SelectableIconProps : Props {
    var name: String
    var alt: String?
    var selected: Boolean
    var disabled: Boolean
    var inputType: InputType
    var groupName: String?
    var onChange: () -> Unit
}

/**
 * An icon that acts as a selectable option: the native input is kept (visually hidden) for
 * keyboard/screen-reader semantics, while the icon's background shows the selected state.
 */
private val selectableIconX =
    FC<SelectableIconProps>("selectableIcon") { props ->
        label {
            className =
                ClassName(
                    listOfNotNull(
                            "icon-option",
                            if (props.selected) "selected" else null,
                            if (props.disabled) "disabled" else null,
                        )
                        .joinToString(" ")
                )
            input {
                className = ClassName("visually-hidden")
                type = props.inputType
                props.groupName?.let { name = it }
                checked = props.selected
                disabled = props.disabled
                onChange = { props.onChange() }
            }
            icon(props.name) { alt = props.alt }
        }
    }

fun ChildrenBuilder.selectableIcon(
    name: String,
    selected: Boolean,
    inputType: InputType,
    groupName: String? = null,
    disabled: Boolean = false,
    alt: String? = null,
    onChange: () -> Unit,
) {
    selectableIconX {
        this.name = name
        this.alt = alt
        this.selected = selected
        this.inputType = inputType
        this.groupName = groupName
        this.disabled = disabled
        this.onChange = onChange
    }
}
