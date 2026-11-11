package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import kotlin.math.max

// Candidate ranking for direct feed. A walk only discovers links; the score decides which links are
// worth trying first, independently of traversal order.

private const val BELT_SCORE_WEIGHT = 100.0
private const val BELT_REQUIREMENT_WEIGHT = 20.0
private const val BULK_SCORE_WEIGHT = 10.0
private const val ONE_MACHINE_SCORE = 20.0
private const val INGREDIENT_SCORE = 5.0
private const val RATIO_SCORE_WEIGHT = 10.0

/** A material another recipe still needs is a belt source, not a private band input. */
private const val SHARED_CONSUMER_SCORE_WEIGHT = 25.0

/** Building a producer far beyond the plan's machine need is wasted capacity. */
private const val OVERFEED_SCORE_WEIGHT = 30.0

/**
 * One feed link: [producer] makes [material] and [consumer] takes it. A producer may have several
 * consumer links, and a consumer several producer links.
 */
internal data class FeedLink(
    val producer: FeedNode,
    val consumer: FeedNode,
    val material: Material,
) {
    val name = "${material.name}#${producer.recipe.name}#${consumer.recipe.name}"

    val outputPerMachine by lazy { producer.production.outputPerMachine(material) }

    val inputPerMachine by lazy { consumer.production.inputPerMachine(material) }

    val ioRatio by lazy { inputPerMachine / outputPerMachine }

    override fun toString() = material.name

    /**
     * How much a link is worth direct-feeding: belt volume saved, bulk gain when a recipe makes
     * more than it takes, a bonus for two machines that are only partly used, and penalties for the
     * belt lanes the pair would still need, extra solid ingredients and a worse ratio. A pair that
     * already needs many lanes scores below one that can internalize more, so grouping starts from
     * the pair that has room to grow.
     */
    internal val candidateScore by lazy {
        val ctx = producer.ctx
        val producerAmount = producer.production.amount * ctx.scale
        val consumerAmount = consumer.production.amount * ctx.scale

        val beltSavings =
            ctx.logistics.beltCount(
                max(
                    producerAmount * outputPerMachine,
                    consumerAmount * inputPerMachine,
                ),
                ctx.logistics.beltStack,
            )
        val beltRequirement = ctx.bandBeltRows(listOf(this))

        val solidInputs = producer.recipe.ingredients.filterKeys { !ctx.logistics.isFluid(it) }
        val bulkGain =
            (producer.recipe.result[material] ?: 0.0) / max(1.0, solidInputs.values.sum())
        val oneMachine =
            if (producerAmount <= 1.0 + FEED_EPSILON && consumerAmount <= 1.0 + FEED_EPSILON) 1.0
            else 0.0
        // a shared product internalized into one consumer strands the other consumers
        val outsideConsumers =
            ctx.consumersInPlan.count {
                it != consumer.production && material in it.recipe.ingredients
            }
        val neededProducer = consumerAmount * ioRatio
        val overfeed =
            if (producerAmount > 0.0) max(0.0, producerAmount - neededProducer) / producerAmount
            else 0.0
        beltSavings * BELT_SCORE_WEIGHT +
            bulkGain * BULK_SCORE_WEIGHT +
            oneMachine * ONE_MACHINE_SCORE -
            beltRequirement * BELT_REQUIREMENT_WEIGHT -
            solidInputs.size * INGREDIENT_SCORE -
            cyclePenalty(ioRatio) * RATIO_SCORE_WEIGHT -
            outsideConsumers * SHARED_CONSUMER_SCORE_WEIGHT -
            overfeed * OVERFEED_SCORE_WEIGHT
    }
}

/**
 * The canonical order of links: [FeedLink.name] keeps the material, then both recipes. It is both
 * the tie-break of [linkComparator] and the order [linkKey] caches link sets in, so the score never
 * decides what counts as the same group.
 */
internal val linkNameComparator = compareBy<FeedLink> { it.name }

/** Best first: higher score, then fewer solid ingredients, then [linkNameComparator]. */
internal val linkComparator =
    compareByDescending<FeedLink> { it.candidateScore }
        .thenBy { it.producer.recipe.ingredients.size }
        .then(linkNameComparator)

/** [links] in [linkNameComparator] order, the canonical key of one link set. */
internal fun linkKey(links: List<FeedLink>) = links.sortedWith(linkNameComparator)

/** Every link reachable from the target, best first. */
internal fun DirectFeedContext.candidateLinks() =
    target
        .traverse()
        .distinct()
        .filter {
            it.ioRatio > 0.0 &&
                it.outputPerMachine > FEED_EPSILON &&
                it.inputPerMachine > FEED_EPSILON
        }
        .sortedWith(linkComparator)
        .toList()
