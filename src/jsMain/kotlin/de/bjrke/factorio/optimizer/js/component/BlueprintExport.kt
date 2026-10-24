// The dialog props are read below; light-mode detekt cannot see external interface members.
@file:Suppress("UnusedParameter")

package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.fromPlaced
import de.bjrke.factorio.optimizer.common.blueprint.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.model.Blueprint
import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.blueprint.output.blueprintString
import de.bjrke.factorio.optimizer.common.blueprint.planning.blueprintPlan
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Key
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.dom.svg.ReactSVG.foreignObject
import react.dom.svg.ReactSVG.svg
import react.useState
import web.cssom.ClassName
import web.cssom.Display
import web.cssom.Position
import web.cssom.pct
import web.cssom.px

private const val TILE = 24.0

/** Turns an entity's direction into the clockwise rotation its icon needs; icons face east. */
private fun rotationDegrees(direction: Direction?): Int? = direction?.let {
    when (it) {
        Direction.NORTH -> 270
        Direction.EAST -> 0
        Direction.SOUTH -> 90
        Direction.WEST -> 180
    }
}

/**
 * The clockwise rotation the entity's icon needs on screen. Most icons face east, but a loader's
 * arrow points north and a splitter's arrows point west; an underground exit opens the other way
 * than the belt it feeds. The blueprint itself is never changed, only the preview.
 */
internal fun blueprintDisplayRotation(entity: BlueprintEntity): Int? {
    val base = rotationDegrees(entity.direction) ?: return null
    val extra =
        when {
            entity.name.endsWith("loader") -> 90
            entity.name.endsWith("splitter") -> 180
            entity.name.endsWith("underground-belt") && entity.type == Io.OUTPUT -> 180
            else -> 0
        }
    return ((base + extra) % 360).takeIf { it != 0 }
}

/** The recipe or material signal an entity displays, or null when it shows nothing. */
internal fun blueprintOverlay(entity: BlueprintEntity): String? =
    entity.recipe ?: entity.marker?.name ?: entity.infinity?.item?.name

private fun copyToClipboard(text: String) {
    js("navigator.clipboard && navigator.clipboard.writeText(text)")
}

external interface BlueprintViewProps : Props {
    var blueprint: Blueprint
}

/**
 * Renders the blueprint as an icon grid inside an SVG, so the whole plan scales down to whatever
 * space the dialog offers. Each entity's icon fills its tiles; a directional entity is rotated to
 * point the way its blueprinted [Direction] runs without changing the blueprint, and one carrying a
 * recipe or material shows that signal centred on top.
 */
internal val blueprintView =
    FC<BlueprintViewProps>("blueprintView") { props ->
        val entities = props.blueprint.entities
        val minX = entities.minOfOrNull { it.area.topLeft.x } ?: 0
        val minY = entities.minOfOrNull { it.area.topLeft.y } ?: 0
        val maxX = entities.maxOfOrNull { it.area.bottomRight.x } ?: 0
        val maxY = entities.maxOfOrNull { it.area.bottomRight.y } ?: 0
        val gridWidth = (1 + maxX - minX) * TILE
        val gridHeight = (1 + maxY - minY) * TILE

        svg {
            viewBox = "0 0 $gridWidth $gridHeight"
            preserveAspectRatio = "xMidYMid meet"
            css {
                width = 100.pct
                height = 100.pct
                display = Display.block
            }
            foreignObject {
                this.width = gridWidth
                this.height = gridHeight
                div {
                    className = ClassName("blueprint-view")
                    css {
                        position = Position.relative
                        width = gridWidth.px
                        height = gridHeight.px
                        backgroundColor = web.cssom.Color("var(--factory-panel, #fff)")
                    }
                    for ((index, entity) in entities.withIndex()) {
                        val cell = entity.area
                        val cellWidth = cell.width * TILE
                        val cellHeight = cell.height * TILE
                        val iconRotation = blueprintDisplayRotation(entity)
                        // a quarter turn swaps the icon's box, so the rotated icon still fills the
                        // cell
                        val quarterTurn = iconRotation != null && iconRotation % 180 != 0
                        val iconWidth = if (quarterTurn) cellHeight else cellWidth
                        val iconHeight = if (quarterTurn) cellWidth else cellHeight
                        div {
                            key = Key(index.toString())
                            css {
                                position = Position.absolute
                                left = ((cell.topLeft.x - minX) * TILE).px
                                top = ((cell.topLeft.y - minY) * TILE).px
                                width = cellWidth.px
                                height = cellHeight.px
                            }
                            div {
                                css {
                                    position = Position.absolute
                                    left = ((cellWidth - iconWidth) / 2).px
                                    top = ((cellHeight - iconHeight) / 2).px
                                    width = iconWidth.px
                                    height = iconHeight.px
                                }
                                icon(entity.name) {
                                    width = iconWidth
                                    height = iconHeight
                                    iconRotation?.let { rotation = it }
                                }
                            }
                            blueprintOverlay(entity)?.let { overlay ->
                                // the overlay stays square: an unstretched tile on a scaled entity,
                                // otherwise half the entity, centred on it in both cases
                                val overlaySize =
                                    if (cellWidth == cellHeight) cellWidth / 2 else TILE
                                div {
                                    className = ClassName("blueprint-overlay-icon")
                                    css {
                                        position = Position.absolute
                                        left = ((cellWidth - overlaySize) / 2).px
                                        top = ((cellHeight - overlaySize) / 2).px
                                        width = overlaySize.px
                                        height = overlaySize.px
                                    }
                                    icon(overlay) { size = overlaySize.toInt() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

external interface BlueprintDialogProps : Props {
    var label: String

    // read by the dialog below; light-mode detekt cannot see external interface members
    var text: String
    var blueprint: Blueprint
    var onClose: () -> Unit
}

internal val blueprintDialog =
    FC<BlueprintDialogProps>("blueprintDialog") { props ->
        div {
            className = ClassName("blueprint-overlay")
            role = AriaRole.dialog
            onClick = { props.onClose() }
            div {
                className = ClassName("blueprint-dialog")
                onClick = { it.stopPropagation() }
                div {
                    className = ClassName("blueprint-toolbar")
                    span {
                        className = ClassName("blueprint-label")
                        +props.label
                    }
                    button {
                        onClick = { copyToClipboard(props.text) }
                        +"Copy"
                    }
                    button {
                        onClick = { props.onClose() }
                        +"Close"
                    }
                }
                textarea {
                    className = ClassName("blueprint-string")
                    readOnly = true
                    value = props.text
                }
                div {
                    className = ClassName("blueprint-preview")
                    blueprintView { blueprint = props.blueprint }
                }
            }
        }
    }

external interface BlueprintButtonProps : Props {
    var outcome: BlueprintPlanResult
    var logistics: LogisticsCalculator
    var beltEnd: BeltEnd
}

internal const val BUTTON_TEXT = "Blueprint(beta)"

internal val blueprintButton =
    FC<BlueprintButtonProps>("blueprintButton") { props ->
        val (open, setOpen) = useState(false)
        when (val outcome = props.outcome) {
            is BlueprintPlanResult.Unavailable ->
                button {
                    disabled = true
                    title = outcome.reason
                    +BUTTON_TEXT
                }
            is BlueprintPlanResult.Planned -> {
                button {
                    onClick = { setOpen(true) }
                    +BUTTON_TEXT
                }
                if (open) {
                    val blueprint = fromPlaced(outcome.plan, props.logistics, props.beltEnd)
                    blueprintDialog {
                        label = outcome.plan.label
                        text = blueprintString(blueprint.toJson())
                        this.blueprint = blueprint
                        onClose = { setOpen(false) }
                    }
                }
            }
        }
    }

fun ChildrenBuilder.urlBlueprintButton(props: FactorioOptimizerProps) {
    blueprintButton {
        outcome = blueprintPlan(props.optimizationResult, props.logistics, props.normalizeTarget)
        logistics = props.logistics
        beltEnd = props.beltEnd
    }
}
