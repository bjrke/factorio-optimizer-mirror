package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DefaultRecipesTest {

    private fun recipes(spaceAge: Boolean) = allSciencePacks(spaceAge).associateBy(Recipe::name)

    private val baseSciencePacks =
        setOf(
            "automation-science-pack",
            "chemical-science-pack",
            "logistic-science-pack",
            "military-science-pack",
            "production-science-pack",
            "space-science-pack",
            "utility-science-pack",
        )

    private val spaceAgeSciencePacks =
        baseSciencePacks +
            setOf(
                "agricultural-science-pack",
                "cryogenic-science-pack",
                "electromagnetic-science-pack",
                "metallurgic-science-pack",
                "promethium-science-pack",
            )

    @Test
    fun baseGameScienceHasSevenPacks() {
        val all = recipes(spaceAge = false).getValue(ALL_SCIENCE_PACKS)
        assertEquals(baseSciencePacks, all.ingredients.keys.map(Material::name).toSet())
    }

    @Test
    fun spaceAgeScienceAddsPlanetPacks() {
        val all = recipes(spaceAge = true).getValue(ALL_SCIENCE_PACKS)
        assertEquals(spaceAgeSciencePacks, all.ingredients.keys.map(Material::name).toSet())
    }

    @Test
    fun baseGameSpaceScienceComesFromRocketSilo() {
        val space = recipes(spaceAge = false).getValue("space-science-pack")
        assertEquals(setOf(Category.ROCKET_BUILDING), space.categories)
    }

    @Test
    fun spaceAgeHasNoRocketSiloSpaceScienceFallback() {
        assertFalse(recipes(spaceAge = true).containsKey("space-science-pack"))
    }
}
