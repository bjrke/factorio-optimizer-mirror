package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_MATERIALS_PER_BELT
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_ROW_BELTS
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.beltRowBudget
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.oneLaneCapacity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.pairsNeeded
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowInputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowOutputs
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.math.max

// The machine-rate arithmetic that decides how attractive a feed link is, and the band model the
// search builds on. Actual feasibility is decided by the window's plan-rate flow, not by a fixed
// per-machine ratio: overfeed is allowed, underfeed is not, and a worse ratio may still work when a
// repeated window builds more copies.

/** Upper bound on the machines of one direct-feed band, so a band cannot run away. */
const val GROUP_MAX_MACHINES = 10

/** A band with more than one link stays one machine shorter, keeping wide rows powerable. */
internal const val MULTI_LINK_MAX_MACHINES = 9

/** The longest window a group with [links] links may build. */
internal fun maxBandMachines(links: Int) =
    if (links > 1) MULTI_LINK_MAX_MACHINES else GROUP_MAX_MACHINES

/** The most recipes one band cycle may hold; the arrangement search is bounded by this. */
internal const val MAX_BAND_RECIPES = 8

/** The longest cycle ratio `p + q` one link may be simplified to for scoring. */
internal const val MAX_RATIO_MACHINES = 6

/** Slack for the floating point rate and supply comparisons. */
internal const val FEED_EPSILON = 1e-9

/** How much an underfed pattern costs against an exact ratio. */
internal const val UNDERFEED_PENALTY = 3.0

/** How much an overfed pattern costs against an exact ratio; overfeed is allowed. */
internal const val OVERFEED_PENALTY = 0.5

/**
 * The preferred small cycle `p:q` (producer machines per consumer machine) for [ratio], and the
 * cost of using it. A ratio below the pattern is underfed and costs more than an overfed one, but
 * neither is a hard rejection: the window's plan-rate flow decides whether it can be built.
 */
internal fun cycleFit(ratio: Double): Pair<Pair<Int, Int>?, Double> {
    if (ratio <= 0.0) return null to Double.MAX_VALUE
    var best: Pair<Int, Int>? = null
    var bestPenalty = Double.MAX_VALUE
    for (sum in 2..MAX_RATIO_MACHINES) {
        for (p in 1 until sum) {
            val q = sum - p
            val value = p.toDouble() / q
            val under = max(0.0, ratio - value) / ratio
            val over = max(0.0, value - ratio) / ratio
            val penalty = under * UNDERFEED_PENALTY + over * OVERFEED_PENALTY
            if (penalty < bestPenalty - FEED_EPSILON) {
                best = p to q
                bestPenalty = penalty
            }
        }
    }
    return best to bestPenalty
}

/** The preferred small cycle for [ratio], or null when the ratio is not positive. */
internal fun cycleRatio(ratio: Double) = cycleFit(ratio).first

/** The cost of the preferred small cycle for [ratio]. */
internal fun cyclePenalty(ratio: Double) = cycleFit(ratio).second

/** A group of recipes that feed each other directly, with the plan machine need of each recipe. */
internal class FeedGroup(
    val links: List<FeedLink>,
    val needs: Map<FactoryProduction, Double>,
)

/** The solid materials a band row belts and does not insert machine to machine. */
internal fun DirectFeedContext.bandBeltMaterials(links: List<FeedLink>): Set<Material> {
    val materials = bandBeltInputs(links) + bandBeltOutputs(links)
    return materials
}

/** The solid inputs a band draws from outside its links. */
internal fun DirectFeedContext.bandBeltInputs(links: List<FeedLink>): Set<Material> {
    val internal = links.mapTo(mutableSetOf()) { it.material }
    return links
        .asSequence()
        .flatMap { listOf(it.producer.production, it.consumer.production) }
        .distinct()
        .flatMap { production -> production.recipe.ingredients.keys }
        .filterNot { logistics.isFluid(it) }
        .filterNot { it in internal }
        .toSet()
}

/** The solid results a band puts on a belt rather than inserting them. */
internal fun DirectFeedContext.bandBeltOutputs(links: List<FeedLink>): Set<Material> {
    val internal = links.mapTo(mutableSetOf()) { it.material }
    return links
        .asSequence()
        .flatMap { listOf(it.producer.production, it.consumer.production) }
        .distinct()
        .flatMap { production -> production.recipe.result.keys }
        .filterNot { logistics.isFluid(it) }
        .filterNot { it in internal }
        .toSet()
}

/** The belt rows [links] need, ignoring lane capacity: the quick pre-filter for growth. */
internal fun DirectFeedContext.bandBeltRows(links: List<FeedLink>): Int {
    val inputs = bandBeltInputs(links)
    val outputs = bandBeltOutputs(links)
    val materials = (inputs + outputs).size
    val pairable = (inputs - outputs).size
    return materials - minOf(pairsNeeded(materials), pairable / MAX_MATERIALS_PER_BELT)
}

/** Whether [links] can still fit the row's belt budget before the expensive arrangement. */
internal fun DirectFeedContext.bandBeltMayFit(links: List<FeedLink>) =
    bandBeltRows(links) <= MAX_ROW_BELTS

/**
 * Whether the arranged [plan] fits the row's four belt rows. This is the precise check: the machine
 * rates decide which inputs pair without losing throughput and which only share a belt because the
 * row would otherwise have to drop a belt row.
 */
internal fun DirectFeedContext.bandBeltFits(plan: BandPlan): Boolean {
    val internal = plan.feeds.mapTo(mutableSetOf()) { it.link.material }
    val inputs = rowInputs(plan.machines, logistics).minus(internal)
    val outputs = rowOutputs(plan.machines, logistics).minus(internal)
    return beltRowBudget(inputs, outputs, logistics.oneLaneCapacity()).fits
}

/** One machine-to-machine insertion an arrangement realizes. */
internal class BandFeed(
    val gap: Int,
    val link: FeedLink,
    val flow: Double,
    val producerIsLeft: Boolean,
)

/**
 * One arrangement of a group: the machine sequence, the insertions between the machines, and the
 * ratio penalty of the sequence, computed once so the comparator does not recount the machines.
 */
internal class BandPlan(
    val machines: List<FactoryProduction>,
    val feeds: List<BandFeed>,
    val ratioPenalty: Double,
)

/**
 * One production in the direct-feed graph. A production may feed several consumers, so it is a
 * node, not a link: [ingredients] are the links to the producers of its inputs, and [consumers] are
 * the links that take its product.
 */
internal class FeedNode(
    val ctx: DirectFeedContext,
    val production: FactoryProduction,
) {

    val material = production.material

    val recipe = production.recipe

    /**
     * The links that feed this production: one per in-plan producer of a solid ingredient. Fluids
     * are never links. Multi-producer materials are not filtered here; the explicit stone and
     * solid-fuel policies keep them out of the direct-feed graph.
     */
    val ingredients by lazy {
        recipe.ingredients.keys
            .filterNot { ctx.logistics.isFluid(it) }
            .flatMap { ctx.producers[it].orEmpty() }
            .map { ctx.feedLink(it, production) }
    }

    /** Every plan consumer that takes this production's material. */
    val consumers: List<FeedLink> by lazy {
        recipe.result.keys
            .filterNot { ctx.logistics.isFluid(it) }
            .flatMap { ctx.consumers[it].orEmpty() }
            .map { ctx.feedLink(production, it) }
    }

    /**
     * Every ingredient link reachable from this node, once each. A production shared by several
     * consumers is expanded only the first time, so the walk terminates. Cyclic edges are not
     * filtered here; the optimizer's `unplannedRecipes` keeps real solid cycles out of the plan, so
     * the visited set is only a safety net. A future planned cycle needs an explicit policy (see
     * `pitfalls.md`).
     */
    fun traverse() = sequence {
        val visited = mutableSetOf<FeedNode>()

        context(seq: SequenceScope<FeedLink>)
        suspend fun FeedNode.walk() {
            if (!visited.add(this)) return
            for (link in ingredients) {
                seq.yield(link)
                link.producer.walk()
            }
        }

        this@FeedNode.walk()
    }

    override fun toString() = material.name
}

/**
 * The plan the direct-feed search walks: the [target] production, every [included] production, the
 * [scale] the plan is drawn at, the [logistics] that size the inserters, and the per-material
 * producer index.
 *
 * The search keys machines and needs by [FactoryProduction], so a recipe must be held by exactly
 * one production of [included] — [target] included, since it is one of them: two of the same recipe
 * would be counted as two different machines, splitting their needs and ratios.
 */
internal data class DirectFeedContext(
    val targetProduction: FactoryProduction,
    val factories: Map<Recipe, FactoryProduction>,
    val logistics: LogisticsCalculator,
    val scale: Double = 1.0,
    /**
     * The productions that may consume a material in the whole plan. The belted pass runs on a
     * subset of the plan, so it passes the main context's productions here; the default is this
     * context's own [included] set.
     */
    val consumerScope: Set<FactoryProduction>? = null,
) {
    private val nodeCache = mutableMapOf<FactoryProduction, FeedNode>()

    val target = nodeOf(targetProduction)

    val producers =
        factories
            .flatMap { (recipe, factory) -> recipe.result.keys.map { it to factory } }
            // stone is a byproduct of Vulcanus' lava recipes, so it is always imported instead of
            // being fed from one of several producers.
            .filter { (m, p) -> m.name != "stone" && buildable(logistics, p.recipe) }
            .groupBy({ (m, _) -> m }, { (_, p) -> p })
            .filterKeys { !logistics.isFluid(it) }

    val consumers =
        factories
            .flatMap { (recipe, factory) -> recipe.ingredients.keys.map { it to factory } }
            .filter { (_, p) -> buildable(logistics, p.recipe) }
            .groupBy({ (m, _) -> m }, { (_, p) -> p })
            .filterKeys { !logistics.isFluid(it) }

    /**
     * The target plus every production that alone supplies a material. The target is seeded even
     * when the optimizer runs several recipes for its material: it is always built, and its inputs
     * must still be reported as sources.
     */
    val included = buildMap {
        putAll(producers.values.mapNotNull { it.singleOrNull() }.associateBy { it.recipe })
        put(target.production.recipe, target.production)
    }

    /** The plan's consumers, including the ones outside a belted sub-plan. */
    val consumersInPlan = consumerScope ?: included.values.toSet()

    /** The one node for [production], created on first use. */
    internal fun nodeOf(production: FactoryProduction) =
        nodeCache.getOrPut(production) { FeedNode(this, production) }

    private val feedLinks = mutableMapOf<Pair<FactoryProduction, FactoryProduction>, FeedLink>()

    fun feedLink(p: FactoryProduction, c: FactoryProduction) =
        feedLinks.getOrPut(p to c) {
            FeedLink(
                nodeOf(p),
                nodeOf(c),
                c.recipe.ingredients.keys.intersect(p.recipe.result.keys).single(),
            )
        }
}
