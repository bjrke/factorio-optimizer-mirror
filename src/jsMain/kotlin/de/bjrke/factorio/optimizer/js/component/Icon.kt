package de.bjrke.factorio.optimizer.js.component

import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.img
import web.cssom.VerticalAlign

external interface IconProps : Props {
    var name: String
    var size: Int?
}

private fun correctName(s: String) =
    when {
        s == "stone-wall" -> "wall"
        s == "raw-fish" -> "fish"
        s.endsWith("-rotting") -> s.removeSuffix("-rotting")
        else -> s
    }

private val iconX =
    FC<IconProps>("icon") { props ->
        val size = props.size ?: 32
        img {
            src = "icons/${correctName(props.name)}.png"
            alt = props.name
            title = props.name
            width = size.toDouble()
            height = size.toDouble()
            css {
                verticalAlign = VerticalAlign.middle
            }
        }
    }

fun ChildrenBuilder.icon(name: String, handler: IconProps.() -> Unit = {}) {
    iconX {
        this.name = name
        handler()
    }
}
