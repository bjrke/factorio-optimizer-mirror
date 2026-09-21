package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.business.parseRecipes
import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories
import de.bjrke.factorio.optimizer.common.model.Factories.factorySetFromUrl
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.toUrlParam
import de.bjrke.factorio.optimizer.js.component.urlFactorySelector
import de.bjrke.factorio.optimizer.js.component.urlMaterialSelector
import de.bjrke.factorio.optimizer.js.component.urlOptimizationResultPresenter
import react.FC
import react.Props
import react.create
import react.dom.client.createRoot
import react.dom.html.ReactHTML.div
import tanstack.history.createHashHistory
import tanstack.react.router.RouteOptions
import tanstack.react.router.RouterOptions
import tanstack.react.router.RouterProvider
import tanstack.react.router.createRootRoute
import tanstack.react.router.createRoute
import tanstack.react.router.createRouter
import tanstack.react.router.useNavigate
import tanstack.react.router.useParams
import tanstack.router.core.ParamName
import tanstack.router.core.RoutePath
import web.dom.document
import web.events.EventHandler
import web.window.window

val defaultRecipes = parseRecipes()

class FactorioOptimizerProps {
    private val params = useParams()
    private val navigate = useNavigate()

    var normalizeTarget = params[ParamName("normalizeTarget")]?.toDouble() ?: 1.0
    var target = Material.of(params[ParamName("target")] ?: ALL_SCIENCE_PACKS)
    var factories =
        params[ParamName("factories")]?.let { factorySetFromUrl(it) } ?: defaultFactories

    val parsedRecipes = defaultRecipes
    val optimizationResult: OptimizationResult = Optimizer(parsedRecipes, factories)[target]

    private fun createNewUrl(): String =
        "/${superRound(normalizeTarget, 1000000.0)}/${target.name}/${factories.toUrlParam()}"

    fun changeUrl(change: FactorioOptimizerProps.() -> Unit = {}) {
        navigate {
            to = RoutePath(apply(change).createNewUrl())
        }
    }
}

val RouterBasedParams =
    FC<Props> {
        val props = FactorioOptimizerProps()
        div {
            div { urlMaterialSelector(props) }
            div { urlFactorySelector(props) }
            div { urlOptimizationResultPresenter(props) }
        }
    }

fun main() {

    window.onload = EventHandler {
        val root = document.createElement("div")
        document.body.prepend(root)

        val rootRoute = createRootRoute()

        val indexRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path = RoutePath("/\$normalizeTarget/\$target/\$factories"),
                    component = RouterBasedParams,
                )
            )

        val catchAllRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path = RoutePath("\$"),
                    component = RouterBasedParams,
                )
            )

        rootRoute.addChildren(arrayOf(indexRoute, catchAllRoute))

        val appRouter =
            createRouter(
                RouterOptions(
                    routeTree = rootRoute,
                    history = createHashHistory(),
                )
            )

        val app = FC { RouterProvider { router = appRouter } }

        createRoot(root).render(app.create())
    }
}
