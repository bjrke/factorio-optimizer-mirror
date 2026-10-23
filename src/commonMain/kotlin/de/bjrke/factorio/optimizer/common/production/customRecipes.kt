package de.bjrke.factorio.optimizer.common.production

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Recipe

private const val SPACE_SCIENCE_PACK = "space-science-pack"

const val ALL_SCIENCE_PACKS = "all-science-packs"

private val baseSciencePacks =
    listOf(
        "automation-science-pack",
        "chemical-science-pack",
        "logistic-science-pack",
        "military-science-pack",
        "production-science-pack",
        SPACE_SCIENCE_PACK,
        "utility-science-pack",
    )

private val spaceAgeSciencePacks =
    baseSciencePacks +
        listOf(
            "agricultural-science-pack",
            "cryogenic-science-pack",
            "electromagnetic-science-pack",
            "metallurgic-science-pack",
            "promethium-science-pack",
        )

private fun allSciencePacks(sciencePacks: List<String>) =
    Recipe(
        name = ALL_SCIENCE_PACKS,
        ingredients = sciencePacks.associate { Material(it) to 1.0 },
        result = mapOf(Material(ALL_SCIENCE_PACKS) to 1.0),
        time = 1.0,
        categories = setOf(Category.SCIENCE_LAB),
    )

/**
 * The base game produces space science in the rocket silo. In Space Age the mod ships its own
 * recipe (assembled from asteroids in space), so this fallback must not be added then.
 */
private fun spaceSciencePackForBaseMod() =
    Recipe(
        name = SPACE_SCIENCE_PACK,
        ingredients = mapOf(Material("satellite") to 1.0, Material("rocket-part") to 100.0),
        result = mapOf(Material(SPACE_SCIENCE_PACK) to 1000.0),
        time = 1.0,
        categories = setOf(Category.ROCKET_BUILDING),
    )

/**
 * Recipes the optimizer synthesises on top of the game's prototypes: the dummy recipe that consumes
 * every science pack (the default optimisation target) and, for the base game, the space science
 * recipe the rocket silo runs (Space Age ships its own recipe instead).
 */
fun allSciencePacks(spaceAge: Boolean) =
    if (spaceAge) listOf(allSciencePacks(spaceAgeSciencePacks))
    else
        listOf(
            spaceSciencePackForBaseMod(),
            allSciencePacks(baseSciencePacks),
        )
