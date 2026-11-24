package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.game.context.MaterialRegistry
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.Subgroup
import kotlinx.serialization.json.JsonObject

private const val SPACE_SCIENCE_PACK = "space-science-pack"
private const val ROCKET_SILO = "rocket-silo"
private const val ROCKET_PART = "rocket-part"
private const val SATELLITE = "satellite"

/** A launch's rocket-part cost when the dump does not carry the silo; Space Age's value. */
private const val DEFAULT_ROCKET_PARTS_REQUIRED = 50.0

const val ALL_SCIENCE_PACKS = "all-science-packs"

/** Every science pack a lab consumes, from the dump's `lab.inputs` prototypes. */
private fun labInputs(root: JsonObject) =
    root
        .table("lab")
        .filterValueIsInstance<JsonObject>()
        .flatMap { (_, element) -> element.strings("inputs") }
        .distinct()

private fun MaterialRegistry.allSciencePacks(sciencePacks: List<String>) =
    Recipe(
        name = ALL_SCIENCE_PACKS,
        ingredients = sciencePacks.associate { Material(it) to 1.0 },
        result = mapOf(Material(ALL_SCIENCE_PACKS) to 1.0),
        time = 1.0,
        categories = setOf(Category.SCIENCE_LAB),
    )

/**
 * Scales the dump's `rocket-part` recipe to the parts one launch consumes. Null when the dump has
 * no part recipe, because the part ingredients are the recipe's, not a constant of the engine.
 *
 * The time is the parts' crafting time at crafting speed 1; the machine's own speed is applied when
 * the optimizer sizes it. The launch interval after the last part (27.20 s in the base game, 26.95
 * s in Space Age on the wiki) is left out on purpose: it is shorter than the crafting and only
 * shrinks the launch rate, which is not worth a second hand-maintained constant. The silo's
 * `surface_conditions` gate the cycle, so a surface the silo cannot be built on gets no launch.
 */
private fun MaterialRegistry.launchRecipe(root: JsonObject): Recipe? {
    val part =
        recipePrototype(ROCKET_PART, root.table("recipe").table(ROCKET_PART)) { Subgroup() }
            ?: return null
    val siloJson = root.table(ROCKET_SILO).table(ROCKET_SILO)
    val parts =
        siloJson.number("rocket_parts_required")?.takeIf { it > 0.0 }
            ?: DEFAULT_ROCKET_PARTS_REQUIRED

    return Recipe(
        name = ROCKET_PART,
        ingredients = part.ingredients.mapValues { (_, amount) -> amount * parts },
        result = mapOf(Material(ROCKET_PART) to parts),
        time = parts * part.time,
        categories = part.categories,
        surfaceConditions = (part.surfaceConditions + siloJson.surfaceConditions()).distinct(),
    )
}

/**
 * The base game produces space science in the rocket silo, and the dump carries no
 * `space-science-pack` recipe then. One launch is one silo cycle: the silo crafts its rocket parts
 * and takes the satellite payload, and the launch yields 1000 packs, a fixed engine reward rather
 * than a prototype field.
 */
private fun MaterialRegistry.spaceSciencePackForBaseMod(root: JsonObject): Recipe? {
    if (root.table("recipe").containsKey(SPACE_SCIENCE_PACK)) return null
    return this.launchRecipe(root)?.let {
        it.copy(
            name = SPACE_SCIENCE_PACK,
            ingredients = it.ingredients + (Material(SATELLITE) to 1.0),
            result = mapOf(Material(SPACE_SCIENCE_PACK) to 1000.0),
        )
    }
}

/**
 * A Space Age launch carries cargo instead of yielding space science, so a `rocket-part` target is
 * the parts one launch needs. Its recipe is the whole silo cycle whose result is those parts and
 * whose time is the part crafting; it replaces the dump's one-part recipe, which is a fraction of a
 * launch and would report a part rate the silo cannot sustain while launching. The silo puts the
 * parts on its product belt, which in game merely removes the silo's cargo, so a player who wants
 * them for launches can delete that belt.
 */
private fun MaterialRegistry.rocketPartLaunchForSpaceAge(root: JsonObject) =
    if (!root.isSpaceAgeDump()) null else this.launchRecipe(root)

/**
 * Recipes the optimizer synthesises on top of the game's prototypes: the dummy recipe that consumes
 * every science pack the lab accepts (the default optimisation target), the base game's rocket-silo
 * launch when the lab takes space science but the dump has no recipe for it, and Space Age's launch
 * cycle for a rocket-part target. A dump with no lab adds nothing, so a synthetic test dump is not
 * given recipes it never declared.
 */
internal fun MaterialRegistry.customRecipes(root: JsonObject) = buildList {
    val packs = labInputs(root)
    if (packs.isEmpty()) return@buildList
    add(allSciencePacks(packs))
    if (SPACE_SCIENCE_PACK in packs) spaceSciencePackForBaseMod(root)?.let(::add)
    rocketPartLaunchForSpaceAge(root)?.let(::add)
}
