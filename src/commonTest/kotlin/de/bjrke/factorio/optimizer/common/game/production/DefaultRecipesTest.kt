package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The recipes the parser synthesises on top of the dump: the lab's inputs and the base launch. */
class DefaultRecipesTest {

    private fun recipes(json: String) =
        customRecipes(Json.parseToJsonElement(json) as JsonObject).associateBy(Recipe::name)

    private fun lab(inputs: Set<String>, extra: String = "") =
        """{"lab": {"lab": {"inputs": [${inputs.joinToString { "\"$it\"" }}]}}$extra}"""

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

    private val spaceAgeHasScienceRecipe = """, "recipe": {"space-science-pack": {}}"""

    @Test
    fun baseGameScienceHasSevenPacks() {
        val all = recipes(lab(baseSciencePacks)).getValue(ALL_SCIENCE_PACKS)
        assertEquals(baseSciencePacks, all.ingredients.keys.map(Material::name).toSet())
    }

    @Test
    fun spaceAgeScienceAddsPlanetPacks() {
        val all =
            recipes(lab(spaceAgeSciencePacks, spaceAgeHasScienceRecipe)).getValue(ALL_SCIENCE_PACKS)
        assertEquals(spaceAgeSciencePacks, all.ingredients.keys.map(Material::name).toSet())
    }

    @Test
    fun baseGameSpaceScienceComesFromRocketSilo() {
        val space =
            recipes(
                    lab(
                        baseSciencePacks,
                        """, "rocket-silo": {"rocket-silo": {"rocket_parts_required": 50}}""",
                    ),
                )
                .getValue("space-science-pack")
        assertEquals(setOf(Category.ROCKET_BUILDING), space.categories)
        assertEquals(50.0, space.ingredients.getValue(Material("rocket-part")))
        assertEquals(1.0, space.ingredients.getValue(Material("satellite")))
        assertEquals(1000.0, space.result.getValue(Material("space-science-pack")))
    }

    @Test
    fun spaceAgeHasNoRocketSiloSpaceScienceFallback() {
        assertContainsNot(
            recipes(lab(spaceAgeSciencePacks, spaceAgeHasScienceRecipe)),
            "space-science-pack",
        )
    }
}
