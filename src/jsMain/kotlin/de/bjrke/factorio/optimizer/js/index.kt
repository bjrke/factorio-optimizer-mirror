// index.kt is the web entry point; the props type is named after the app component.
@file:Suppress("MatchingDeclarationName")

package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.production.normalizeTargetFromUrl
import de.bjrke.factorio.optimizer.common.game.production.normalizeTargetToUrl
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.LogisticsConfig
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.validIntermediateLimit
import de.bjrke.factorio.optimizer.common.game.selection.EMPTY_SELECTION
import de.bjrke.factorio.optimizer.common.game.selection.Selection
import de.bjrke.factorio.optimizer.common.game.selection.beltEndFromSelection
import de.bjrke.factorio.optimizer.common.game.selection.excludedMaterialsFromUrl
import de.bjrke.factorio.optimizer.common.game.selection.excludedMaterialsToUrl
import de.bjrke.factorio.optimizer.common.game.selection.fullBeltIn
import de.bjrke.factorio.optimizer.common.game.selection.selectionString
import de.bjrke.factorio.optimizer.common.game.selection.spaceAgeIn
import de.bjrke.factorio.optimizer.common.game.selection.surfaceFromUrl
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.optimize.Optimizer
import de.bjrke.factorio.optimizer.js.component.appFooter
import de.bjrke.factorio.optimizer.js.component.blueprintCopyButton
import de.bjrke.factorio.optimizer.js.component.blueprintPanel
import de.bjrke.factorio.optimizer.js.component.blueprintPreview
import de.bjrke.factorio.optimizer.js.component.urlFactorySelector
import de.bjrke.factorio.optimizer.js.component.urlIntermediateLimitInput
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
import react.useMemo
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
    var intermediateLimit = validIntermediateLimit(params[ParamName("intermediates")])
    var excludedMaterials = excludedMaterialsFromUrl(params[ParamName("excluded")])

    val parsedRecipes = RecipeLoader.forSurface(spaceAgeEnabled, surface)

    // the limit and the exclusions are part of the optimizer's configuration, so changing either
    // drops its result cache; a target change reuses it
    val optimizer =
        useMemo(
            "${selection.orEmpty()}/$intermediateLimit/" +
                excludedMaterials.map { it.name }.sorted().joinToString(","),
        ) {
            Optimizer(parsedRecipes, factories, intermediateLimit, excludedMaterials)
        }
    val optimizationResult: OptimizationResult = optimizer[target]

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
            LogisticsCalculatorSelection(
                belt,
                inserter,
                beltLanes,
                beltStack,
                inserterStack,
            ),
        )

    /** The URL the current settings produce; it fully determines the blueprint. */
    val urlKey: String
        get() = createNewUrl()

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
            "/$beltStack/$inserterStack/$intermediateLimit/${excludedMaterialsToUrl(excludedMaterials)}"
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
        // the URL fully determines the blueprint, so a render that keeps it reuses the layout
        val preview =
            useMemo(props.urlKey) {
                blueprintPreview(
                    props.optimizationResult,
                    props.logistics,
                    props.normalizeTarget,
                    props.beltEnd,
                    PowerPole.forSelection(props.surface, props.factories),
                )
            }
        div {
            className = ClassName("app-layout")
            div {
                className = ClassName("app-header")
                div { urlMaterialSelector(props) }
                div { urlPlanetSelector(props) }
                div { urlFactorySelector(props) }
                div { urlLogisticsSelector(props) }
                div { urlIntermediateLimitInput(props) }
                div { urlSpaceAgeToggle(props) }
                blueprintCopyButton {
                    outcome = preview.outcome
                    blueprint = preview.blueprint
                }
            }
            div {
                className = ClassName("app-content")
                div {
                    className = ClassName("app-result")
                    urlOptimizationResultPresenter(props)
                }
                blueprintPanel {
                    outcome = preview.outcome
                    blueprint = preview.blueprint
                }
            }
        }
    }

private fun createAppRouter() =
    createRootRoute().let { rootRoute ->
        val indexRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path =
                        RoutePath(
                            $$"/$normalizeTarget/$target/$selection/$beltStack/$inserterStack/$intermediates/$excluded",
                        ),
                    component = RouterBasedParams,
                ),
            )

        // a URL from before the exclusions has no seventh segment; it still decodes to no
        // exclusions
        val legacyExcludedRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path =
                        RoutePath(
                            $$"/$normalizeTarget/$target/$selection/$beltStack/$inserterStack/$intermediates",
                        ),
                    component = RouterBasedParams,
                ),
            )

        // a URL from before the intermediate limit has no sixth segment; it still decodes to the
        // default limit
        val legacyRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path =
                        RoutePath(
                            $$"/$normalizeTarget/$target/$selection/$beltStack/$inserterStack",
                        ),
                    component = RouterBasedParams,
                ),
            )

        val catchAllRoute =
            createRoute(
                RouteOptions(
                    getParentRoute = { rootRoute },
                    path = RoutePath("$"),
                    component = RouterBasedParams,
                ),
            )

        rootRoute.addChildren(arrayOf(indexRoute, legacyExcludedRoute, legacyRoute, catchAllRoute))

        createRouter(
            RouterOptions(
                routeTree = rootRoute,
                history = createHashHistory(),
            ),
        )
    }

fun main() {

    window.onload = EventHandler {
        val root = document.createElement("div")
        root.className = ClassName("app-root")
        document.body.prepend(root)

        document.querySelector("#footer")?.let { footerElement ->
            createRoot(footerElement).render(appFooter.create())
        }

        val appRouter = createAppRouter()
        val app = FC { RouterProvider { router = appRouter } }

        createRoot(root).render(app.create())
    }
}
