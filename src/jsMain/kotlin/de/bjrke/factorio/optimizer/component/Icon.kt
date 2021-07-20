package de.bjrke.factorio.optimizer.component

import kotlinx.css.VerticalAlign
import kotlinx.css.verticalAlign
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.dom.attrs
import react.rFunction
import styled.css
import styled.styledImg

interface IconProps : RProps {
    var name: String
    var size: Int?
}

private fun correctName(s: String) =
    when (s) {
        "stone-wall" -> "wall"
        else -> s
    }

private val iconX =
    rFunction<IconProps>("icon") { props ->
        val size = props.size ?: 32
        styledImg(src = "icons/${correctName(props.name)}.png") {
            css { verticalAlign = VerticalAlign.middle }
            attrs {
                width = "$size"
                height = "$size"
                alt = props.name
                title = props.name
            }
        }
    }

fun RBuilder.icon(name: String, handler: IconProps.() -> Unit = {}) = iconX {
    attrs.name = name
    attrs(handler)
}
