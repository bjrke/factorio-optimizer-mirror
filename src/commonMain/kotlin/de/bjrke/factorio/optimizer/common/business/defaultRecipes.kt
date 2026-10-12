package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe

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

private fun allSciencePacks(material: (String) -> Material, spaceAge: Boolean) =
    Recipe(
        name = ALL_SCIENCE_PACKS,
        ingredients =
            (if (spaceAge) spaceAgeSciencePacks else baseSciencePacks).associate {
                material(it) to 1.0
            },
        result = mapOf(material(ALL_SCIENCE_PACKS) to 1.0),
        time = 1.0,
        category = Category.SCIENCE_LAB,
    )

/**
 * The base game produces space science in the rocket silo. In Space Age the mod ships its own
 * recipe (assembled from asteroids in space), so this fallback must not be added then.
 */
private fun spaceSciencePack(material: (String) -> Material) =
    Recipe(
        name = SPACE_SCIENCE_PACK,
        ingredients = mapOf(material("satellite") to 1.0, material("rocket-part") to 100.0),
        result = mapOf(material(SPACE_SCIENCE_PACK) to 1000.0),
        time = 1.0,
        category = Category.ROCKET_BUILDING,
    )

/**
 * Recipes the optimizer synthesises on top of the game's prototypes: the dummy recipe that consumes
 * every science pack (the default optimisation target) and, for the base game, the space science
 * recipe the rocket silo runs (Space Age ships its own recipe instead).
 */
fun defaultRecipes(material: (String) -> Material, spaceAge: Boolean) =
    (if (spaceAge) emptyList() else listOf(spaceSciencePack(material))) +
        listOf(allSciencePacks(material, spaceAge))
