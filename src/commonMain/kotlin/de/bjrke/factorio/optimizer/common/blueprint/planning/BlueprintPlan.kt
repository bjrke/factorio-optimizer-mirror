package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.MAX_BLUEPRINT_RECIPES
import de.bjrke.factorio.optimizer.common.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.service.OptimizationResult
import de.bjrke.factorio.optimizer.common.special.directFeed
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Reduces an [OptimizationResult] to a plan a single blueprint can build.
 *
 * Only the production the target needs is built: starting at the target recipe, an input is
 * followed to its single producer (the recipe that makes it) and that producer is built, even when
 * several recipes take it. Every other input — place-dependent recipes (mining/pumping/harvesting),
 * classic smelters, multi-output recipes, the complex/cyclic processes (oil refining, biochambers,
 * cryogenic plants, centrifuging, recycling, ...) — becomes an import. At most [maxRecipes] recipes
 * are built (see [includedRecipes]), each with exactly the machine count the optimizer sized for
 * the requested rate.
 */
fun blueprintPlan(
    result: OptimizationResult,
    logistics: LogisticsCalculator,
    scale: Double = 1.0,
    maxRecipes: Int = MAX_BLUEPRINT_RECIPES,
): BlueprintPlanResult {
    if (result.target.name == ALL_SCIENCE_PACKS) {
        return BlueprintPlanResult.Unavailable(
            "all-science-packs needs every science pack and is too large for one blueprint; " +
                "choose a specific material",
        )
    }

    val targetFactory =
        planTarget(result.factories.values, result.recipe)
            ?: return BlueprintPlanResult.Unavailable(
                "no buildable factory for ${result.target.name}",
            )

    val included = includedRecipes(result.factories.values, targetFactory, maxRecipes)

    // split the recipes into planned recipes and direct-feed bands: a recipe feeding another one in
    // a small whole-machine cycle is built interleaved with it, one planned recipe per consumer
    val feed = directFeed(targetFactory.recipe, included, scale, logistics)

    return BlueprintPlanResult.Planned(
        BlueprintPlan(
            target = result.target,
            rows = feed.rows,
            sources = plannedSources(included, scale, logistics),
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
