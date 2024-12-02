package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.optimize
import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories
import de.bjrke.factorio.optimizer.common.model.Factories.factorySetFromUrl
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.toUrlParam
import de.bjrke.factorio.optimizer.js.component.*
import js.core.jso
import react.Props
import react.createElement
import react.dom.client.createRoot
import react.dom.div
import react.fc
import react.redux.provider
import react.router.RouterProvider
import react.router.dom.Link
import react.router.dom.createHashRouter
import react.router.useNavigate
import react.router.useParams
import redux.createStore
import redux.rEnhancer
import web.dom.document
import web.window.window

class FactorioOptimizerProps {
    private val params = useParams()
    private val navigate = useNavigate()

    var normalizeTarget = params["normalizeTarget"]?.toDouble() ?: 1.0
    var target = Material.of(params["target"] ?: ALL_SCIENCE_PACKS)
    var factories = params["factories"]?.let { factorySetFromUrl(it) } ?: defaultFactories

    val parsedRecipes = defaultParsedRecipes
    val optimizationResult: OptimizationResult = optimize(target, parsedRecipes, factories)

    private fun createNewUrl(): String =
        "/${superRound(normalizeTarget, 1000000.0)}/${target.name}/${factories.toUrlParam()}"

    fun changeUrl(change: FactorioOptimizerProps.() -> Unit = {}) {
        navigate(apply(change).createNewUrl())
    }
}

val RouterBasedParams =
    fc<Props> {
        val props = FactorioOptimizerProps()
        div {
            div { urlMaterialSelector(props) }
            div { urlFactorySelector(props) }
            div { urlOptimizationResultPresenter(props) }
            Link {
                attrs.to = "/redux"
                +"redux based"
            }
        }
    }

val ReduxBasedParams =
    fc<Props> {
        div {
            div { reduxMaterialSelector() }
            div { reduxFactorySelector() }
            div { reduxOptimizationResultPresenter() }
            Link {
                attrs.to = "/"
                +"URL based"
            }
        }
    }

fun main() {

    window.onload = {
        val store = createStore(::rootReducer, OptimizerAppState(), rEnhancer())
        val root = document.createElement("div")
        document.body.prepend(root)

        val hashRouter =
            createHashRouter(
                routes =
                    arrayOf(
                        jso {
                            path = "/redux"
                            element = createElement<Props> { ReduxBasedParams {} }
                        },
                        jso {
                            path = "/:normalizeTarget/:target/:factories"
                            element = createElement<Props> { RouterBasedParams {} }
                        },
                        jso {
                            path = "*"
                            element = createElement<Props> { RouterBasedParams {} }
                        }
                    )
            )

        val app = fc<Props> { provider(store) { RouterProvider { attrs.router = hashRouter } } }

        createRoot(root).render(createElement<Props> { app() })
    }
}
