package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.forSurface

object RecipeLoader {

    private val cache = mutableMapOf<Boolean, ParsedRecipe>()

    private val surfaceCache = mutableMapOf<Pair<Boolean, Surface>, ParsedRecipe>()

    /** Parses the base-game or Space-Age dump, depending on whether Space Age is active. */
    fun forActiveMods(spaceAge: Boolean = true) =
        cache.getOrPut(spaceAge) {
            parsePrototypes(loadPrototypeDump(spaceAge), allSciencePacks(spaceAge))
        }

    /**
     * The parsed recipes filtered to [surface], cached so the surface-filtered set and its usage
     * index are built once instead of on every render.
     */
    fun forSurface(spaceAge: Boolean, surface: Surface) =
        surfaceCache.getOrPut(spaceAge to surface) {
            forActiveMods(spaceAge).forSurface(surface)
        }
}
