package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlin.math.ceil

/** How many distinct recipes a single blueprint may build; the rest are imported. */
const val MAX_BLUEPRINT_RECIPES = 8

/** Upper bound on the machines of one recipe row, so a huge rate cannot explode the blueprint. */
internal const val MAX_COPIES = 9

/** Slack for a machine count that should be whole but carries floating-point noise. */
internal const val MACHINE_COUNT_EPSILON = 1e-6

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
    ceil(production.amount * scale - MACHINE_COUNT_EPSILON).toInt().coerceIn(1, MAX_COPIES)

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
