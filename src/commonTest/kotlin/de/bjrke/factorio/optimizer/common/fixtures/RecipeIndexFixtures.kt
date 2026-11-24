package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.IndexedRecipe
import de.bjrke.factorio.optimizer.common.model.RecipeIndexer

// One indexer for the suites, so repeated fixture properties resolve to the same recipe identity.
// Tests never assert absolute slot numbers; a suite that does needs its own RecipeIndexer.
private val testRecipeIndexer = RecipeIndexer()

/** The indexed form of [this] recipe, reusing its slot for an equal recipe value. */
internal fun Recipe.indexedForTest(): IndexedRecipe = testRecipeIndexer.index(this)
