package de.bjrke.factorio.optimizer

import de.bjrke.factorio.optimizer.business.optimize
import de.bjrke.factorio.optimizer.component.*
import de.bjrke.factorio.optimizer.model.*
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
    var target: String
    var factories: String
}

class FactorioOptimizerProps(private val routeResultProps: RouteResultProps<UrlProps>) : RProps {

    private val params = routeResultProps.match.params

    val parsedRecipes = defaultParsedRecipes

    var target = Material.of(params.target)
    var normalizeTarget = params.normalizeTarget
    var factories = factorySetFromUrl(params.factories)

    val optimizationResult: OptimizationResult = optimize(target, parsedRecipes, factories)

    fun createNewUrl(): String = "/$normalizeTarget/${target.name}/${factories.toUrlParam()}"

    fun changeUrl(change: FactorioOptimizerProps.() -> Unit = {}) {
        routeResultProps.history.push(apply(change).createNewUrl())
    }
}

fun main() {

    val defaultUrl = "/1/all-science-packs/${defaultFactories.toUrlParam()}"

    window.onload =
        {
            val store =
                createStore(
                    ::rootReducer,
                    OptimizerAppState(),
                    compose(
                        rEnhancer(),
                        js(
                            "if(window.__REDUX_DEVTOOLS_EXTENSION__ )window.__REDUX_DEVTOOLS_EXTENSION__ ();else(function(f){return f;});"
                        )
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
                                    div { reduxFactorySelector() }
                                    div { reduxOptimizationResultPresenter() }
                                    navLink<RProps>(defaultUrl) { +"URL based" }
                                }
                            }
                            route<UrlProps>("/:normalizeTarget/:target/:factories") {
                                routeResultProps ->
                                val props = FactorioOptimizerProps(routeResultProps)
                                div {
                                    div {
                                        urlMaterialSelector(props)
                                        urlNormalizeTargetPresenter(props)
                                    }
                                    div { urlFactorySelector(props) }
                                    div { urlOptimizationResultPresenter(props) }
                                    navLink<RProps>("/redux") { +"redux based" }
                                }
                            }
                            redirect(to = defaultUrl)
                        }
                    }
                }
            }
        }
}
