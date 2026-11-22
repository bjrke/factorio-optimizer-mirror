package de.bjrke.factorio.optimizer.js.component

import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import tanstack.react.router.ErrorComponentProps
import web.cssom.ClassName

/**
 * The router's error component: a render that throws — for example an optimizer invariant — leaves
 * the page readable and reports the reason instead of unmounting the whole app behind TanStack
 * Router's error page.
 */
internal val planFailurePanel =
    FC<ErrorComponentProps>("planFailurePanel") { props ->
        div {
            className = ClassName("app-layout")
            div {
                className = ClassName("app-content")
                div {
                    className = ClassName("app-result")
                    p { +"Could not plan this factory." }
                    p { +props.error.message.orEmpty() }
                }
            }
        }
    }
