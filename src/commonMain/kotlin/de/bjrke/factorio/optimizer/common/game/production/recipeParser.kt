package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.context.GameContext
import de.bjrke.factorio.optimizer.common.game.context.MaterialRegistry
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.Subgroup
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceProperty
import de.bjrke.factorio.optimizer.common.game.prototype.surfaceProperty
import kotlinx.serialization.json.JsonObject

/**
 * Parses the `data.raw` JSON dumped by `factorio --dump-data` (see the `dumpPrototypes` gradle
 * task) into the optimizer's model. Only the prototype types the optimizer needs are read;
 * everything else in the dump is ignored.
 *
 * The dump's `fluid` keys configure the registry before any material is created, so
 * [Material.isFluid] is fixed for the universe. Pass [gameCtx] to parse into an existing universe;
 * the default builds a fresh one from the dump. A supplied context must already agree with the dump
 * about which materials are fluids: [PrototypeDump.parse] rejects a mismatch. The shipped dumps go
 * through [PrototypeDump.load], which decodes them once; a root that is not a JSON object is not a
 * dump and fails.
 */
fun parsePrototypes(json: String, gameCtx: GameContext? = null): ParsedRecipe =
    PrototypeDump.of(json).parse(gameCtx)

/**
 * Builds a fresh [ParsedRecipe] in [gameCtx]'s registry. Nothing is cached: a parsed recipe holds
 * [Material]s from exactly one registry, so it cannot be shared between universes. Without a
 * [gameCtx] the dump creates a registry from its [PrototypeDump.fluidNames].
 *
 * A supplied [gameCtx] fixed every [Material.isFluid] before the dump was read, so it must already
 * agree with the dump's fluid classification; [requireFluidClassification] rejects a mismatch
 * instead of letting a fluid ride on belts or a solid through pipes.
 */
internal fun PrototypeDump.parse(gameCtx: GameContext?): ParsedRecipe {
    val newGameCtx = gameCtx ?: GameContext(MaterialRegistry(fluidNames))
    val materials = newGameCtx.materialRegistry

    val recipes = materials.recipes(root)
    if (gameCtx != null) requireFluidClassification(recipes)

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
        logistics = materials.logisticsStats(root),
        fuelValues = materials.fuelValues(root),
        surfaceProperties = surfaceProperties(root),
        gameCtx = newGameCtx,
    )
}

/**
 * The kept recipes' materials whose [Material.isFluid] disagrees with the dump's fluid table. Both
 * directions are checked: a dump fluid the registry calls solid, and a name the registry calls
 * fluid that the dump has as an item. Only recipe materials are checked: `data.raw.fluid` also
 * lists `fluid-unknown` and the `parameter-*` placeholders, which no kept recipe references and
 * which a test registry need not seed.
 */
private fun PrototypeDump.requireFluidClassification(recipes: List<Recipe>) {
    val mismatched = buildSet {
        for (recipe in recipes) {
            for (material in recipe.ingredients.keys + recipe.result.keys) {
                if ((material.name in fluidNames) != material.isFluid) add(material.name)
            }
        }
    }
    require(mismatched.isEmpty()) {
        "the GameContext registry disagrees with the dump about whether these are fluids: " +
            mismatched.sorted().joinToString(", ") +
            "; build the MaterialRegistry from the dump's data.raw.fluid keys"
    }
}

private fun MaterialRegistry.recipes(root: JsonObject): List<Recipe> {
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
    val scrapReach = scrapReach(parsedRecipes)
    return parsedRecipes.values
        .filter { recipe ->
            Category.RECYCLING !in recipe.categories ||
                scrapReach.containsAll(recipe.ingredients.keys)
        }
        .sortedBy(Recipe::name)
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
private fun MaterialRegistry.fuelValues(root: JsonObject) =
    root
        .table("item")
        .filterValueIsInstance<JsonObject>()
        .mapNotNull { (name, element) ->
            element.energyValue("fuel_value")?.let { Material(name) to it }
        }
        .toMap()

/** `scrap` and everything the scrap-recycling recipe yields. */
private fun MaterialRegistry.scrapReach(parsedRecipes: Map<String, Recipe>) = buildSet {
    add(Material("scrap"))
    parsedRecipes["scrap-recycling"]?.let { addAll(it.result.keys) }
}
