package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.business.*
import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.js.component.*
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
    var target = Material(params[ParamName("target")] ?: ALL_SCIENCE_PACKS)
    private val selection = params[ParamName("selection")]
    var spaceAgeEnabled = selection?.let(SelectionFlag.SPACE_AGE::enabledIn) ?: true
    val factoryConfig = FactoryConfig(spaceAgeEnabled)
    var factories = factoryConfig.fromUrl(selection)
    var surface =
        if (spaceAgeEnabled) surfaceFromUrl(selection) ?: Surface.NAUVIS else Surface.NAUVIS

    val parsedRecipes = RecipeProvider.forActiveMods(spaceAgeEnabled).forSurface(surface)

    val optimizationResult: OptimizationResult = Optimizer(parsedRecipes, factories)[target]

    val logisticsConfig = LogisticsConfig(parsedRecipes.logistics, spaceAgeEnabled)
    var belt = logisticsConfig.belt(selection)
    var inserter = logisticsConfig.inserter(selection)
    var beltLanes = logisticsConfig.beltLanes(selection)
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

    private fun createNewUrl(): String {
        val encodedSelection =
            selectionString(
                factories,
                spaceAgeEnabled,
                surface,
                fullBelt = beltLanes == Belt.MAX_LANES,
                belt,
                inserter,
            )
        // the segment is never empty so the route always matches; '-' marks the all-defaults choice
        val selectionSegment = encodedSelection.ifEmpty { EMPTY_SELECTION }
        return "/${normalizeTargetToUrl(normalizeTarget)}/${target.name}/$selectionSegment" +
            "/$beltStack/$inserterStack"
    }

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
                            "/\$normalizeTarget/\$target/\$selection/\$beltStack/\$inserterStack"
                        ),
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
