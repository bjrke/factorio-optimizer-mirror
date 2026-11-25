package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.IndexedRecipe
import de.bjrke.factorio.optimizer.common.model.RecipeIndex

/**
 * The indexed form of [this] recipe for a fixture production.
 *
 * Every recipe shares the sentinel slot, so every fixture `IndexedRecipe` is equal to every other
 * one: `IndexedRecipe` compares slots only. That is deliberate — fixtures never assert slot numbers
 * — but a test that needs two productions of *different* recipes to differ must compare
 * `recipe.recipe`, and one that needs distinct slots builds its own indexes.
 */
internal fun Recipe.indexedForTest(): IndexedRecipe =
    IndexedRecipe.of(RecipeIndex(Int.MAX_VALUE), this)
