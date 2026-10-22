package de.bjrke.factorio.optimizer.common.data

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlinx.serialization.json.JsonObject

/**
 * Recipes for spoilable prototypes spoiling into another item, derived from the `spoil_ticks` /
 * `spoil_result` fields the item and capsule prototypes carry. Spoil times are in ticks (60 per
 * second). Items that spoil into an entity rather than an item (`spoil_result` unset, e.g.
 * `biter-egg`) cannot be modelled as a recipe and are skipped.
 *
 * Recipes are named `<source>-spoiling` so they don't collide with the recipe that produces the
 * source item; the icon component maps that suffix back to the source icon.
 */
fun spoilageRecipes(root: JsonObject): List<Recipe> =
    listOf("item", "capsule").flatMap { kind ->
        root.table(kind).filterValueIsInstance<JsonObject>().mapNotNull { (name, element) ->
            val ticks = element.number("spoil_ticks") ?: return@mapNotNull null
            val result = element.string("spoil_result") ?: return@mapNotNull null

            Recipe(
                name = "$name-spoiling",
                ingredients = mapOf(Material(name) to 1.0),
                result = mapOf(Material(result) to 1.0),
                time = ticks / 60.0,
                categories = setOf(Category.SPOILING),
            )
        }
    }
