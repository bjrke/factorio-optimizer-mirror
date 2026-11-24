package de.bjrke.factorio.optimizer.common.game.context

/**
 * The registries one game run shares. A material belongs to exactly one [MaterialRegistry], so
 * every material a run creates must come from [materialRegistry]; mixing registries makes equal
 * names unequal.
 *
 * The composition root: later registries (subgroups, ...) join it here. `game.prototype` reads them
 * through `ParsedRecipe.gameCtx`, so this package must stay below the rulebook and above the
 * vocabulary, depending only on `core.model`.
 */
class GameContext(val materialRegistry: MaterialRegistry)
