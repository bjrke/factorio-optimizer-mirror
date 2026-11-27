package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.MIN_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.validIntermediateLimit
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useState
import web.cssom.ClassName
import web.cssom.Display
import web.cssom.rem
import web.html.InputType
import web.html.number

external interface IntermediateLimitInputProps : Props {
    var value: Int
    var change: (Int) -> Unit
}

/**
 * The number of intermediate materials the plan may build. The value is part of the URL, so the
 * field keeps the typed text to itself and reports the clamped number only when the edit is
 * committed (Enter or blur): a keystroke must not replan the factory, and an empty field must stay
 * editable instead of snapping back to the current limit.
 */
internal val intermediateLimitInput =
    FC<IntermediateLimitInputProps>("intermediateLimitInput") { props ->
        val current = props.value.toString()
        val (text, setText) = useState(current)
        // a URL change outside the field moves the limit: follow it
        useEffect(current) { setText(current) }

        fun commit() {
            val typed = text.toIntOrNull()
            if (typed == null) setText(current)
            else props.change(validIntermediateLimit(typed.toString()))
        }

        selectionGroup {
            controlBox {
                span {
                    className = ClassName("intermediate-limit")
                    css { display = Display.inlineFlex }
                    span {
                        css { marginRight = 0.25.rem }
                        icon("signal-item-parameter") { alt = "intermediates" }
                    }
                    input {
                        type = InputType.number
                        min = MIN_INTERMEDIATE_LIMIT.toString()
                        max = MAX_INTERMEDIATE_LIMIT.toString()
                        value = text
                        title = "Maximum intermediate materials built"
                        css { width = 4.rem }
                        onChange = { setText(it.target.value) }
                        onKeyDown = { event ->
                            if (event.key == "Enter") {
                                event.preventDefault()
                                commit()
                            }
                        }
                        onBlur = { commit() }
                    }
                }
            }
        }
    }

fun ChildrenBuilder.urlIntermediateLimitInput(props: FactorioOptimizerProps) {
    intermediateLimitInput {
        value = props.intermediateLimit
        change = { props.changeUrl(replace = true) { intermediateLimit = it } }
    }
}
