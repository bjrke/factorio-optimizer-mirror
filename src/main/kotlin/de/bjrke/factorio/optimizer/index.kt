package de.bjrke.factorio.optimizer

import de.bjrke.factorio.optimizer.component.*
import kotlinx.browser.document
import kotlinx.browser.window
import react.RProps
import react.dom.div
import react.dom.render
import react.redux.provider
import react.router.dom.*
import redux.compose
import redux.createStore
import redux.rEnhancer

interface UrlProps : RProps {
    var normalizeTarget: Double
}

fun UrlProps.createNewUrl(): String = "/$normalizeTarget"

fun RouteResultProps<UrlProps>.createNewUrl(change: UrlProps.() -> Unit): String =
    match.params.apply(change).createNewUrl()

fun RouteResultProps<UrlProps>.changeUrl(change: UrlProps.() -> Unit) {
    history.push(createNewUrl(change))
}

fun main() {
    window.onload = {
        val store = createStore(
            ::rootReducer,
            OptimizerAppState(),
            compose(
                rEnhancer(),
                js("if(window.__REDUX_DEVTOOLS_EXTENSION__ )window.__REDUX_DEVTOOLS_EXTENSION__ ();else(function(f){return f;});")
            )
        )
        render(document.getElementById("root")) {
            provider(store) {
                hashRouter {
                    switch {
                        route("/redux") {
                            div {
                                div {
                                    reduxMaterialSelector()
                                    reduxNormalizeTargetPresenter()
                                }
                                div {
                                    reduxFactorySelector()
                                }
                                div {
                                    reduxOptimizationResultPresenter()
                                }
                                navLink<RProps>("/1") {
                                    +"URL based"
                                }
                            }
                        }
                        route<UrlProps>("/:normalizeTarget") { routeResultProps ->
                            div {
                                div {
                                    reduxMaterialSelector()
                                    urlNormalizeTargetPresenter(routeResultProps)
                                }
                                div {
                                    reduxFactorySelector()
                                }
                                div {
                                    urlOptimizationResultPresenter(routeResultProps)
                                }
                                navLink<RProps>("/redux") {
                                    +"redux based"
                                }
                            }
                        }
                        redirect(to = "/1")
                    }
                }
            }
        }
    }
}
