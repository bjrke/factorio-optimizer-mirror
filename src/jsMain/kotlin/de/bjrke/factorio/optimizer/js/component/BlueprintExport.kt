package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.*
import de.bjrke.factorio.optimizer.common.model.Blueprint
import de.bjrke.factorio.optimizer.common.model.toJson
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import emotion.react.css
import react.*
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.textarea
import web.cssom.ClassName
import web.cssom.Position
import web.cssom.px
import web.cssom.rem

private const val TILE = 24.0

/** Turns an entity's direction into the clockwise rotation its icon needs; icons face east. */
private fun rotationDegrees(direction: Int?): Int? = direction?.let {
    (((it - 4) * 90 / 4) % 360 + 360) % 360
}

private fun copyToClipboard(text: String) {
    js("navigator.clipboard && navigator.clipboard.writeText(text)")
}

external interface BlueprintViewProps : Props {
    var blueprint: Blueprint
}

/** Renders the blueprint as an icon grid: each entity drawn at its tile position, belts rotated. */
internal val blueprintView =
    FC<BlueprintViewProps>("blueprintView") { props ->
        val entities = props.blueprint.entities
        val minX = entities.minOfOrNull { it.area.topLeft.x } ?: 0
        val minY = entities.minOfOrNull { it.area.topLeft.y } ?: 0
        val maxX = entities.maxOfOrNull { it.area.bottomRight.x } ?: 0
        val maxY = entities.maxOfOrNull { it.area.bottomRight.y } ?: 0

        div {
            className = ClassName("blueprint-view")
            css {
                position = Position.relative
                width = ((maxX - minX) * TILE).px
                height = ((maxY - minY) * TILE).px
                backgroundColor = web.cssom.Color("var(--factory-panel, #fff)")
            }
            for ((index, entity) in entities.withIndex()) {
                div {
                    key = Key(index.toString())
                    val area = entity.area
                    css {
                        position = Position.absolute
                        left = ((area.topLeft.x - minX) * TILE).px
                        top = ((area.topLeft.y - minY) * TILE).px
                        width = (area.width * TILE).px
                        height = (area.height * TILE).px
                    }
                    icon(entity.name) {
                        size = (minOf(area.width, area.height) * TILE).toInt()
                        rotationDegrees(entity.direction)?.let { rotation = it }
                    }
                }
            }
        }
    }

external interface BlueprintDialogProps : Props {
    var label: String
    var text: String
    var blueprint: Blueprint
    var onClose: () -> Unit
}

internal val blueprintDialog =
    FC<BlueprintDialogProps>("blueprintDialog") { props ->
        div {
            className = ClassName("license-overlay")
            role = AriaRole.dialog
            onClick = { props.onClose() }
            div {
                className = ClassName("license-dialog")
                onClick = { it.stopPropagation() }
                button {
                    className = ClassName("license-close")
                    onClick = { props.onClose() }
                    +"Close"
                }
                div { +props.label }
                textarea {
                    className = ClassName("blueprint-string")
                    readOnly = true
                    value = props.text
                    css {
                        width = 30.rem
                        height = 6.rem
                    }
                }
                button {
                    onClick = { copyToClipboard(props.text) }
                    +"Copy"
                }
                blueprintView { blueprint = props.blueprint }
            }
        }
    }

external interface BlueprintButtonProps : Props {
    var outcome: BlueprintPlanResult
    var logistics: LogisticsCalculator
    var cheats: Boolean
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
                    val blueprint = buildBlueprint(outcome.plan, props.logistics, props.cheats)
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
        cheats = props.cheats
    }
}
