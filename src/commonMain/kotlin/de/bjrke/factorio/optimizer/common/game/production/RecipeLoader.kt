package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe

object RecipeLoader {

    private val cache = mutableMapOf<Boolean, ParsedRecipe>()

    /** Parses the base-game or Space-Age dump, depending on whether Space Age is active. */
    fun forActiveMods(spaceAge: Boolean = true) =
        cache.getOrPut(spaceAge) {
            parsePrototypes(loadPrototypeDump(spaceAge), allSciencePacks(spaceAge))
        }
}
