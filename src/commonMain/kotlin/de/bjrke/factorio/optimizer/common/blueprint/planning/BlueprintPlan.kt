package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.band.BandPolicy
import de.bjrke.factorio.optimizer.common.blueprint.planning.band.DirectFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.band.directFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.planLabel
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.model.IndexedRecipe
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.util.EPSILON

/** Slack for a delivered rate that should fit one lane but carries floating-point noise. */
internal const val CAPACITY_EPSILON = EPSILON

/**
 * How often the planner may replan while it fits the product belts and the direct-feed bands. Each
 * shrink step is exact for a fixed banding, so the extra steps only cover a band search that
 * chooses differently at the smaller scale.
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
 * shrink instead, because thinning one row would starve its consumers. A forced band that is over
 * the machine budget shrinks the plan as well, so the material stays internal instead of falling
 * back onto a belt — otherwise the same recipe pair would shrink from a different baseline
 * depending on whether the band or its belt happened to be the limit. [BlueprintPlan.scale] then
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
) =
    when (val targetName = result.target.name) {
        ALL_SCIENCE_PACKS ->
            BlueprintPlanResult.Unavailable(
                "all-science-packs needs every science pack and is too large for one " +
                    "blueprint; choose a specific material",
            )
        else if Category.IMPORT in result.recipe.categories ->
            BlueprintPlanResult.Unavailable(
                "no recipe produces $targetName on this surface; choose a material a recipe makes there",
            )
        else if result.factories[result.recipe] == null ->
            BlueprintPlanResult.Unavailable("no buildable factory for $targetName")
        else -> plannedPlan(result, logistics, scale)
    }

/**
 * Splits, packs and shrinks the plan until it fits its product belts and every band fits its four
 * solid belt rows, then reports the settled plan. A band that the finished feed sides cannot pack
 * is rebuilt as its own rows.
 */
private fun plannedPlan(
    result: OptimizationResult,
    logistics: LogisticsCalculator,
    scale: Double,
): BlueprintPlanResult.Planned {
    var effective = scale
    var forced = emptySet<Material>()
    val rejected = mutableSetOf<Set<IndexedRecipe>>()
    val targetProduction = result.factories.getValue(result.recipe)

    fun directFeed() =
        directFeed(
            targetProduction,
            result.factories,
            logistics,
            effective,
            BandPolicy(forced, rejected),
        )

    var feed = directFeed()
    var steps = 0
    while (steps++ < MAX_CAPACITY_STEPS) {
        // Settles the band search for one fixed [effective] scale and [forced] set. A band whose
        // finished inputs land on opposite feed sides is rejected here and built as its own rows
        // instead, because the search only knows lane counts.
        //
        // Each pass adds at least one recipe set to the rejected groups, and only recipe sets the
        // search can produce are ever added, so the loop is bounded even though it is not capped
        // like the caller.
        while (true) {
            if (!rejected.addAll(failingBandGroups(feed.rows, result.target, effective, logistics)))
                break
            feed = directFeed()
        }
        // A forced band that is too long for the machine budget must shrink first: its material
        // would otherwise fall back onto a belt, and that belt's lane rate would shrink the plan
        // from a different baseline than the same plan's direct-fed neighbor.
        when {
            feed.bandOverflow.isNotEmpty() -> {
                effective = shrinkTo(effective, feed.bandOverflow)
                feed = directFeed()
            }
            feed.overCapacityOutputs.isNotEmpty() -> {
                // direct feed first, but only a material with one consumer: a shared product cannot
                // be internalized without stranding its other consumers, so it has to shrink
                // instead
                val newForced = feed.newForcedMaterials(feed.overCapacityOutputs, forced)
                if (newForced.isNotEmpty()) forced = forced + newForced
                else effective = shrinkTo(effective, feed.overCapacityOutputs)
                feed = directFeed()
            }
            else -> break
        }
    }
    return BlueprintPlanResult.Planned(
        BlueprintPlan(
            target = result.target,
            rows = feed.rows,
            sources = plannedSources(feed.rows, logistics),
            scale = effective,
            label = planLabel(result.target, (effective / scale).coerceAtMost(1.0)),
        ),
    )
}

/** The not-yet-forced over-capacity materials that only one consumer takes. */
private fun DirectFeed.newForcedMaterials(over: Map<Material, Double>, forced: Set<Material>) =
    over.keys.filterTo(mutableSetOf()) { it !in forced && consumers(it) == 1 }

/** How many distinct productions consume [material] in the plan. */
private fun DirectFeed.consumers(material: Material) =
    rows
        .flatMapTo(mutableSetOf()) { row ->
            row.machines.filter { material in it.recipe.ingredients }
        }
        .size

/**
 * The scale at which the tightest overflow — a lane share or a band's machine share — just fits.
 * The demand and the machine need are linear in the scale, so one step is exact; the small step
 * back below a limit that did not move covers a value that is only floating-point noise above it.
 */
private fun shrinkTo(
    scale: Double,
    over: Map<Material, Double>,
): Double {
    val limit = scale / over.values.max()
    return if (limit < scale) limit else scale * (1.0 - CAPACITY_EPSILON)
}
