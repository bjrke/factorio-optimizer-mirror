package de.bjrke.factorio.optimizer.common.band

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import kotlin.math.abs

// The machine-rate arithmetic that decides whether two recipes can be direct-fed, and the band
// model the search builds on. A link fits when a small whole-machine cycle stands in for the rate
// ratio; see cycleRatio.

/** Upper bound on the machines of one direct-feed band, so a band cannot run away. */
const val GROUP_MAX_MACHINES = 18

/** Under-supply accepted when no cycle matches the needed machine rate exactly. */
internal const val UNDER_SUPPLY = 1.05

/** Over-supply accepted; a shorter cycle wins over an exact but longer one. */
internal const val OVER_SUPPLY = 1.25

/** The longest cycle ratio `p + q` one link may be simplified to. */
internal const val MAX_RATIO_MACHINES = 6

/** Slack for the floating point rate and supply comparisons. */
internal const val FEED_EPSILON = 1e-9

/**
 * The smallest whole-machine cycle `p:q` (producer machines per consumer machine) that can stand in
 * for [ratio]: `ratio / 1.05 <= p / q <= ratio * 1.25`, with `p + q` at most 6. Ties go to the
 * closest ratio. Null when no small cycle fits, i.e. the link cannot be direct-fed.
 */
internal fun cycleRatio(ratio: Double): Pair<Int, Int>? {
    if (ratio <= 0.0) return null
    for (sum in 2..MAX_RATIO_MACHINES) {
        var best: Pair<Int, Int>? = null
        var bestError = Double.MAX_VALUE
        for (p in 1 until sum) {
            val q = sum - p
            val value = p.toDouble() / q
            if (value < ratio / UNDER_SUPPLY - FEED_EPSILON) continue
            if (value > ratio * OVER_SUPPLY + FEED_EPSILON) continue
            val error = abs(value - ratio) / ratio
            if (error < bestError) {
                best = p to q
                bestError = error
            }
        }
        best?.let {
            return it
        }
    }
    return null
}

/** A group of recipes that feed each other directly, with the whole machine need of each recipe. */
internal class FeedGroup(
    val links: List<FeedLink>,
    val needs: Map<Recipe, Int>,
)

/** One machine-to-machine insertion an arrangement realizes. */
internal class BandFeed(
    val gap: Int,
    val link: FeedLink,
    val flow: Double,
    val producerIsLeft: Boolean,
)

/** One arrangement of a group: the machine sequence and the insertions between the machines. */
internal class BandPlan(val machines: List<Recipe>, val feeds: List<BandFeed>)

/**
 * One link in the direct-feed graph: [production] makes [material], and [parent] is the link whose
 * consumer takes it. The root link (the target) has no parent, so its material leaves the plan.
 */
internal data class FeedLink(
    val ctx: DirectFeedContext,
    val production: FactoryProduction = ctx.target,
    val parent: FeedLink? = null,
) {

    val producer = production.recipe

    val consumer = parent?.production?.recipe

    val material = production.material

    val inputPerMachine by lazy {
        parent?.production?.inputPerMachine(material) ?: 0.0
    }

    val outputPerMachine by lazy { production.outputPerMachine() }

    val ioRatio by lazy { inputPerMachine / outputPerMachine }

    /** The links from this one up to the root, nearest first. */
    fun pathReverse(): Sequence<FeedLink> = sequence {
        var current = parent
        while (current != null) {
            yield(current)
            current = current.parent
        }
    }

    /** Yields this link (unless it is the root) and then every link reachable through it. */
    context(seq: SequenceScope<FeedLink>)
    suspend fun traverse() {
        if (parent != null) seq.yield(this)

        ingredients.forEach { it.traverse() }
    }

    private val pathRecipes by lazy {
        buildSet {
            add(production.recipe)
            for (link in pathReverse()) add(link.production.recipe)
        }
    }

    private fun producerOf(material: Material) = ctx.producers[material]?.singleOrNull()

    /**
     * Whether [material] closes a recipe cycle: an ancestor already supplies it, or its single
     * in-plan producer (this recipe included) is already on the path.
     */
    private fun closesCycle(material: Material) =
        !ctx.logistics.isFluid(material) &&
            (pathReverse().any { it.material == material } ||
                (producerOf(material)?.recipe?.let(pathRecipes::contains) ?: false))

    /**
     * Whether an ingredient can only come from a cycle. The walk prunes such an ingredient so it
     * terminates, and the link itself is dropped so a band cannot be built with a machine it can
     * never feed (`aTwoRecipeCycleStaysBelted`).
     */
    val hasCyclicIngredient by lazy { production.recipe.ingredients.keys.any(::closesCycle) }

    /**
     * The links that feed this production: one per solid ingredient with exactly one in-plan
     * producer, skipping fluids and cyclic materials so a recipe cycle is never entered twice.
     */
    val ingredients by lazy {
        production.recipe.ingredients.keys.flatMap { material ->
            // fluids and cycles
            if (ctx.logistics.isFluid(material) || closesCycle(material)) return@flatMap emptyList()
            // exactly one producer
            producerOf(material)?.let { listOf(FeedLink(ctx, it, this)) }.orEmpty()
        }
    }

    override fun toString() = material.name
}

/**
 * The plan the direct-feed search walks: the [target] production, every [included] production, the
 * [scale] the plan is drawn at, the [logistics] that size the inserters, and the per-material
 * producer index.
 */
internal data class DirectFeedContext(
    val target: FactoryProduction,
    val included: List<FactoryProduction>,
    val scale: Double,
    val logistics: LogisticsCalculator,
) {
    val byRecipe = included.associateBy { it.recipe }

    // the in-plan producers of each material
    val producers =
        included
            .flatMap { f -> f.recipe.result.keys.map { it to f } }
            .groupBy({ it.first }, { it.second })
}
