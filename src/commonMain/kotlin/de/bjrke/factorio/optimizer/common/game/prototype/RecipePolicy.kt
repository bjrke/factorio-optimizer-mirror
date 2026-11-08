package de.bjrke.factorio.optimizer.common.game.prototype

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
