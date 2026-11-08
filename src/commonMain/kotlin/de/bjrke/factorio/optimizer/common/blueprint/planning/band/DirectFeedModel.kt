package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
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
    val needs: Map<FactoryProduction, Int>,
)

/** One machine-to-machine insertion an arrangement realizes. */
internal class BandFeed(
    val gap: Int,
    val link: FeedLink,
    val flow: Double,
    val producerIsLeft: Boolean,
)

/** One arrangement of a group: the machine sequence and the insertions between the machines. */
internal class BandPlan(val machines: List<FactoryProduction>, val feeds: List<BandFeed>)

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
     * filtered here; real solid cycles are kept out by `buildable`, so the visited set is only a
     * safety net. A future buildable cycle needs an explicit policy (see `pitfalls.md`).
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
 * One feed link: [producer] makes [material] and [consumer] takes it. A producer may have several
 * consumer links, and a consumer several producer links.
 */
internal data class FeedLink(
    val producer: FeedNode,
    val consumer: FeedNode,
    val material: Material,
) {

    val outputPerMachine by lazy { producer.production.outputPerMachine(material) }

    val inputPerMachine by lazy { consumer.production.inputPerMachine(material) }

    val ioRatio by lazy { inputPerMachine / outputPerMachine }

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
    val scale: Double,
    val logistics: LogisticsCalculator,
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
