package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import kotlinx.serialization.json.JsonObject

private const val SPACE_SCIENCE_PACK = "space-science-pack"
private const val ROCKET_SILO = "rocket-silo"
private const val ROCKET_PART = "rocket-part"
private const val SATELLITE = "satellite"

const val ALL_SCIENCE_PACKS = "all-science-packs"

/** Every science pack a lab consumes, from the dump's `lab.inputs` prototypes. */
private fun labInputs(root: JsonObject) =
    root
        .table("lab")
        .filterValueIsInstance<JsonObject>()
        .flatMap { (_, element) -> element.strings("inputs") }
        .distinct()

private fun allSciencePacks(sciencePacks: List<String>) =
    Recipe(
        name = ALL_SCIENCE_PACKS,
        ingredients = sciencePacks.associate { Material(it) to 1.0 },
        result = mapOf(Material(ALL_SCIENCE_PACKS) to 1.0),
        time = 1.0,
        categories = setOf(Category.SCIENCE_LAB),
    )

/**
 * The base game produces space science in the rocket silo, and the dump carries no
 * `space-science-pack` recipe then. A launch consumes the silo's `rocket_parts_required` rocket
 * parts and one satellite and yields 1000 packs, a fixed engine reward rather than a prototype
 * field.
 */
private fun spaceSciencePackForBaseMod(root: JsonObject): Recipe? {
    if (root.table("recipe").containsKey(SPACE_SCIENCE_PACK)) return null
    val rocketParts =
        root.table(ROCKET_SILO).table(ROCKET_SILO).number("rocket_parts_required")?.toInt() ?: 100
    return Recipe(
        name = SPACE_SCIENCE_PACK,
        ingredients =
            mapOf(
                Material(SATELLITE) to 1.0,
                Material(ROCKET_PART) to rocketParts.toDouble(),
            ),
        result = mapOf(Material(SPACE_SCIENCE_PACK) to 1000.0),
        time = 1.0,
        categories = setOf(Category.ROCKET_BUILDING),
    )
}

/**
 * Recipes the optimizer synthesises on top of the game's prototypes: the dummy recipe that consumes
 * every science pack the lab accepts (the default optimisation target) and, when the lab takes
 * space science but the dump has no recipe for it, the base game's rocket-silo launch. A dump with
 * no lab adds nothing, so a synthetic test dump is not given recipes it never declared.
 */
internal fun customRecipes(root: JsonObject) = buildList {
    val packs = labInputs(root)
    if (packs.isEmpty()) return@buildList
    add(allSciencePacks(packs))
    if (SPACE_SCIENCE_PACK in packs) spaceSciencePackForBaseMod(root)?.let(::add)
}
