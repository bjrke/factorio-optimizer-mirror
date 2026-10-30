package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import kotlin.math.abs

/**
 * The machine-rate arithmetic that decides whether two recipes can be direct-fed, and the band
 * model the search builds on. A link fits when a small whole-machine cycle stands in for the rate
 * ratio; see [cycleRatio].
 */
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

/** Items of [material] one machine of [production] takes per second. */
internal fun inputPerMachine(production: FactoryProduction, material: Material) =
    inputPerMachine(production, production.recipe.ingredients[material] ?: 0.0)

internal fun inputPerMachine(production: FactoryProduction, items: Double): Double {
    val time = production.recipe.time
    return if (time <= 0.0) 0.0 else production.speed * items / time
}

/**
 * Items of [material] one machine of [production] makes per second. The productivity bonus counts:
 * that is what a direct-fed consumer actually receives, so it belongs in the ratio.
 */
internal fun outputPerMachine(production: FactoryProduction, material: Material) =
    outputPerMachine(production, production.recipe.result[material] ?: 0.0)

internal fun outputPerMachine(production: FactoryProduction, items: Double): Double {
    val time = production.recipe.time
    return if (time <= 0.0) 0.0
    else production.speed * (1.0 + production.productivity) * items / time
}

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

/** One material that can be inserted straight from a producer's machines into a consumer's. */
internal data class FeedLink(val material: Material, val producer: Recipe, val consumer: Recipe)

/** A group of recipes that feed each other directly, with the whole machine need of each recipe. */
internal class FeedGroup(
    val recipes: List<Recipe>,
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
