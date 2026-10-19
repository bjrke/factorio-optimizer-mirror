package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Dimension
import kotlin.math.ceil
import kotlin.math.roundToInt

/** How many distinct recipes a single blueprint may build; the rest are imported. */
const val MAX_BLUEPRINT_RECIPES = 8

/** Upper bound on the machines of one recipe row, so a huge rate cannot explode the blueprint. */
internal const val MAX_COPIES = 9

/**
 * Complex or cyclic processes that are usually done elsewhere and therefore left out of a
 * blueprint; their products become imports. Only kept when the target itself comes from one of
 * them. Oil refining and cracking (and their bio/cryo Space Age shortcuts) pull in long fluid
 * chains, so e.g. heavy oil and petroleum gas derivatives like plastic are imported. Simple
 * chemical-plant recipes (lubricant, sulfur, ...) are *not* excluded; only their fluid inputs are.
 */
private val externallyProcessedCategories =
    setOf(
        Category.OIL_PROCESSING,
        Category.CENTRIFUGING,
        Category.ORGANIC,
        Category.CRYOGENICS,
        Category.RECYCLING,
        Category.CRUSHING,
        Category.ASTEROID_COLLECTION,
    )

/** Classic smelters are left out (their plates are imported); the foundry is kept. */
private val classicSmelters =
    setOf(Factory.STONE_FURNACE, Factory.STEEL_FURNACE, Factory.ELECTRIC_FURNACE)

/**
 * Footprint of the machine that runs a recipe, keyed by its prototype name. The width bounds how
 * many inserters (and therefore direct-insertion children) fit on one edge. Unknown machines fall
 * back to the assembling-machine size.
 */
fun blueprintMachineFootprint(identifier: String): Dimension =
    when (identifier) {
        "stone-furnace",
        "steel-furnace" -> Dimension(2, 2)
        "recycler" -> Dimension(4, 2)
        "electromagnetic-plant" -> Dimension(4, 4)
        "foundry",
        "oil-refinery",
        "cryogenic-plant" -> Dimension(5, 5)
        "rocket-silo" -> Dimension(9, 9)
        else -> Dimension(3, 3)
    }

/** Which way a machine's fluid port flows: it only takes fluid, only gives it, or does both. */
enum class PortFlow {
    INPUT,
    OUTPUT,
    BOTH,
}

/**
 * A fluid connection of a machine: the tile just outside it (relative to the machine's top-left
 * tile) where a pipe has to sit, and which way it flows.
 */
data class FluidPort(val tile: Coordinate, val flow: PortFlow)

/**
 * Where a machine's pipes connect, relative to its top-left tile.
 *
 * The values come from the `pipe_connections` of the machine prototypes: a connection's `position`
 * (in tile units around the machine's center) is the tile at the machine's edge, and its
 * `direction` is the side the pipe attaches on, so the pipe tile is the edge tile one step in that
 * direction. E.g. the electromagnetic plant's west connection sits at `{-1.5, 0.5}` facing west on
 * a 4x4 machine, giving tile `(-1, 2)` (third row). Machines without pipes return an empty list.
 */
fun blueprintMachineFluidPorts(identifier: String): List<FluidPort> =
    when (identifier) {
        "assembling-machine-2",
        "assembling-machine-3" ->
            listOf(
                FluidPort(Coordinate(1, -1), PortFlow.INPUT),
                FluidPort(Coordinate(1, 3), PortFlow.OUTPUT),
            )
        // the chemical plant and the biochamber have both fluids on the top corners and both
        // outputs on the bottom corners
        "chemical-plant",
        "biochamber" ->
            listOf(
                FluidPort(Coordinate(0, -1), PortFlow.INPUT),
                FluidPort(Coordinate(2, -1), PortFlow.INPUT),
                FluidPort(Coordinate(0, 3), PortFlow.OUTPUT),
                FluidPort(Coordinate(2, 3), PortFlow.OUTPUT),
            )
        // the oil refinery takes its fluids on the bottom and gives them on the top
        "oil-refinery" ->
            listOf(
                FluidPort(Coordinate(1, 5), PortFlow.INPUT),
                FluidPort(Coordinate(3, 5), PortFlow.INPUT),
                FluidPort(Coordinate(0, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(2, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(4, -1), PortFlow.OUTPUT),
            )
        "foundry" ->
            listOf(
                FluidPort(Coordinate(1, 5), PortFlow.INPUT),
                FluidPort(Coordinate(3, 5), PortFlow.INPUT),
                FluidPort(Coordinate(1, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(3, -1), PortFlow.OUTPUT),
            )
        // the cryogenic plant takes up to three fluids on the bottom and gives them on the top
        "cryogenic-plant" ->
            listOf(
                FluidPort(Coordinate(0, 5), PortFlow.INPUT),
                FluidPort(Coordinate(2, 5), PortFlow.INPUT),
                FluidPort(Coordinate(4, 5), PortFlow.INPUT),
                FluidPort(Coordinate(0, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(2, -1), PortFlow.OUTPUT),
                FluidPort(Coordinate(4, -1), PortFlow.OUTPUT),
            )
        // the electromagnetic plant can be fed and drained on every side
        "electromagnetic-plant" ->
            listOf(
                FluidPort(Coordinate(-1, 2), PortFlow.BOTH),
                FluidPort(Coordinate(4, 1), PortFlow.BOTH),
                FluidPort(Coordinate(2, 4), PortFlow.BOTH),
                FluidPort(Coordinate(1, -1), PortFlow.BOTH),
            )
        else -> emptyList()
    }

/** The outcome of turning an optimization result into a buildable plan. */
sealed interface BlueprintPlanResult {
    data class Planned(val plan: BlueprintPlan) : BlueprintPlanResult

    /** Nothing (sensible) can be built, with a human-readable explanation. */
    data class Unavailable(val reason: String) : BlueprintPlanResult
}

/**
 * Whole machines a plan builds for [production] at [scale]: the count the optimizer sized, rounded
 * up, and never more than [MAX_COPIES]. A machine count that should be whole but carries floating
 * point noise must not round up.
 */
internal fun plannedMachines(production: FactoryProduction, scale: Double): Int =
    ceil(production.amount * scale - 1e-6).toInt().coerceIn(1, MAX_COPIES)

/**
 * The reduced production plan a blueprint is laid out from: the [recipes] to build, the external
 * [sources] they draw from, and the [scale] the whole plan is drawn at. A [scale] below 1 means the
 * layout had to shrink to fit its belts; [label] explains that and how many copies reach full rate.
 *
 * [bands] hold the direct-feed groups: machine sequences that insert their intermediate straight
 * into the next machine instead of over a belt. Band members appear in [recipes] like any other
 * allocation, but their machine count is the band's, so a recipe can be built once per consumer.
 */
data class BlueprintPlan(
    val target: Material,
    val recipes: List<PlannedRecipe>,
    val sources: List<PlannedSource>,
    val scale: Double,
    val label: String,
    val bands: List<PlannedBand> = emptyList(),
)

/** One recipe to build, with the whole [machines] count it is built as. */
data class PlannedRecipe(
    val production: FactoryProduction,
    val machines: Double,
) {
    val recipe: Recipe
        get() = production.recipe

    val factory: Factory
        get() = production.factory

    val material: Material
        get() = production.material
}

/**
 * One direct-feed band: the allocations to build, left to right, and the insertions between them. A
 * band is one row, so its machines share the lane rows and the output belt.
 */
data class PlannedBand(
    /** The allocation of every machine, in machine order; indices into [BlueprintPlan.recipes]. */
    val machines: List<Int>,
    val links: List<PlannedLink>,
)

/**
 * One material inserted straight from one machine of a band into the next one, sitting in the gap
 * after machine [gap].
 */
data class PlannedLink(
    val gap: Int,
    val material: Material,
    /** Items per second crossing the gap, for the inserter count. */
    val flow: Double,
    /** Whether the producer is the left machine, i.e. the inserter picks up on the west side. */
    val producerIsLeft: Boolean,
)

/** An external input: one belt/pipe lane of [material] the plan consumes but does not produce. */
data class PlannedSource(val material: Material, val rate: Double, val fluid: Boolean)

/**
 * Reduces an [OptimizationResult] to a plan a single blueprint can build.
 *
 * Only the production the target needs is built: starting at the target recipe, an input is
 * followed to its single producer (the recipe that makes it) and that producer is built, even when
 * several recipes take it. Every other input — place-dependent recipes (mining/pumping/harvesting),
 * classic smelters, multi-output recipes, the complex/cyclic processes (oil refining, biochambers,
 * cryogenic plants, centrifuging, recycling, ...) — becomes an import. At most [maxRecipes] recipes
 * are built, each with exactly the machine count the optimizer sized for the requested rate.
 */
fun blueprintPlan(
    result: OptimizationResult,
    logistics: LogisticsCalculator,
    normalizeTarget: Double = 1.0,
    maxRecipes: Int = MAX_BLUEPRINT_RECIPES,
): BlueprintPlanResult {
    if (result.target.name == ALL_SCIENCE_PACKS) {
        return BlueprintPlanResult.Unavailable(
            "all-science-packs needs every science pack and is too large for one blueprint; " +
                "choose a specific material",
        )
    }

    fun isTarget(factory: FactoryProduction) = factory.recipe.name == result.recipe.name

    fun buildable(factory: FactoryProduction): Boolean {
        if (factory.factory == Factory.NO_FACTORY) return false
        // the target's producer is always kept, even when it would otherwise be left out
        if (isTarget(factory)) return true
        if (factory.recipe.categories.any { it in extractionCategories }) return false
        if (factory.factory in classicSmelters) return false
        if (factory.recipe.categories.any { it in externallyProcessedCategories }) return false
        // a recipe yielding several materials would need a mixed output belt, not supported yet
        if (factory.recipe.result.size > 1) return false
        return true
    }

    val candidates = result.factories.values.filter(::buildable)
    val producerOf = mutableMapOf<Material, MutableList<FactoryProduction>>()
    for (candidate in candidates) {
        candidate.recipe.result.keys.forEach {
            producerOf.getOrPut(it) { mutableListOf() }.add(candidate)
        }
    }

    // a material with exactly one producer can be built and fed on, even to several consumers: the
    // layout shares one belt from the producer when more than one recipe takes it
    val singleProducer = producerOf.keys.filter { producerOf.getValue(it).size == 1 }.toSet()

    val targetFactory =
        candidates.firstOrNull { it.recipe.name == result.recipe.name }
            ?: return BlueprintPlanResult.Unavailable(
                "no buildable factory for ${result.target.name}",
            )

    val included = mutableListOf<FactoryProduction>()
    fun include(factory: FactoryProduction) {
        if (included.any { it.recipe === factory.recipe } || included.size >= maxRecipes) return
        included += factory
        // build every input that has a single producer, until the recipe cap is reached
        factory.recipe.ingredients.keys
            .filter { it in singleProducer }
            .sortedBy { it.name }
            .forEach { include(producerOf.getValue(it).single()) }
    }
    include(targetFactory)

    // recompute producer/consumer among the actually included recipes
    val producers = mutableMapOf<Material, MutableList<FactoryProduction>>()
    val consumers = mutableMapOf<Material, MutableList<FactoryProduction>>()
    for (factory in included) {
        factory.recipe.result.keys.forEach {
            producers.getOrPut(it) { mutableListOf() }.add(factory)
        }
        factory.recipe.ingredients.keys.forEach {
            consumers.getOrPut(it) { mutableListOf() }.add(factory)
        }
    }

    // build exactly the requested rate: a factory per machine the optimizer sized, even when an
    // import lane would then need upgrading
    val scale = normalizeTarget

    // split the recipes into allocations and direct-feed bands: a recipe feeding another one in a
    // small whole-machine cycle is built interleaved with it, and gets one allocation per consumer
    val feed = directFeed(targetFactory.recipe, included, scale, logistics)

    val sources =
        consumers
            .filterKeys { it !in producers }
            .map { (material, factories) ->
                PlannedSource(
                    material = material,
                    rate = factories.sumOf { it.consumption(material) } * scale,
                    fluid = logistics.isFluid(material),
                )
            }
            .sortedBy { it.material.name }

    return BlueprintPlanResult.Planned(
        BlueprintPlan(
            target = result.target,
            recipes = feed.recipes,
            sources = sources,
            scale = scale,
            label = planLabel(result.target, scale / normalizeTarget),
            bands = feed.bands,
        ),
    )
}

/**
 * The blueprint label: the target plus, when the layout had to shrink to fit its belts, how much of
 * the requested rate it covers and how many copies reach the full rate.
 */
private fun planLabel(target: Material, share: Double): String =
    if (share >= 1.0) target.name
    else {
        val percent = (share * 100).roundToInt()
        val copies = ceil(1.0 / share).toInt()
        "${target.name} ($percent%, place ${copies}x for full rate)"
    }
