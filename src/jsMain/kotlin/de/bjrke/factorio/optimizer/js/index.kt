// index.kt is the web entry point; the props type is named after the app component.
@file:Suppress("MatchingDeclarationName")

package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.production.normalizeTargetFromUrl
import de.bjrke.factorio.optimizer.common.production.normalizeTargetToUrl
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.prototype.LogisticsConfig
import de.bjrke.factorio.optimizer.common.prototype.Surface
import de.bjrke.factorio.optimizer.common.prototype.selection.EMPTY_SELECTION
import de.bjrke.factorio.optimizer.common.prototype.selection.Selection
import de.bjrke.factorio.optimizer.common.prototype.selection.beltEndFromSelection
import de.bjrke.factorio.optimizer.common.prototype.selection.forSurface
import de.bjrke.factorio.optimizer.common.prototype.selection.fullBeltIn
import de.bjrke.factorio.optimizer.common.prototype.selection.selectionString
import de.bjrke.factorio.optimizer.common.prototype.selection.spaceAgeIn
import de.bjrke.factorio.optimizer.common.prototype.selection.surfaceFromUrl
import de.bjrke.factorio.optimizer.common.service.OptimizationResult
import de.bjrke.factorio.optimizer.common.service.Optimizer
import de.bjrke.factorio.optimizer.js.component.appFooter
import de.bjrke.factorio.optimizer.js.component.urlBlueprintButton
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
    var spaceAgeEnabled = selection?.let(::spaceAgeIn) ?: true
    val factoryConfig = FactoryConfig(spaceAgeEnabled)
    var factories = factoryConfig.fromUrl(selection)
    var surface =
        if (spaceAgeEnabled) surfaceFromUrl(selection) ?: Surface.NAUVIS else Surface.NAUVIS

    val parsedRecipes = RecipeLoader.forActiveMods(spaceAgeEnabled).forSurface(surface)

    val optimizationResult: OptimizationResult = Optimizer(parsedRecipes, factories)[target]

    val logisticsConfig = LogisticsConfig(parsedRecipes.logistics, spaceAgeEnabled)
    var belt = logisticsConfig.belt(selection)
    var inserter = logisticsConfig.inserter(selection)
    var beltLanes = logisticsConfig.beltLanes(fullBeltIn(selection))
    var beltEnd = beltEndFromSelection(selection, surface)
    var beltStack = logisticsConfig.beltStack(params[ParamName("beltStack")], inserter)
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
                Selection(
                    factories,
                    spaceAgeEnabled,
                    surface,
                    fullBelt = beltLanes == BeltTier.MAX_LANES,
                    belt,
                    inserter,
                    // a chest marker cannot be built on a space platform; keep the URL honest
                    beltEnd =
                        beltEnd.takeIf { it.allowedOn(surface) } ?: BeltEnd.CONSTANT_COMBINATOR,
                ),
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
                div { urlBlueprintButton(props) }
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
                            "/\$normalizeTarget/\$target/\$selection/\$beltStack/\$inserterStack",
                        ),
                    component = RouterBasedParams,
                ),
            )

        val catchAllRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path = RoutePath("\$"),
                    component = RouterBasedParams,
                ),
            )

        rootRoute.addChildren(arrayOf(indexRoute, catchAllRoute))

        val appRouter =
            createRouter(
                RouterOptions(
                    routeTree = rootRoute,
                    history = createHashHistory(),
                ),
            )

        val app = FC { RouterProvider { router = appRouter } }

        createRoot(root).render(app.create())
    }
}
