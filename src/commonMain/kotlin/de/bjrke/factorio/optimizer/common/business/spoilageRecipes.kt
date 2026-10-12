package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlinx.serialization.json.JsonObject

/**
 * Recipes for spoilable prototypes rotting into another item, derived from the `spoil_ticks` /
 * `spoil_result` fields the item and capsule prototypes carry. Spoil times are in ticks (60 per
 * second). Items that spoil into an entity rather than an item (`spoil_result` unset, e.g.
 * `biter-egg`) cannot be modelled as a recipe and are skipped.
 *
 * Recipes are named `<source>-rotting` so they don't collide with the recipe that produces the
 * source item; the icon component maps that suffix back to the source icon.
 */
fun spoilageRecipes(root: JsonObject, material: (String) -> Material): List<Recipe> =
    listOf("item", "capsule").flatMap { kind ->
        root.table(kind)?.entries.orEmpty().mapNotNull { (name, element) ->
            val prototype = element as? JsonObject ?: return@mapNotNull null
            val ticks = prototype.number("spoil_ticks") ?: return@mapNotNull null
            val result = prototype.string("spoil_result") ?: return@mapNotNull null

            Recipe(
                name = "$name-rotting",
                ingredients = mapOf(material(name) to 1.0),
                result = mapOf(material(result) to 1.0),
                time = ticks / 60.0,
                category = Category.ROTTING,
            )
        }
    }
