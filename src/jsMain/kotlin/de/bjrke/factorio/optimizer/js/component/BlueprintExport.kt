// The panel props are read below; light-mode detekt cannot see external interface members.
@file:Suppress("UnusedParameter")

package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.RowLayout
import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.planning.blueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.printing.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.Blueprint
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.blueprintString
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import emotion.react.css
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG.foreignObject
import react.dom.svg.ReactSVG.svg
import web.cssom.ClassName
import web.cssom.Display
import web.cssom.Position
import web.cssom.pct
import web.cssom.px

private const val TILE = 24.0

/** Turns an entity's direction into the clockwise rotation its icon needs; icons face east. */
private fun rotationDegrees(direction: Direction) =
    when (direction) {
        Direction.NORTH -> 270
        Direction.EAST -> 0
        Direction.SOUTH -> 90
        Direction.WEST -> 180
    }

/**
 * The clockwise rotation the entity's icon needs on screen. Most icons face east, but a loader's
 * arrow points north and a splitter's arrows point west; an underground exit opens the other way
 * than the belt it feeds. The blueprint itself is never changed, only the preview.
 */
internal fun blueprintDisplayRotation(entity: BlueprintEntity): Int {
    val name = entity.name
    if (name in Factory.factoriesByIdentifier) return 0
    val base = rotationDegrees(entity.direction)
    val extra =
        when {
            name.endsWith("loader") -> 90
            name.endsWith("splitter") -> 180
            name.endsWith("underground-belt") && entity.type == Io.OUTPUT -> 180
            else -> 0
        }
    return (base + extra) % 360
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
                        val quarterTurn = iconRotation % 180 != 0
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
                                    if (iconRotation != 0) rotation = iconRotation
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

/** The blueprint a plan produces, or null when the plan cannot be built. */
internal data class BlueprintPreview(
    val outcome: BlueprintPlanResult,
    val blueprint: Blueprint?,
)

/**
 * Plans and lays out the blueprint for [result] once, so the always-visible panel does not rerun
 * the layout on every render. The URL fully determines the inputs, so callers memoize on it.
 */
internal fun blueprintPreview(
    result: OptimizationResult,
    logistics: LogisticsCalculator,
    normalizeTarget: Double,
    beltEnd: BeltEnd,
    powerPole: PowerPole = PowerPole.MEDIUM_ELECTRIC_POLE,
): BlueprintPreview {
    val outcome = blueprintPlan(result, logistics, normalizeTarget)
    return BlueprintPreview(
        outcome,
        (outcome as? BlueprintPlanResult.Planned)?.let {
            RowLayout(it.plan, logistics, beltEnd, powerPole).blueprint()
        },
    )
}

external interface BlueprintPanelProps : Props {
    var blueprint: Blueprint?
}

/**
 * The blueprint preview next to the optimizer result. A plan without a blueprint (unavailable)
 * renders no panel at all; the copy button beside the selectors carries the reason.
 */
internal val blueprintPanel =
    FC<BlueprintPanelProps>("blueprintPanel") { props ->
        val blueprint = props.blueprint
        if (blueprint != null) {
            div {
                className = ClassName("blueprint-panel")
                div {
                    className = ClassName("blueprint-preview")
                    blueprintView { this.blueprint = blueprint }
                }
            }
        }
    }

external interface BlueprintCopyButtonProps : Props {
    var outcome: BlueprintPlanResult
    var blueprint: Blueprint?
}

/**
 * Copies the blueprint string. It sits in the header next to the selectors, so the panel itself
 * only shows the preview; an unavailable plan disables it and explains why on hover.
 */
internal val blueprintCopyButton =
    FC<BlueprintCopyButtonProps>("blueprintCopyButton") { props ->
        val blueprint = props.blueprint
        selectionGroup {
            button {
                className = ClassName("icon-option icon-button")
                disabled = blueprint == null
                title =
                    when (val outcome = props.outcome) {
                        is BlueprintPlanResult.Planned -> "Copy blueprint string"
                        is BlueprintPlanResult.Unavailable -> outcome.reason
                    }
                onClick = { blueprint?.let { copyToClipboard(blueprintString(it.toJson())) } }
                icon("copy-paste-tool") { alt = "Copy blueprint" }
            }
        }
    }
