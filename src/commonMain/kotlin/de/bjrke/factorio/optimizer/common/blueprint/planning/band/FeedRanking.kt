package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import kotlin.math.max

// Candidate ranking for direct feed. A walk only discovers links; the score decides which links are
// worth trying first, independently of traversal order.

private const val BELT_SCORE_WEIGHT = 100.0
private const val BELT_REQUIREMENT_WEIGHT = 20.0
private const val BULK_SCORE_WEIGHT = 10.0
private const val ONE_MACHINE_SCORE = 20.0
private const val INGREDIENT_SCORE = 5.0
private const val RATIO_SCORE_WEIGHT = 10.0

/** Every link reachable from the target, best first. */
internal fun DirectFeedContext.candidateLinks(): List<FeedLink> =
    target
        .traverse()
        .distinct()
        .filter {
            it.ioRatio > 0.0 &&
                it.outputPerMachine > FEED_EPSILON &&
                it.inputPerMachine > FEED_EPSILON
        }
        .sortedWith(candidateComparator())
        .toList()

internal fun DirectFeedContext.candidateComparator(): Comparator<FeedLink> =
    compareByDescending<FeedLink> { candidateScore(it) }
        .thenBy { it.producer.recipe.ingredients.size }
        .thenBy { it.material.name }
        .thenBy { it.producer.recipe.name }
        .thenBy { it.consumer.recipe.name }

/**
 * How much a link is worth direct-feeding: belt volume saved, bulk gain when a recipe makes more
 * than it takes, a bonus for two machines that are only partly used, and penalties for the belt
 * lanes the pair would still need, extra solid ingredients and a worse ratio. A pair that already
 * needs many lanes scores below one that can internalize more, so grouping starts from the pair
 * that has room to grow.
 */
internal fun DirectFeedContext.candidateScore(link: FeedLink): Double {
    val producerRate = link.producer.production.amount * link.outputPerMachine
    val consumerRate = link.consumer.production.amount * link.inputPerMachine
    val beltSavings = logistics.beltCount(max(producerRate, consumerRate), logistics.beltStack)
    val beltRequirement = bandBeltMaterials(listOf(link)).size
    val solidIngredients = link.producer.recipe.ingredients.keys.count { !logistics.isFluid(it) }
    val solidInputs =
        link.producer.recipe.ingredients.filterKeys { !logistics.isFluid(it) }.values.sum()
    val bulkGain = (link.producer.recipe.result[link.material] ?: 0.0) / max(1.0, solidInputs)
    val oneMachine =
        if (
            link.producer.production.amount <= 1.0 + FEED_EPSILON &&
                link.consumer.production.amount <= 1.0 + FEED_EPSILON
        ) {
            1.0
        } else {
            0.0
        }
    return beltSavings * BELT_SCORE_WEIGHT +
        bulkGain * BULK_SCORE_WEIGHT +
        oneMachine * ONE_MACHINE_SCORE -
        beltRequirement * BELT_REQUIREMENT_WEIGHT -
        solidIngredients * INGREDIENT_SCORE -
        cyclePenalty(link.ioRatio) * RATIO_SCORE_WEIGHT
}
