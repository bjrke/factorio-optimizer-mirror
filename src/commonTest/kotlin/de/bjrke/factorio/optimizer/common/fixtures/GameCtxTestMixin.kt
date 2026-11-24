package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.game.context.GameContext
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.forSurface

interface GameCtxTestMixin {

    val gameCtx: GameContext

    val materialRegistry
        get() = gameCtx.materialRegistry

    fun Material(name: String) = gameCtx.materialRegistry.Material(name)

    /** The dump parsed into this game's registry; only the immutable dump is shared. */
    fun forActiveMods(spaceAge: Boolean = true) = RecipeLoader.parse(spaceAge, gameCtx)

    /** The dump filtered to [surface] in this game's registry. */
    fun forSurface(surface: Surface, spaceAge: Boolean = true) =
        RecipeLoader.parse(spaceAge, gameCtx).forSurface(surface)
}
