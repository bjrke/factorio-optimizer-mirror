package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.iconLayers
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import web.cssom.Display
import web.cssom.Position
import web.cssom.Transform
import web.cssom.VerticalAlign
import web.cssom.deg
import web.cssom.px
import web.cssom.rotate
import web.cssom.scalex

external interface IconProps : Props {
    var name: String
    var size: Int?
    var alt: String?

    /**
     * Draws the icon into an explicit box, overriding [size]. Used for non-square footprints, where
     * the icon is stretched to fill the entity's area.
     */
    var width: Double?
    var height: Double?

    /** Rotates the icon this many degrees clockwise, e.g. to turn a belt into a machine. */
    var rotation: Int?

    /** Mirrors the icon across the vertical axis, e.g. to point an inserter out of a machine. */
    var flipHorizontal: Boolean?
}

/** The CSS transform that turns an upright icon into the [rotation]/[flipHorizontal] variant. */
private fun iconTransform(rotation: Int?, flipHorizontal: Boolean?): Transform? =
    when {
        rotation != null -> rotate(rotation.deg)
        flipHorizontal == true -> scalex(-1)
        else -> null
    }

private val iconX =
    FC<IconProps>("icon") { props ->
        val size = props.size ?: 32
        val iconWidth = props.width ?: size.toDouble()
        val iconHeight = props.height ?: size.toDouble()
        val label = props.alt ?: props.name
        val transform = iconTransform(props.rotation, props.flipHorizontal)
        val layers = iconLayers(props.name)
        if (layers.size == 1) {
            img {
                src = "icons/${layers.single().file}.png"
                alt = label
                title = label
                width = iconWidth
                height = iconHeight
                css {
                    verticalAlign = VerticalAlign.middle
                    transform?.let { this.transform = it }
                }
            }
        } else {
            span {
                title = label
                css {
                    position = Position.relative
                    display = Display.inlineBlock
                    width = iconWidth.px
                    height = iconHeight.px
                    verticalAlign = VerticalAlign.middle
                    transform?.let { this.transform = it }
                }
                for ((index, layer) in layers.withIndex()) {
                    val layerWidth = iconWidth * layer.scale
                    val layerHeight = iconHeight * layer.scale
                    val left = layer.left?.times(iconWidth) ?: ((iconWidth - layerWidth) / 2.0)
                    val top = layer.top?.times(iconHeight) ?: ((iconHeight - layerHeight) / 2.0)
                    img {
                        src = "icons/${layer.file}.png"
                        alt = if (index == 0) label else ""
                        css {
                            position = Position.absolute
                            width = layerWidth.px
                            height = layerHeight.px
                            this.left = left.px
                            this.top = top.px
                        }
                    }
                }
            }
        }
    }

fun ChildrenBuilder.icon(name: String, handler: IconProps.() -> Unit = {}) {
    iconX {
        this.name = name
        handler()
    }
}
