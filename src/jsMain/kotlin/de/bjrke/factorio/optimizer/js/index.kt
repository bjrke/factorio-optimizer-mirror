package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.business.RecipeProvider
import de.bjrke.factorio.optimizer.common.business.SPACE_AGE_MOD
import de.bjrke.factorio.optimizer.common.business.defaultMods
import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories
import de.bjrke.factorio.optimizer.common.model.Factories.factorySetFromUrl
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.forSurface
import de.bjrke.factorio.optimizer.common.model.surfaceFromUrl
import de.bjrke.factorio.optimizer.common.model.toUrlParam
import de.bjrke.factorio.optimizer.js.component.urlFactorySelector
import de.bjrke.factorio.optimizer.js.component.urlMaterialSelector
import de.bjrke.factorio.optimizer.js.component.urlModSelector
import de.bjrke.factorio.optimizer.js.component.urlOptimizationResultPresenter
import de.bjrke.factorio.optimizer.js.component.urlPlanetSelector
import react.FC
import react.Props
import react.create
import react.dom.client.createRoot
import react.dom.html.ReactHTML.div
import tanstack.history.createHashHistory
import tanstack.react.router.*
import tanstack.router.core.ParamName
import tanstack.router.core.RoutePath
import web.cssom.ClassName
import web.dom.document
import web.events.EventHandler
import web.window.window

class FactorioOptimizerProps {
    private val params = useParams()
    private val navigate = useNavigate()

    var normalizeTarget = params[ParamName("normalizeTarget")]?.toDouble() ?: 1.0
    var target = Material.of(params[ParamName("target")] ?: ALL_SCIENCE_PACKS)
    var factories =
        params[ParamName("factories")]?.let { factorySetFromUrl(it) } ?: defaultFactories
    var mods =
        params[ParamName("mods")]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: defaultMods
    val spaceAgeEnabled = SPACE_AGE_MOD in RecipeProvider.resolveMods(mods)
    var surface =
        if (spaceAgeEnabled) {
            params[ParamName("surface")]?.let { surfaceFromUrl(it) } ?: Surface.NAUVIS
        } else {
            Surface.NAUVIS
        }

    val parsedRecipes = RecipeProvider.forActiveMods(mods).forSurface(surface)
    val optimizationResult: OptimizationResult = Optimizer(parsedRecipes, factories)[target]

    private fun createNewUrl(): String =
        "/${superRound(normalizeTarget, 1000000.0)}/${target.name}/${factories.toUrlParam()}/${mods.sorted().joinToString(",")}/${surface.toUrlParam()}"

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
            className = ClassName("app-layout")
            div {
                className = ClassName("app-header")
                div { urlMaterialSelector(props) }
                div { urlPlanetSelector(props) }
                div { urlFactorySelector(props) }
                div { urlModSelector(props) }
            }
            div {
                className = ClassName("app-content")
                urlOptimizationResultPresenter(props)
            }
        }
    }

fun main() {

    window.onload = EventHandler {
        val root = document.createElement("div")
        root.className = ClassName("app-root")
        document.body.prepend(root)

        val rootRoute = createRootRoute()

        val indexRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path = RoutePath("/\$normalizeTarget/\$target/\$factories/\$mods/\$surface"),
                    component = RouterBasedParams,
                )
            )

        // legacy route without the surface segment, defaults to Nauvis
        val legacyRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path = RoutePath("/\$normalizeTarget/\$target/\$factories/\$mods"),
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

        rootRoute.addChildren(arrayOf(indexRoute, legacyRoute, catchAllRoute))

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
