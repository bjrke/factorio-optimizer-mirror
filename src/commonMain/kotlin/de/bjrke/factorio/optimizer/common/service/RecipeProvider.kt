package de.bjrke.factorio.optimizer.common.service

import de.bjrke.factorio.optimizer.common.data.allSciencePacks
import de.bjrke.factorio.optimizer.common.data.loadPrototypeDump
import de.bjrke.factorio.optimizer.common.data.parsePrototypes
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe

object RecipeProvider {

    private val cache = mutableMapOf<Boolean, ParsedRecipe>()

    /** Parses the base-game or Space-Age dump, depending on whether Space Age is active. */
    fun forActiveMods(spaceAge: Boolean = true) =
        cache.getOrPut(spaceAge) {
            parsePrototypes(loadPrototypeDump(spaceAge), allSciencePacks(spaceAge))
        }
}
