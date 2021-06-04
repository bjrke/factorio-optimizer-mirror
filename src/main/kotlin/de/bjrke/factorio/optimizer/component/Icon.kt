package de.bjrke.factorio.optimizer.component

import kotlinx.css.*
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

private val iconX =
    rFunction<IconProps>("icon") { props ->
        val size = props.size ?: 32
        styledImg(src = "icons/${props.name}.png") {
            css {
                width = size.px
                height = size.px
                verticalAlign = VerticalAlign.middle
                marginRight = 5.px
            }
            attrs {
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
