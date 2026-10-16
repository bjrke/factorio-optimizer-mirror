package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Dimension
import kotlin.math.ceil
import kotlin.math.roundToInt

/** How many distinct recipes a single blueprint may build; the rest are imported. */
const val MAX_BLUEPRINT_RECIPES = 8

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

/** The outcome of turning an optimization result into a buildable plan. */
sealed interface BlueprintPlanResult {
    data class Planned(val plan: BlueprintPlan) : BlueprintPlanResult

    /** Nothing (sensible) can be built, with a human-readable explanation. */
    data class Unavailable(val reason: String) : BlueprintPlanResult
}

/**
 * The reduced production plan a blueprint is laid out from: the [recipes] to build, the external
 * [sources] they draw from, and the [scale] the whole plan is drawn at. A [scale] below 1 means the
 * layout had to shrink to fit its belts; [label] explains that and how many copies reach full rate.
 */
data class BlueprintPlan(
    val target: Material,
    val recipes: List<PlannedRecipe>,
    val sources: List<PlannedSource>,
    val scale: Double,
    val label: String,
)

/**
 * One recipe to build, with its [machines] count (already scaled) and the input materials that are
 * built as part of the plan ([directInsert]). A built input may be direct-inserted or, when more
 * than one recipe takes it, shared over one belt.
 */
data class PlannedRecipe(
    val production: FactoryProduction,
    val machines: Double,
    val directInsert: Set<Material>,
) {
    val recipe: Recipe
        get() = production.recipe

    val factory: Factory
        get() = production.factory

    val material: Material
        get() = production.material
}

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

    val recipes = included.map { factory ->
        PlannedRecipe(
            production = factory,
            machines = factory.amount * scale,
            directInsert =
                factory.recipe.ingredients.keys.filterTo(mutableSetOf()) { it in producers },
        )
    }

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
            recipes = recipes,
            sources = sources,
            scale = scale,
            label = planLabel(result.target, scale / normalizeTarget),
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
