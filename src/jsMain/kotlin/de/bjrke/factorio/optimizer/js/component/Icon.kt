package de.bjrke.factorio.optimizer.js.component

import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import web.cssom.Display
import web.cssom.Position
import web.cssom.VerticalAlign
import web.cssom.px

external interface IconProps : Props {
    var name: String
    var size: Int?
}

/**
 * A single image layer of an icon. [scale] and the optional [left]/[top] (fractions of the icon
 * box) mirror Factorio's `icons` layering; the default centres the layer.
 */
private data class IconLayer(
    val file: String,
    val scale: Double = 1.0,
    val left: Double? = null,
    val top: Double? = null,
)

/** Maps the few prototype names whose icon file differs from the prototype name. */
private fun iconName(s: String) =
    when (s) {
        "stone-wall" -> "wall"
        "raw-fish" -> "fish"
        "asteroid-collection" -> "asteroid-collector"
        else -> s
    }

/**
 * Factorio composes the auto-generated `*-recycling` recipe icon from the recycling background, the
 * recycled item and the recycling top overlay (see `recycler/recycling.lua`). The `data.raw` dump
 * drops those icons, so the layers are reconstructed from the recipe name; the recycled item equals
 * the name suffix. The synthetic `*-spoiling` recipes get the spoiling item plus a spoilage badge.
 */
private fun layersFor(name: String): List<IconLayer> =
    when {
        name.endsWith("-recycling") ->
            listOf(
                IconLayer("recycling"),
                IconLayer(iconName(name.removeSuffix("-recycling")), 0.6),
                IconLayer("recycling-top"),
            )
        name.endsWith("-spoiling") ->
            listOf(
                IconLayer(iconName(name.removeSuffix("-spoiling"))),
                IconLayer("spoilage", 0.5, left = 0.5, top = 0.5),
            )
        else -> listOf(IconLayer(iconName(name)))
    }

private val iconX =
    FC<IconProps>("icon") { props ->
        val size = props.size ?: 32
        val layers = layersFor(props.name)
        if (layers.size == 1) {
            img {
                src = "icons/${layers.single().file}.png"
                alt = props.name
                title = props.name
                width = size.toDouble()
                height = size.toDouble()
                css { verticalAlign = VerticalAlign.middle }
            }
        } else {
            span {
                title = props.name
                css {
                    position = Position.relative
                    display = Display.inlineBlock
                    width = size.px
                    height = size.px
                    verticalAlign = VerticalAlign.middle
                }
                for ((index, layer) in layers.withIndex()) {
                    val layerSize = size * layer.scale
                    val left = layer.left?.times(size) ?: (size - layerSize) / 2.0
                    val top = layer.top?.times(size) ?: (size - layerSize) / 2.0
                    img {
                        src = "icons/${layer.file}.png"
                        alt = if (index == 0) props.name else ""
                        css {
                            position = Position.absolute
                            width = layerSize.px
                            height = layerSize.px
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
