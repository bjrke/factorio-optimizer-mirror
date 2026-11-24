package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.game.context.GameContext
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.forSurface

/**
 * The production universe: the shipped dump parsed once per game and filtered per surface.
 *
 * Only the real universes are cached. A caller that owns its own [GameContext] (a test suite's
 * `gameCtx`) uses [parse], which builds a fresh [ParsedRecipe] and caches nothing: the result's
 * materials belong to that registry, so it must not be shared.
 */
object RecipeLoader {

    private val cache = mutableMapOf<Boolean, ParsedRecipe>()

    private val surfaceCache = mutableMapOf<Pair<Boolean, Surface>, ParsedRecipe>()

    /** Parses the base-game or Space-Age dump, depending on whether Space Age is active. */
    fun forActiveMods(spaceAge: Boolean = true) =
        cache.getOrPut(spaceAge) { PrototypeDump.load(spaceAge).parse(null) }

    /**
     * The parsed recipes filtered to [surface], cached so the surface-filtered set and its usage
     * index are built once instead of on every render.
     */
    fun forSurface(spaceAge: Boolean, surface: Surface) =
        surfaceCache.getOrPut(spaceAge to surface) { forActiveMods(spaceAge).forSurface(surface) }

    /** Parses the shipped dump into [gameCtx]; the caller owns the result and nothing is cached. */
    internal fun parse(spaceAge: Boolean, gameCtx: GameContext) =
        PrototypeDump.load(spaceAge).parse(gameCtx)
}
