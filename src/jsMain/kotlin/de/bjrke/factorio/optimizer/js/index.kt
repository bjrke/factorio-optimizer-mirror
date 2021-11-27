package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.business.optimize
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories
import de.bjrke.factorio.optimizer.common.model.Factories.factorySetFromUrl
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.toUrlParam
import de.bjrke.factorio.optimizer.js.component.reduxFactorySelector
import de.bjrke.factorio.optimizer.js.component.reduxMaterialSelector
import de.bjrke.factorio.optimizer.js.component.reduxOptimizationResultPresenter
import de.bjrke.factorio.optimizer.js.component.urlFactorySelector
import de.bjrke.factorio.optimizer.js.component.urlMaterialSelector
import de.bjrke.factorio.optimizer.js.component.urlOptimizationResultPresenter
import kotlinx.browser.document
import kotlinx.browser.window
import react.Props
import react.dom.div
import react.dom.render
import react.fc
import react.redux.provider
import react.router.dom.HashRouter
import react.router.dom.History
import react.router.dom.Link
import react.router.dom.Redirect
import react.router.dom.Route
import react.router.dom.Switch
import react.router.dom.useHistory
import react.router.dom.useParams
import redux.createStore
import redux.rEnhancer

class FactorioOptimizerProps(
    var normalizeTarget: Double,
    var target: Material,
    var factories: Set<Factory>,
    private val history: History
) : Props {
    val parsedRecipes = defaultParsedRecipes

    val optimizationResult: OptimizationResult = optimize(target, parsedRecipes, factories)

    fun createNewUrl(): String = "/$normalizeTarget/${target.name}/${factories.toUrlParam()}"

    fun changeUrl(change: FactorioOptimizerProps.() -> Unit = {}) {
        history.push(apply(change).createNewUrl())
    }
}

fun main() {

    val defaultUrl = "/1/all-science-packs/${defaultFactories.toUrlParam()}"

    window.onload =
        {
            val store = createStore(::rootReducer, OptimizerAppState(), rEnhancer())
            render(document.getElementById("root")) {
                provider(store) {
                    div { +"Hello 123" }
                    HashRouter {
                        Switch {
                            Route {
                                attrs.path = arrayOf("/redux")
                                div {
                                    div { reduxMaterialSelector() }
                                    div { reduxFactorySelector() }
                                    div { reduxOptimizationResultPresenter() }
                                    Link {
                                        attrs.to = defaultUrl
                                        +"URL based"
                                    }
                                }
                            }
                            Route {
                                attrs.path = arrayOf("/:normalizeTarget/:target/:factories")
                                fc<Props> {
                                    val props = getRouteParams()
                                    div {
                                        div { urlMaterialSelector(props) }
                                        div { urlFactorySelector(props) }
                                        div { urlOptimizationResultPresenter(props) }
                                        Link {
                                            attrs.to = "/redux"
                                            +"redux based"
                                        }
                                    }
                                }()
                            }
                        }
                        Redirect { attrs.to = defaultUrl }
                    }
                }
            }
        }
}

private fun getRouteParams(): FactorioOptimizerProps {
    val params = useParams()

    val normalizeTarget = params["normalizeTarget"]?.toDouble() ?: 1.0
    val target = Material.of(params["target"] ?: "all-science-packs")
    val factories = params["factories"]?.let { factorySetFromUrl(it) } ?: defaultFactories

    val props = FactorioOptimizerProps(normalizeTarget, target, factories, useHistory())
    return props
}
