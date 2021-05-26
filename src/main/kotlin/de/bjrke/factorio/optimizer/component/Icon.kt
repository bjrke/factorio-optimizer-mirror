package de.bjrke.factorio.optimizer.component

import kotlinx.css.*
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import styled.css
import styled.styledImg

interface IconProps : RProps {
    var name: String
    var size: Int?
}

class Icon(props: IconProps) : RComponent<IconProps, RState>(props) {
    override fun RBuilder.render() {
        val size = props.size ?: 32
        styledImg(src = "icons/${props.name}.png") {
            css {
                width = size.px
                height = size.px
                objectFit = ObjectFit.cover
                objectPosition = "0 0"
            }
            attrs.height = "$size"
        }
    }
}

fun RBuilder.icon(handler: IconProps.() -> Unit) = child(Icon::class) { attrs(handler) }
