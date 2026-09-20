package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe

private const val SPACE_SCIENCE_PACK = "space-science-pack"

const val ALL_SCIENCE_PACKS = "all-science-packs"

private fun allSciencePacks(material: (String) -> Material) =
    Recipe(
        name = ALL_SCIENCE_PACKS,
        ingredients =
            mapOf(
                material("automation-science-pack") to 1.0,
                material("chemical-science-pack") to 1.0,
                material("logistic-science-pack") to 1.0,
                material("military-science-pack") to 1.0,
                material("production-science-pack") to 1.0,
                material(SPACE_SCIENCE_PACK) to 1.0,
                material("utility-science-pack") to 1.0,
            ),
        result = mapOf(material(ALL_SCIENCE_PACKS) to 1.0),
        time = 1.0,
        category = Category.SCIENCE_LAB,
    )

private fun spaceSciencePack(material: (String) -> Material) =
    Recipe(
        name = SPACE_SCIENCE_PACK,
        ingredients = mapOf(material("satellite") to 1.0, material("rocket-part") to 100.0),
        result = mapOf(material(SPACE_SCIENCE_PACK) to 1000.0),
        time = 1.0,
        category = Category.ROCKET_BUILDING,
    )

fun defaultRecipes(material: (String) -> Material) =
    listOf(spaceSciencePack(material), allSciencePacks(material))
