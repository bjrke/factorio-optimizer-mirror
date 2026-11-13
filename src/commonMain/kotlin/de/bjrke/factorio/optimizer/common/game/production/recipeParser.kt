package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.Subgroup
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceProperty
import de.bjrke.factorio.optimizer.common.game.prototype.surfaceProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Parses the `data.raw` JSON dumped by `factorio --dump-data` (see the `dumpPrototypes` gradle
 * task) into the optimizer's model. Only the prototype types the optimizer needs are read;
 * everything else in the dump is ignored.
 */
fun parsePrototypes(json: String): ParsedRecipe {
    val root =
        Json.parseToJsonElement(json) as? JsonObject
            ?: return ParsedRecipe(emptyList(), emptyList())

    val subgroups = mutableMapOf<String, Subgroup>()
    val noSubGroup = Subgroup()
    fun subGroup(name: String?) =
        when (name) {
            null -> noSubGroup
            else -> subgroups.getOrPut(name) { Subgroup(name) }
        }

    val parsedRecipes = buildList {
        for ((name, element) in root.table("recipe").filterValueIsInstance<JsonObject>()) {
            recipePrototype(name, element, ::subGroup)?.let(::add)
        }

        addAll(resourceRecipes(root))
        addAll(spoilageRecipes(root))
        addAll(customRecipes(root))
    }
        .associateBy(Recipe::name)

    // Only scrap and what scrap-recycling yields may enter the recycler. Keeping recycling recipes
    // one step past scrap avoids reverse chains (e.g. an accumulator recycling back into the
    // circuits that used to make it) that would otherwise beat the forward recipes.
    val scrapReach = parsedRecipes.scrapReach()
    val recipes =
        parsedRecipes.values
            .filter { recipe ->
                Category.RECYCLING !in recipe.categories ||
                    scrapReach.containsAll(recipe.ingredients.keys)
            }
            .sortedBy(Recipe::name)

    return ParsedRecipe(
        // Only materials a kept recipe produces: a hidden recipe's items (loaders, cheat entities,
        // ...) are dropped above but would otherwise leak in, and a material that merely appears as
        // an ingredient (with no recipe yielding it) has no surface or factory to be produced on.
        materials =
            recipes
                .flatMapTo(mutableSetOf()) {
                    it.result.keys
                }
                .sortedBy(Material::name),
        recipes = recipes,
        machineStats = machineStats(root),
        logistics = logisticsStats(root),
        fuelValues = fuelValues(root),
        surfaceProperties = surfaceProperties(root),
    )
}

/**
 * The physical properties of every surface: the `surface-property` defaults, overridden by each
 * planet's (or the space platform's) own `surface_properties`. The curated [Surface.properties]
 * fill a value the dump does not carry.
 */
private fun surfaceProperties(root: JsonObject): Map<Surface, Map<SurfaceProperty, Int>> {
    val defaults =
        root
            .table("surface-property")
            .filterValueIsInstance<JsonObject>()
            .mapNotNull { (name, element) ->
                val property = surfaceProperty(name) ?: return@mapNotNull null
                element.number("default_value")?.toInt()?.let { property to it }
            }
            .toMap()

    return Surface.entries.associateWith { surface ->
        val prototypes =
            if (surface == Surface.SPACE) root.table("surface") else root.table("planet")
        val overrides = prototypes.table(surface.prototype).table("surface_properties")
        SurfaceProperty.entries.associateWith { property ->
            overrides.number(property.identifier)?.toInt()
                ?: defaults[property]
                ?: surface.properties.getValue(property)
        }
    }
}

/** The energy every burnable item releases, from `data.raw.item.fuel_value`. */
private fun fuelValues(root: JsonObject) =
    root
        .table("item")
        .filterValueIsInstance<JsonObject>()
        .mapNotNull { (name, element) ->
            element.energyValue("fuel_value")?.let { Material(name) to it }
        }
        .toMap()

/** `scrap` and everything the scrap-recycling recipe yields. */
private fun Map<String, Recipe>.scrapReach() = buildSet {
    add(Material("scrap"))
    this@scrapReach["scrap-recycling"]?.run {
        addAll(result.keys)
    }
}
