package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import kotlin.jvm.JvmInline

/**
 * A recipe's dense slot in one [OptimizationResult].
 *
 * The slot is assigned once by [RecipeIndexer] and is the recipe's identity for every map and set
 * after the optimizer, so those collections hash one int instead of walking the recipe's ingredient
 * and result maps. It is only meaningful inside the result that minted it: recipes from two results
 * may share a slot number without being the same recipe.
 */
@JvmInline
value class RecipeIndex(val value: Int) {
    init {
        require(value >= 0) { "a recipe index must be non-negative, was $value" }
    }
}

/**
 * A [Recipe] with the dense [RecipeIndex] the optimizer assigned it.
 *
 * The fields mirror [Recipe] one-for-one so post-optimizer code reads the same recipe shape it
 * already knows. Equality and hashing use only [index]: the indexer creates one instance per recipe
 * of a result, and [RecipeIndexer] reuses it, so two values with the same index are the same
 * recipe.
 *
 * The constructor and [copy] are private, because a copy with a different field would be a second
 * value claiming the same identity. [RecipeIndexer] opts in through [of].
 */
@ConsistentCopyVisibility
data class IndexedRecipe
private constructor(
    val index: RecipeIndex,
    val recipe: Recipe,
) {

    inline val name
        get() = recipe.name

    inline val ingredients
        get() = recipe.ingredients

    inline val result
        get() = recipe.result

    inline val time
        get() = recipe.time

    inline val categories
        get() = recipe.categories

    inline val subgroup
        get() = recipe.subgroup

    inline val surfaceConditions
        get() = recipe.surfaceConditions

    override fun equals(other: Any?) = other is IndexedRecipe && index == other.index

    override fun hashCode() = index.value

    override fun toString() = "IndexedRecipe(index=$index, name=$name)"

    companion object {

        /** The indexed form of [recipe] in slot [index]; only [RecipeIndexer] mints the slot. */
        internal fun of(index: RecipeIndex, recipe: Recipe) =
            IndexedRecipe(
                index = index,
                recipe = recipe,
            )
    }
}

/**
 * Mints the [RecipeIndex] slots of one [OptimizationResult].
 *
 * A result owns one indexer: slots start at zero and are handed out on first use, so they are
 * dense. The cached [IndexedRecipe] is returned again for an equal [Recipe], which keeps one
 * instance per recipe and lets reference and index equality agree inside the result.
 */
internal class RecipeIndexer {
    private val indexed = mutableMapOf<Recipe, IndexedRecipe>()

    /** The indexed recipe for [recipe], minted at the next free slot on first use. */
    fun index(recipe: Recipe): IndexedRecipe =
        indexed.getOrPut(recipe) { IndexedRecipe.of(RecipeIndex(indexed.size), recipe) }
}
