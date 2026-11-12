package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.extractionCategories

/**
 * Whether a recipe can stand in the blueprint on its own: place-dependent recipes
 * (mining/pumping/harvesting) and compound processes are skipped, as are imports and recipes that
 * would need a mixed output belt. Smelting is planned; the optimizer's intermediate limit decides
 * which smelting products are built. A recipe may have any number of fluid results, because fluids
 * leave a row through their own lanes; at most one non-fluid result may be produced, because a
 * second solid would need a second output belt. The target is not exempt here;
 * `DirectFeedContext.included` seeds whatever production the optimizer chose.
 */
internal fun buildable(logistics: LogisticsCalculator, recipe: Recipe) =
    recipe.categories.none { it in externallyProcessedCategories } &&
        recipe.result.keys.count { !logistics.isFluid(it) } <= 1

/**
 * Complex processes that are usually done elsewhere and therefore left out of a blueprint; their
 * products become imports. Only kept when the target itself comes from one of them. Oil refining
 * pulls in a long fluid chain, so its products not produced by a simple recipe stay imports. Simple
 * chemical-plant recipes (plastic, sulfur, ...) are not excluded; only their fluid inputs are.
 * Centrifuging is planned too; the optimizer discards its cyclic recipes separately. Smelting is
 * planned as well, so a furnace row is built when the optimizer keeps its product.
 */
private val externallyProcessedCategories =
    extractionCategories +
        setOf(
            Category.OIL_PROCESSING,
            Category.RECYCLING,
            Category.CRUSHING,
            Category.SPOILING,
        )
