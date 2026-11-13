package de.bjrke.factorio.optimizer.common.game.prototype

/**
 * Drops every recipe the [surface] cannot run and every spoiling recipe whose input it lacks.
 *
 * This is a rulebook operation, not a URL codec: the entry points cache the result through
 * `RecipeLoader.forSurface`, so the parsed recipe set and its usage index are built once per
 * surface.
 *
 * The dump-wide [ParsedRecipe.materials] list is kept on purpose: the picker may offer a material
 * this surface cannot make, and `blueprintPlan` reports such a target as unavailable instead.
 * Recomputing the list from the kept recipes would hide those targets from the picker, while a
 * pasted URL still has to be rejected by the planner.
 */
fun ParsedRecipe.forSurface(surface: Surface): ParsedRecipe {
    val properties = surfaceProperties[surface] ?: surface.properties
    val kept = recipes.filter {
        surface.accepts(it.surfaceConditions, properties) &&
            // the planet-labeled subgroup is only a fallback for recipes without explicit
            // surface conditions: some of those fit several surfaces, e.g. the
            // quantum-processor (low pressure) is also assembled on space platforms
            (it.surfaceConditions.isNotEmpty() || surface.accepts(it.subgroup)) &&
            it.categories.any(surface::accepts) &&
            (it.categories.none(extractionCategories::contains) ||
                surface.acceptsResource(it.name)) &&
            // the auto-generated recycling recipes only exist where scrap is collected
            (Category.RECYCLING !in it.categories || surface == Surface.FULGORA)
    }

    // A spoiling recipe only makes sense where the spoiling item is actually produced. This keeps
    // bacteria (a free chest import on Fulgora) from being "imported only to be spoiled" into ore.
    val produced = buildSet {
        kept.forEach {
            if (Category.SPOILING !in it.categories) addAll(it.result.keys)
        }
    }

    return copy(
        recipes =
            kept.filter { recipe ->
                Category.SPOILING !in recipe.categories ||
                    produced.containsAll(recipe.ingredients.keys)
            },
        surface = surface,
    )
}
