package de.bjrke.factorio.optimizer.common.prototype.selection

import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.prototype.Surface
import de.bjrke.factorio.optimizer.common.prototype.accepts
import de.bjrke.factorio.optimizer.common.prototype.acceptsResource
import de.bjrke.factorio.optimizer.common.prototype.extractionCategories

private val surfacesByCode = Surface.entries.filter { it.code != null }.associateBy { it.code }

/** Scans the `selection` URL parameter for a surface's single code; the default is Nauvis. */
fun surfaceFromUrl(s: String?) = s?.firstNotNullOfOrNull { surfacesByCode[it] }

/** Resolves the CLI's `--planet=` value: an exact [Surface.identifier] or a single code. */
fun surfaceFromPlanet(s: String?) =
    Surface.entries.firstOrNull { it.identifier == s } ?: surfaceFromUrl(s)

/** Drops every recipe the [surface] cannot run and every spoiling recipe whose input it lacks. */
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
    )
}
