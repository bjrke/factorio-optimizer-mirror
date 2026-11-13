package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.band.DirectFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.band.directFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.beltStackFor
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Slack for a delivered rate that should fit one lane but carries floating-point noise. */
private const val CAPACITY_EPSILON = 1e-9

/**
 * How often the planner may replan while it fits the product belts. Each shrink step is exact for a
 * fixed banding, so the extra steps only cover a band search that chooses differently at the
 * smaller scale.
 */
private const val MAX_CAPACITY_STEPS = 6

/**
 * Reduces an [OptimizationResult] to a plan a single blueprint can build.
 *
 * Only the production the target needs is built: the target recipe and every other recipe that
 * alone supplies one of the plan's materials, even when several recipes take that material. Every
 * other input — place-dependent recipes (mining/pumping/harvesting), oil processing, recycling,
 * crushing, spoiling, recipes that would need a mixed output belt, and the cycles the optimizer
 * discards — becomes an import; the remaining simple chemistry, organic, cryogenic, centrifuging
 * and smelting recipes are built. Each recipe is built with exactly the machine count the optimizer
 * sized for the requested rate.
 *
 * A solid row's product belt fills one lane only, so no belt can deliver more than one lane's
 * capacity. A belt is stacked only when its producer stacks it (a stack inserter, or a
 * direct-output machine such as the big mining drill); a non-stacking inserter leaves its output
 * unstacked, so the belt capacity research level cannot raise the limit. When a delivered rate
 * exceeds its lane, the plan first forces the material's producer and consumer into one direct-feed
 * band; a material that cannot be direct-fed (the target, or a shared product) makes the whole plan
 * shrink instead, because thinning one row would starve its consumers. [BlueprintPlan.scale] then
 * holds the drawn scale and [BlueprintPlan.label] reports how many copies reach the requested rate.
 *
 * The rate checked here is the rate the plan must deliver — the target's requested rate and each
 * intermediate's consumer demand — limited by what the built rows can make. A row that makes more
 * than its belt can carry is throttled by that belt, so overproduction is not a capacity failure; a
 * row that cannot even make the demand is underbuilt (for example by the machine cap), not over
 * capacity, and must not shrink the plan.
 *
 * A target no recipe produces is the optimizer's [Category.IMPORT] pseudo source, an import rather
 * than a machine; it is reported as [BlueprintPlanResult.Unavailable] instead of being laid out.
 */
fun blueprintPlan(
    result: OptimizationResult,
    logistics: LogisticsCalculator,
    scale: Double = 1.0,
): BlueprintPlanResult {
    if (result.target.name == ALL_SCIENCE_PACKS) {
        return BlueprintPlanResult.Unavailable(
            "all-science-packs needs every science pack and is too large for one blueprint; " +
                "choose a specific material",
        )
    }
    if (Category.IMPORT in result.recipe.categories) {
        return BlueprintPlanResult.Unavailable(
            "no recipe produces ${result.target.name} on this surface; choose a material a " +
                "recipe makes there",
        )
    }

    val targetFactory =
        result.factories[result.recipe]
            ?: return BlueprintPlanResult.Unavailable(
                "no buildable factory for ${result.target.name}",
            )

    var effective = scale
    var forced = emptySet<Material>()

    // split the recipes into rows and direct-feed bands: a recipe feeding another one in a small
    // whole-machine cycle is built interleaved with it, one production per consumer machine
    var feed = directFeed(targetFactory, result.factories, logistics, effective, forced)

    fun planned() =
        BlueprintPlanResult.Planned(
            BlueprintPlan(
                target = result.target,
                rows = feed.rows,
                sources = plannedSources(feed.rows, logistics),
                scale = effective,
                label = planLabel(result.target, (effective / scale).coerceAtMost(1.0)),
            ),
        )

    repeat(MAX_CAPACITY_STEPS) {
        val over = feed.overCapacityOutputs(logistics)
        if (over.isEmpty()) return planned()

        // direct feed first, but only a material with one consumer: a shared product cannot be
        // internalized without stranding its other consumers, so it has to shrink instead
        val newForced =
            over.keys.filterTo(mutableSetOf()) { material ->
                material !in forced && feed.consumers(material) == 1
            }
        if (newForced.isNotEmpty()) {
            forced = forced + newForced
        } else {
            effective = shrinkToLane(effective, over)
        }
        feed = directFeed(targetFactory, result.factories, logistics, effective, forced)
    }
    return planned()
}

/**
 * The solid outputs whose delivered rate exceeds their producer's one-lane capacity at the drawn
 * scale, mapped to the share of a lane they need. A direct-output machine stacks its own product,
 * so its lane capacity follows the requested belt stack even when the selected inserter cannot
 * stack; any other product is stacked only by a stacking inserter.
 */
private fun DirectFeed.overCapacityOutputs(
    logistics: LogisticsCalculator,
): Map<Material, Double> {
    val onBelt = rows.flatMapTo(mutableSetOf()) { row -> row.beltMaterials }
    return buildMap {
        for ((material, demand) in beltRates) {
            if (material !in onBelt) continue
            val capacity = laneCapacity(material, logistics)
            if (
                demand > capacity + CAPACITY_EPSILON &&
                    builtRate(material) > capacity + CAPACITY_EPSILON
            ) {
                put(material, demand / capacity)
            }
        }
    }
}

/** The one-lane rate of [material]'s product belt, following its producer's stacking. */
private fun DirectFeed.laneCapacity(
    material: Material,
    logistics: LogisticsCalculator,
): Double {
    val producer = rows.firstNotNullOfOrNull { row -> row.producerOf(material) }
    val stacks =
        if (producer != null && producer.factory.outputsDirectly) {
            producer.factory.stacksOnBelt
        } else {
            logistics.rates.stacksOnBelt
        }
    return logistics.beltThroughput(logistics.beltStackFor(stacks)) / logistics.lanes
}

/**
 * The rate the built machines can put on a belt for [material] before the belt throttles them. A
 * row that cannot even make the demanded rate is underbuilt, not over capacity, so it does not
 * shrink the plan; once it can make the demand, the demand decides.
 */
private fun DirectFeed.builtRate(material: Material): Double = rows.sumOf { row ->
    val production = row.producerOf(material) ?: return@sumOf 0.0
    row.machines.count { it == production } * production.outputPerMachine(material)
}

/** How many distinct productions consume [material] in the plan. */
private fun DirectFeed.consumers(material: Material) =
    rows
        .flatMapTo(mutableSetOf()) { row ->
            row.machines.filter { material in it.recipe.ingredients }
        }
        .size

/**
 * The scale at which the tightest over-capacity delivery just fits one lane. [over] holds each
 * material's needed share of a lane, so the demand is linear in the scale and one step is exact;
 * the small step back below a limit that did not move covers a rate that is only floating-point
 * noise above the lane.
 */
private fun shrinkToLane(scale: Double, over: Map<Material, Double>): Double {
    val limit = scale / over.values.max()
    return if (limit < scale) limit else scale * (1.0 - CAPACITY_EPSILON)
}

/**
 * The blueprint label: the target plus, when the plan had to shrink to fit its product belts, how
 * much of the requested rate it covers and how many copies reach the full rate.
 */
internal fun planLabel(target: Material, share: Double): String =
    if (share >= 1.0) target.name
    else {
        val percent = (share * 100).roundToInt()
        val copies = ceil(1.0 / share).toInt()
        "${target.name} ($percent%, place ${copies}x for full rate)"
    }
