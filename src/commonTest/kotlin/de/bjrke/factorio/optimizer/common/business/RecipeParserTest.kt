package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup
import de.bjrke.factorio.optimizer.common.model.SurfaceCondition
import de.bjrke.factorio.optimizer.common.model.SurfaceProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeParserTest {

    private fun parse(s: String, mode: Mode = Mode.NORMAL) =
        parseRecipes(
                files = mapOf("test/recipe.lua" to s),
                activeMods = setOf("test"),
                mode = mode,
                defaultRecipesProvider = { listOf() },
            )
            .recipes

    @Test fun emptyTableShouldReturnEmptyRecipeList() = assertTrue(parse("{}").isEmpty())

    @Test
    fun parseCopperPlate() =
        assertEquals(
            listOf(
                Recipe(
                    name = "copper-plate",
                    ingredients = mapOf(Material("copper-ore") to 1.0),
                    result = mapOf(Material("copper-plate") to 1.0),
                    time = 3.2,
                    category = Category.SMELTING,
                    subgroup = Subgroup(),
                )
            ),
            parse(
                """
                {{
                type = "recipe",
                name = "copper-plate",
                category = "smelting",
                energy_required = 3.2,
                ingredients = {{ "copper-ore", 1}},
                result = "copper-plate"
                }}
                """
            ),
        )

    @Test
    fun parseCopperPlateWrappedWithMethodCall() =
        assertEquals(
            listOf(
                Recipe(
                    name = "copper-plate",
                    ingredients = mapOf(Material("copper-ore") to 1.0),
                    result = mapOf(Material("copper-plate") to 1.0),
                    time = 3.2,
                    category = Category.SMELTING,
                    subgroup = Subgroup(),
                )
            ),
            parse(
                """
                data:extend({{
                type = "recipe",
                name = "copper-plate",
                category = "smelting",
                energy_required = 3.2,
                ingredients = {{ "copper-ore", 1}},
                result = "copper-plate"
                }})
                """
            ),
        )

    @Test
    fun parseUraniumProcessing() =
        assertEquals(
            listOf(
                Recipe(
                    name = "uranium-processing",
                    ingredients = mapOf(Material("uranium-ore") to 10.0),
                    result =
                        mapOf(Material("uranium-235") to 0.007, Material("uranium-238") to 0.993),
                    time = 12.0,
                    category = Category.CENTRIFUGING,
                    subgroup = Subgroup("raw-material"),
                )
            ),
            parse(
                """
                {{
                type = "recipe",
                name = "uranium-processing",
                energy_required = 12,
                enabled = false,
                category = "centrifuging",
                ingredients = {{"uranium-ore", 10}},
                icon = "__base__/graphics/icons/uranium-processing.png",
                icon_size = 64, icon_mipmaps = 4,
                subgroup = "raw-material",
                order = "k[uranium-processing]", -- k ordering so it shows up after explosives which is j ordering
                results =
                {
                  {
                    name = "uranium-235",
                    probability = 0.007,
                    amount = 1
                  },
                  {
                    name = "uranium-238",
                    probability = 0.993,
                    amount = 1
                  }
                }
              }}
              """
            ),
        )

    @Test
    fun parseUraniumProcessingNewFormat() =
        assertEquals(
            listOf(
                Recipe(
                    name = "uranium-processing",
                    ingredients = mapOf(Material("uranium-ore") to 10.0),
                    result =
                        mapOf(Material("uranium-235") to 0.007, Material("uranium-238") to 0.993),
                    time = 12.0,
                    category = Category.CENTRIFUGING,
                    subgroup = Subgroup("uranium-processing"),
                )
            ),
            parse(
                """
                {{
                type = "recipe",
                name = "uranium-processing",
                energy_required = 12,
                enabled = false,
                categories = {"centrifuging"},
                ingredients = {{type = "item", name = "uranium-ore", amount = 10}},
                results =
                {
                  {
                    type = "item",
                    name = "uranium-235",
                    shared_probability = { min = 0, max = 0.007 },
                    amount = 1
                  },
                  {
                    type = "item",
                    name = "uranium-238",
                    shared_probability = { min = 0.007, max = 1 },
                    amount = 1
                  }
                },
                subgroup = "uranium-processing"
                }}
                """
            ),
        )

    @Test
    fun parseParameterRecipeIsSkipped() =
        assertTrue(
            parse(
                    """
                    {
                    {type = "recipe", name = "parameter-0", parameter = true, categories = {"parameters"}}
                    }
                    """
                )
                .isEmpty()
        )

    @Test
    fun parseIronGearWheelNormal() =
        assertEquals(
            listOf(
                Recipe(
                    name = "iron-gear-wheel",
                    ingredients = mapOf(Material("iron-plate") to 2.0),
                    result = mapOf(Material("iron-gear-wheel") to 1.0),
                    time = 0.5,
                    category = Category.CRAFTING,
                    subgroup = Subgroup(),
                )
            ),
            parse(
                """
                {{
                type = "recipe",
                name = "iron-gear-wheel",
                normal =
                {
                  ingredients = {{"iron-plate", 2}},
                  result = "iron-gear-wheel"
                },
                expensive =
                {
                  ingredients = {{"iron-plate", 4}},
                  result = "iron-gear-wheel"
                }
                }}
                """
            ),
        )

    @Test
    fun parseIronGearWheelExpensive() =
        assertEquals(
            listOf(
                Recipe(
                    name = "iron-gear-wheel",
                    ingredients = mapOf(Material("iron-plate") to 4.0),
                    result = mapOf(Material("iron-gear-wheel") to 1.0),
                    time = 0.5,
                    category = Category.CRAFTING,
                    subgroup = Subgroup(),
                )
            ),
            parse(
                """
                {{
                type = "recipe",
                name = "iron-gear-wheel",
                normal =
                {
                  ingredients = {{"iron-plate", 2}},
                  result = "iron-gear-wheel"
                },
                expensive =
                {
                  ingredients = {{"iron-plate", 4}},
                  result = "iron-gear-wheel"
                }
                }}
                """,
                mode = Mode.EXPENSINVE,
            ),
        )

    @Test
    fun parseSurfaceConditions() =
        assertEquals(
            listOf(
                Recipe(
                    name = "copper-bacteria",
                    ingredients = mapOf(Material("copper-ore") to 1.0),
                    result = mapOf(Material("copper-bacteria") to 1.0),
                    time = 0.5,
                    category = Category.ORGANIC,
                    subgroup = Subgroup(),
                    surfaceConditions =
                        listOf(
                            SurfaceCondition(
                                property = SurfaceProperty.PRESSURE,
                                range = 2000..2000,
                            ),
                            SurfaceCondition(property = SurfaceProperty.GRAVITY),
                        ),
                )
            ),
            parse(
                """
                {{
                type = "recipe",
                name = "copper-bacteria",
                categories = {"organic", "crafting"},
                ingredients = {{ "copper-ore", 1}},
                result = "copper-bacteria",
                surface_conditions =
                {
                  { property = "pressure", min = 2000, max = 2000 },
                  { property = "gravity" },
                  { property = "unknown", min = 1 }
                }
                }}
                """
            ),
        )

    @Test fun parseAllRecipes() = assertTrue(parseRecipes().recipes.isNotEmpty())
}
