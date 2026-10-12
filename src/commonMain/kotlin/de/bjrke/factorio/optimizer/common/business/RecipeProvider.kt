package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.ParsedRecipe

object RecipeProvider {

    private val cache = mutableMapOf<Boolean, ParsedRecipe>()

    fun availableMods() = selectableMods

    fun resolveMods(selected: Set<String>) = activeMods(selected)

    /** Parses the base-game or Space-Age dump, depending on whether Space Age is active. */
    fun forActiveMods(selected: Set<String>): ParsedRecipe {
        val spaceAge = SPACE_AGE_MOD in activeMods(selected)
        return cache.getOrPut(spaceAge) {
            parsePrototypes(loadPrototypeDump(spaceAge)) { defaultRecipes(it, spaceAge) }
        }
    }
}
