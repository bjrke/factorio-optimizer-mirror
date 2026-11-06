package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.extractionCategories
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/**
 * Whether a factory can stand in the blueprint on its own: place-dependent recipes
 * (mining/pumping/harvesting), classic smelters and complex processes are skipped. The target is
 * not exempt. `DirectFeedContext.included` seeds it separately, and keeping it out of the
 * per-material producer index stops a self-consuming target (kovarex) from planning a second,
 * belted row for its own recipe.
 */
internal fun buildable(factory: FactoryProduction): Boolean {
    if (factory.factory == Factory.NO_FACTORY) return false
    if (factory.recipe.categories.any { it in extractionCategories }) return false
    if (factory.recipe.categories.any { it in externallyProcessedCategories }) return false
    // a recipe yielding several materials would need a mixed output belt, not supported yet
    if (factory.recipe.result.size > 1) return false
    return true
}

/**
 * Complex or cyclic processes that are usually done elsewhere and therefore left out of a
 * blueprint; their products become imports. Only kept when the target itself comes from one of
 * them. Oil refining and cracking (and their bio/cryo Space Age shortcuts) pull in long fluid
 * chains, so e.g. heavy oil and petroleum gas derivatives like plastic are imported. Simple
 * chemical-plant recipes (lubricant, sulfur, ...) are *not* excluded; only their fluid inputs are.
 */
private val externallyProcessedCategories =
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
