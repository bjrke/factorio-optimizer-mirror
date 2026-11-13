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
 * input only reports the clamped number; the URL change re-renders the optimizer.
 */
internal val intermediateLimitInput =
    FC<IntermediateLimitInputProps>("intermediateLimitInput") { props ->
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
                        value = props.value.toString()
                        title = "Maximum intermediate materials built"
                        css { width = 4.rem }
                        onChange = { event ->
                            event.target.value.toIntOrNull()?.let { value ->
                                props.change(validIntermediateLimit(value.toString()))
                            }
                        }
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
