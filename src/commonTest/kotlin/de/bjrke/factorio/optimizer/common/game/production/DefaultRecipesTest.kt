package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.lowDensityStructure
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.rocketFuel
import de.bjrke.factorio.optimizer.common.fixtures.rocketPart
import de.bjrke.factorio.optimizer.common.fixtures.satellite
import de.bjrke.factorio.optimizer.common.fixtures.spaceScience
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceCondition
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceProperty
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The recipes the parser synthesises on top of the dump: the lab's inputs plus the silo's launch
 * cycle, which is one launch's worth of rocket parts.
 */
class DefaultRecipesTest : BaseGameCtxTest() {

    private fun recipes(json: String) =
        materialRegistry
            .customRecipes(Json.parseToJsonElement(json) as JsonObject)
            .associateBy(Recipe::name)

    /** A dump with the lab's [inputs], the [recipePrototypes] and optionally a [silo] prototype. */
    private fun dump(
        inputs: Set<String>,
        recipePrototypes: Map<String, String> = emptyMap(),
        silo: String? = null,
    ): String {
        val siloJson =
            if (silo != null)
                """
                "rocket-silo": {
                    "rocket-silo": $silo
                },
                """
            else ""
        val inputJson = inputs.joinToString { "\"$it\"" }
        val recipeJson =
            if (recipePrototypes.isNotEmpty()) {
                recipePrototypes.entries.joinToString(
                    prefix =
                        """
                        "recipe": {
                        """,
                    postfix = "},",
                ) { (name, json) ->
                    """
                    "$name": $json
                    """
                }
            } else ""
        return """
            {
                $recipeJson
                $siloJson
                "lab": {
                    "lab": {"inputs": [$inputJson]}
                }
            }
            """
    }

    private fun silo(parts: Int, surfaceConditions: String? = null) =
        """
        {
            "rocket_parts_required": $parts
            ${surfaceConditions?.let { """, "surface_conditions": $it""" }.orEmpty()}
        }
        """

    /** One rocket part from [amount] of each ingredient; Space Age uses 1, the base game 10. */
    private fun rocketPart(amount: Int) =
        """
        {
            "ingredients": [
                {"type": "item", "name": "processing-unit", "amount": $amount},
                {"type": "item", "name": "low-density-structure", "amount": $amount},
                {"type": "item", "name": "rocket-fuel", "amount": $amount}],
            "results": [{"type": "item", "name": "rocket-part", "amount": 1}],
            "energy_required": 3,
            "categories": ["rocket-building"]
        }
        """

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

    /**
     * Space Age's dump, marked by the `tree-seed` recipe; it ships its own space-science recipe.
     */
    private val spaceAgeRecipes =
        mapOf("space-science-pack" to "{}", "tree-seed" to "{}", "rocket-part" to rocketPart(1))

    @Test
    fun baseGameScienceHasSevenPacks() {
        val all = recipes(dump(baseSciencePacks)).getValue(ALL_SCIENCE_PACKS)
        assertEquals(baseSciencePacks, all.ingredients.keys.map(Material::name).toSet())
    }

    @Test
    fun spaceAgeScienceAddsPlanetPacks() {
        val all = recipes(dump(spaceAgeSciencePacks, spaceAgeRecipes)).getValue(ALL_SCIENCE_PACKS)
        assertEquals(spaceAgeSciencePacks, all.ingredients.keys.map(Material::name).toSet())
    }

    /**
     * The base game's launch is one silo cycle: the silo crafts the 100 parts the launch consumes
     * out of their ingredients, takes the satellite and launches, yielding 1000 packs.
     */
    @Test
    fun baseGameSpaceScienceLaunchIsOneSiloCycle() {
        val space =
            recipes(
                    dump(
                        baseSciencePacks,
                        mapOf("rocket-part" to rocketPart(10)),
                        silo(100),
                    ),
                )
                .getValue("space-science-pack")

        val ingredients = space.ingredients
        assertAll(
            { assertEquals(setOf(Category.ROCKET_BUILDING), space.categories) },
            { assertEquals(1.0, ingredients.getValue(satellite)) },
            { assertEquals(1000.0, ingredients.getValue(processingUnit)) },
            { assertEquals(1000.0, ingredients.getValue(lowDensityStructure)) },
            { assertEquals(1000.0, ingredients.getValue(rocketFuel)) },
            { assertEquals(1000.0, space.result.getValue(spaceScience)) },
            { assertEquals(300.0, space.time, TOLERANCE) },
        )
    }

    /**
     * The fallback is Space Age's 50 parts; only a dump that lost the silo prototype needs it, and
     * every real dump carries the silo.
     */
    @Test
    fun aDumpWithoutTheRocketSiloUsesTheSpaceAgeFallback() {
        val space =
            recipes(dump(baseSciencePacks, mapOf("rocket-part" to rocketPart(10))))
                .getValue("space-science-pack")

        assertAll(
            { assertEquals(500.0, space.ingredients.getValue(processingUnit)) },
            { assertEquals(150.0, space.time, TOLERANCE) },
        )
    }

    /**
     * A Space Age launch carries cargo, so the parts one launch needs are its result: the dump's
     * one-part recipe is replaced by the silo's cycle.
     */
    @Test
    fun spaceAgeLaunchIsThePartsOfOneLaunch() {
        val parts =
            recipes(dump(spaceAgeSciencePacks, spaceAgeRecipes, silo(50))).getValue("rocket-part")

        val ingredients = parts.ingredients
        assertAll(
            { assertEquals(50.0, parts.result.getValue(rocketPart)) },
            { assertEquals(50.0, ingredients.getValue(processingUnit)) },
            { assertEquals(50.0, ingredients.getValue(lowDensityStructure)) },
            { assertEquals(50.0, ingredients.getValue(rocketFuel)) },
            { assertEquals(150.0, parts.time, TOLERANCE) },
        )
    }

    /**
     * The silo's own surface conditions gate the launch, so a surface the silo cannot be built on
     * gets none, while the base game's silo has no condition and adds none.
     */
    @Test
    fun theLaunchTakesTheSilosSurfaceConditions() {
        val pressure = """[{"property": "pressure", "min": 1}]"""
        val pressureCondition = listOf(SurfaceCondition(SurfaceProperty.PRESSURE, 1..Int.MAX_VALUE))
        val space =
            recipes(dump(spaceAgeSciencePacks, spaceAgeRecipes, silo(50, pressure)))
                .getValue("rocket-part")
        val base =
            recipes(dump(baseSciencePacks, mapOf("rocket-part" to rocketPart(10)), silo(100)))

        assertAll(
            { assertEquals(pressureCondition, space.surfaceConditions) },
            {
                assertEquals(
                    emptyList(),
                    base.getValue("space-science-pack").surfaceConditions,
                )
            },
        )
    }

    /** A base-game rocket part is not a launch, so the dump's one-part recipe stays untouched. */
    @Test
    fun baseGameKeepsTheDumpedPartRecipe() {
        val all = recipes(dump(baseSciencePacks, mapOf("rocket-part" to rocketPart(10)), silo(100)))

        assertContainsNot(all, "rocket-part")
    }

    /** The part ingredients are the dump's, so a dump without the part recipe gets no launch. */
    @Test
    fun aDumpWithoutThePartRecipeHasNoLaunch() {
        assertAll(
            { assertNull(recipes(dump(baseSciencePacks, silo = silo(100)))["space-science-pack"]) },
            {
                assertNull(
                    recipes(
                        dump(
                            spaceAgeSciencePacks,
                            spaceAgeRecipes - "rocket-part",
                            silo(50),
                        ),
                    )["rocket-part"],
                )
            },
        )
    }

    @Test
    fun spaceAgeHasNoRocketSiloSpaceScienceFallback() {
        assertContainsNot(
            recipes(dump(spaceAgeSciencePacks, spaceAgeRecipes)),
            "space-science-pack",
        )
    }
}
