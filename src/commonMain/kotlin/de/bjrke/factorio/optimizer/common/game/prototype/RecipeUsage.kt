package de.bjrke.factorio.optimizer.common.game.prototype

import de.bjrke.factorio.optimizer.common.core.model.Material

/** The default number of intermediate materials a plan may build. */
const val DEFAULT_INTERMEDIATE_LIMIT = 10

/** The smallest selectable intermediate limit; the target itself is always built. */
const val MIN_INTERMEDIATE_LIMIT = 1

/** The largest selectable intermediate limit. */
const val MAX_INTERMEDIATE_LIMIT = 50

/** Clamps a URL/CLI value to the selectable range, falling back to the default. */
fun validIntermediateLimit(value: String?): Int =
    (value?.toIntOrNull() ?: DEFAULT_INTERMEDIATE_LIMIT).coerceIn(
        MIN_INTERMEDIATE_LIMIT,
        MAX_INTERMEDIATE_LIMIT,
    )

/**
 * How many recipes transitively use each material, and the materials ordered by that count.
 *
 * A material used by many recipes is a hub (ores, plates) and is the first to be dropped when the
 * plan has to shrink; a material used by few recipes sits near the target and is kept. The count is
 * computed once per parsed recipe set and cached on [ParsedRecipe.usage].
 */
class RecipeUsage(recipes: List<Recipe>) {

    /** The number of recipes whose transitive ingredient closure contains the material. */
    val counts: Map<Material, Int> = buildMap {
        val producers =
            recipes
                .flatMap { recipe -> recipe.result.keys.map { it to recipe } }
                .groupBy({ it.first }, { it.second })
        for (recipe in recipes) {
            val seen = mutableSetOf<Material>()
            val stack = ArrayDeque(recipe.ingredients.keys)
            while (stack.isNotEmpty()) {
                val material = stack.removeLast()
                if (!seen.add(material)) continue
                put(material, (this[material] ?: 0) + 1)
                producers[material]?.forEach { producer -> stack.addAll(producer.ingredients.keys) }
            }
        }
    }

    /** Materials least-used first, tie-broken by name so the order is deterministic. */
    val order: List<Material> =
        counts.entries.sortedWith(compareBy({ it.value }, { it.key.name })).map { it.key }
}
