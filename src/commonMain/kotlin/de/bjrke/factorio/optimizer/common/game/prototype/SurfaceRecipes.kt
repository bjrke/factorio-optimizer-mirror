package de.bjrke.factorio.optimizer.common.game.prototype

/**
 * Drops every recipe the [surface] cannot run and every spoiling recipe whose input it lacks.
 *
 * This is a rulebook operation, not a URL codec: the entry points cache the result through
 * `RecipeLoader.forSurface`, so the parsed recipe set and its usage index are built once per
 * surface.
 */
fun ParsedRecipe.forSurface(surface: Surface): ParsedRecipe {
    val kept = recipes.filter {
        surface.accepts(it.surfaceConditions) &&
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
