package de.bjrke.factorio.optimizer.common.game.prototype

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.context.MaterialRegistry

private val exclusions =
    setOf(
        // cyclic recipes are problematic
        "fish-breeding",
        "nutrients-from-fish",
        "kovarex-enrichment-process",
        // solid fuel from different sources confuse the layout, one will added per surface
        "solid-fuel-from-light-oil",
        "solid-fuel-from-heavy-oil",
        "solid-fuel-from-petroleum-gas",
        "solid-fuel-from-ammonia",
    )

/** The one solid-fuel recipe a plan may use on [surface]; the alternatives would split it. */
private fun solidFuel(surface: Surface) =
    when (surface) {
        // heavy oil is gained by a pump
        Surface.FULGORA -> "solid-fuel-from-heavy-oil"
        // ammonia is cheaper then oil
        Surface.AQUILO -> "solid-fuel-from-ammonia"
        // no oil and no ammonia available
        Surface.SPACE -> null
        // light oil has the best yield and allows rocket fuel chains
        else -> "solid-fuel-from-light-oil"
    }

private fun coalOrCarbon(surface: Surface) =
    when (surface) {
        // nauvis and vulcanus do have coal deposits
        Surface.NAUVIS,
        Surface.VULCANUS -> "coal-synthesis"
        else -> "carbon"
    }

/**
 * Recipes the optimizer leaves out of the LP on [surface], including when the target is one of
 * their products: the cycle names in [exclusions], every solid-fuel recipe but the surface's own,
 * and the coal/carbon recipe the surface does not need (coal is mined on Nauvis and Vulcanus, while
 * carbon elsewhere would need coal the surface does not have). The target material must come from
 * an allowed recipe instead, so e.g. uranium-235 is built by `uranium-processing`, not kovarex.
 */
internal fun unplannedRecipes(surface: Surface) = buildSet {
    addAll(exclusions)
    solidFuel(surface)?.let { remove(it) }
    add(coalOrCarbon(surface))
}

/** The real nutrient item the biochamber consumes. */
internal fun MaterialRegistry.nutrients() = Material("nutrients")

/**
 * The pseudo material a biochamber's nutrient demand is booked as. It is a distinct key from
 * [nutrients] so the nutrients a biochamber consumes do not close a cycle: bioflux is made in a
 * biochamber too. No recipe produces it, so the optimizer's LP imports it; the plan and the listing
 * show that import, while [Material.prototypeName] makes display and blueprint output name the real
 * nutrient item. The pseudo material renders with the nutrients icon.
 */
internal fun MaterialRegistry.biochamberFuel() =
    Material("nutrients-fuel", prototypeName = "nutrients")

/**
 * The one fuel the furnaces burn on [surface]. Coal is the mined fuel where the surface has a coal
 * patch; the others burn what their own chain provides: Fulgora refines solid fuel from heavy oil,
 * Aquilo from ammonia, Gleba makes rocket fuel from jelly and a space platform crushes carbon out
 * of carbonic asteroids. One fuel per surface also keeps the plan from splitting its demand across
 * several producers.
 */
internal fun MaterialRegistry.burnerFuel(surface: Surface): Material =
    when (surface) {
        Surface.NAUVIS,
        Surface.VULCANUS -> Material("coal")
        Surface.FULGORA,
        Surface.AQUILO -> Material("solid-fuel")
        // no coal and no oil; the jelly chain is the only fuel the surface builds itself
        Surface.GLEBA -> Material("rocket-fuel")
        // the carbonic asteroids are crushed for carbon right on the platform
        Surface.SPACE -> Material("carbon")
    }
