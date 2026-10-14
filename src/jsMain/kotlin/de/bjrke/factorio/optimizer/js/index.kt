package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.business.RecipeProvider
import de.bjrke.factorio.optimizer.common.business.SPACE_AGE_MOD
import de.bjrke.factorio.optimizer.common.business.defaultMods
import de.bjrke.factorio.optimizer.common.business.normalizeTargetFromUrl
import de.bjrke.factorio.optimizer.common.business.normalizeTargetToUrl
import de.bjrke.factorio.optimizer.common.model.FactoryConfig
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.forSurface
import de.bjrke.factorio.optimizer.common.model.surfaceFromUrl
import de.bjrke.factorio.optimizer.common.model.toUrlParam
import de.bjrke.factorio.optimizer.js.component.appFooter
import de.bjrke.factorio.optimizer.js.component.urlFactorySelector
import de.bjrke.factorio.optimizer.js.component.urlLogisticsSelector
import de.bjrke.factorio.optimizer.js.component.urlMaterialSelector
import de.bjrke.factorio.optimizer.js.component.urlOptimizationResultPresenter
import de.bjrke.factorio.optimizer.js.component.urlPlanetSelector
import de.bjrke.factorio.optimizer.js.component.urlSpaceAgeToggle
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

    var normalizeTarget = normalizeTargetFromUrl(params[ParamName("normalizeTarget")])
    var target = Material.of(params[ParamName("target")] ?: ALL_SCIENCE_PACKS)
    var mods =
        params[ParamName("mods")]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: defaultMods
    val activeMods = RecipeProvider.resolveMods(mods)
    val spaceAgeEnabled = SPACE_AGE_MOD in activeMods
    val factoryConfig = FactoryConfig(spaceAgeEnabled)
    var factories =
        params[ParamName("factories")]?.let { factoryConfig.fromUrl(it) } ?: factoryConfig.defaults
    var surface =
        if (spaceAgeEnabled) {
            params[ParamName("surface")]?.let { surfaceFromUrl(it) } ?: Surface.NAUVIS
        } else {
            Surface.NAUVIS
        }

    val parsedRecipes = RecipeProvider.forActiveMods(mods).forSurface(surface)
    val optimizationResult: OptimizationResult = Optimizer(parsedRecipes, factories)[target]

    val logisticsConfig = LogisticsConfig(parsedRecipes.logistics, spaceAgeEnabled)
    var belt = logisticsConfig.belt(params[ParamName("belt")])
    var inserter = logisticsConfig.inserter(params[ParamName("inserter")])
    var beltLanes = logisticsConfig.beltLanes(params[ParamName("lanes")])
    var beltStack = logisticsConfig.beltStack(params[ParamName("beltStack")])
    var inserterStack = logisticsConfig.inserterStack(params[ParamName("inserterStack")], inserter)
    val logistics =
        LogisticsCalculator(
            parsedRecipes.logistics,
            belt,
            inserter,
            beltLanes,
            beltStack,
            inserterStack,
        )

    private fun createNewUrl(): String =
        "/${normalizeTargetToUrl(normalizeTarget)}/${target.name}/${factories.toUrlParam()}/${mods.sorted().joinToString(",")}/${surface.toUrlParam()}/${belt.code}/${inserter.code}/$beltLanes/$beltStack/$inserterStack"

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
                div { urlLogisticsSelector(props) }
                div { urlSpaceAgeToggle(props) }
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

        document.querySelector("#footer")?.let { footerElement ->
            createRoot(footerElement).render(appFooter.create())
        }

        val rootRoute = createRootRoute()

        val indexRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path =
                        RoutePath(
                            "/\$normalizeTarget/\$target/\$factories/\$mods/\$surface" +
                                "/\$belt/\$inserter/\$lanes/\$beltStack/\$inserterStack"
                        ),
                    component = RouterBasedParams,
                )
            )

        // legacy route without the logistics segment, defaults to the fastest belt/inserter
        val surfaceRoute =
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

        rootRoute.addChildren(arrayOf(indexRoute, surfaceRoute, legacyRoute, catchAllRoute))

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
