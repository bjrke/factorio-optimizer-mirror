package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.extractionCategories

/**
 * Whether a recipe can stand in the blueprint on its own: place-dependent recipes
 * (mining/pumping/harvesting), classic smelters and complex processes are skipped, as are imports
 * and recipes that would need a mixed output belt. A recipe may have any number of fluid results,
 * because fluids leave a row through their own lanes; at most one non-fluid result may be produced,
 * because a second solid would need a second output belt. The target is not exempt.
 * `DirectFeedContext.included` seeds it separately, and keeping it out of the per-material producer
 * index stops a self-consuming target (kovarex) from planning a second, belted row for its own
 * recipe.
 */
internal fun buildable(logistics: LogisticsCalculator, recipe: Recipe) =
    recipe.categories.none { it in externallyProcessedCategories } &&
        recipe.result.keys.count { !logistics.isFluid(it) } <= 1

/**
 * Complex or cyclic processes that are usually done elsewhere and therefore left out of a
 * blueprint; their products become imports. Only kept when the target itself comes from one of
 * them. Oil refining and cracking (and their bio/cryo Space Age shortcuts) pull in long fluid
 * chains, so e.g. heavy oil and petroleum gas derivatives like plastic are imported. Simple
 * chemical-plant recipes (lubricant, sulfur, ...) are *not* excluded; only their fluid inputs are.
 */
private val externallyProcessedCategories =
    extractionCategories +
        setOf(
            Category.OIL_PROCESSING,
            Category.CENTRIFUGING,
            Category.ORGANIC,
            Category.CRYOGENICS,
            Category.RECYCLING,
            Category.CRUSHING,
            Category.SMELTING,
            Category.SPOILING,
        )
