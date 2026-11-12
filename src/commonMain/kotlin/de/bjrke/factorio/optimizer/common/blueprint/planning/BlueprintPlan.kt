package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.band.directFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import kotlin.math.ceil
import kotlin.math.roundToInt

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

    val targetFactory =
        result.factories[result.recipe]
            ?: return BlueprintPlanResult.Unavailable(
                "no buildable factory for ${result.target.name}",
            )

    // split the recipes into rows and direct-feed bands: a recipe feeding another one in a small
    // whole-machine cycle is built interleaved with it, one production per consumer machine
    val feed =
        directFeed(
            targetFactory,
            result.factories,
            logistics,
            scale,
        )

    return BlueprintPlanResult.Planned(
        BlueprintPlan(
            target = result.target,
            rows = feed.rows,
            sources = plannedSources(feed.rows, logistics, scale),
            scale = scale,
            label = planLabel(result.target, scale / scale),
        ),
    )
}

/**
 * The blueprint label: the target plus, when the layout had to shrink to fit its belts, how much of
 * the requested rate it covers and how many copies reach the full rate.
 */
internal fun planLabel(target: Material, share: Double): String =
    if (share >= 1.0) target.name
    else {
        val percent = (share * 100).roundToInt()
        val copies = ceil(1.0 / share).toInt()
        "${target.name} ($percent%, place ${copies}x for full rate)"
    }
