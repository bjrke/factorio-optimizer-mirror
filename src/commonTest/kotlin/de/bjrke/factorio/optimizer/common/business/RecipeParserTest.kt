package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.data.parsePrototypes
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup
import de.bjrke.factorio.optimizer.common.model.SurfaceCondition
import de.bjrke.factorio.optimizer.common.model.SurfaceProperty
import de.bjrke.factorio.optimizer.common.model.extractionCategories
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RecipeParserTest {

    private fun parse(s: String) =
        parsePrototypes(s)
            .recipes
            // resource, harvesting, asteroid and spoilage recipes are derived separately
            .filter {
                it.categories.none(extractionCategories::contains) &&
                    Category.SPOILING !in it.categories
            }

    @Test fun emptyDumpShouldReturnEmptyRecipeList() = assertEquals(emptyList(), parse("{}"))

    @Test
    fun parseCopperPlate() =
        assertEquals(
            listOf(
                Recipe(
                    name = "copper-plate",
                    ingredients = mapOf(Material("copper-ore") to 1.0),
                    result = mapOf(Material("copper-plate") to 1.0),
                    time = 3.2,
                    categories = setOf(Category.SMELTING),
                    subgroup = Subgroup(),
                ),
            ),
            parse(
                """
                {"recipe":{"copper-plate":{
                  "type":"recipe",
                  "name":"copper-plate",
                  "categories":["smelting"],
                  "energy_required":3.2,
                  "ingredients":[{"type":"item","name":"copper-ore","amount":1}],
                  "results":[{"type":"item","name":"copper-plate","amount":1}]
                }}}
                """,
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
                    categories = setOf(Category.CENTRIFUGING),
                    subgroup = Subgroup("uranium-processing"),
                ),
            ),
            parse(
                """
                {"recipe":{"uranium-processing":{
                  "type":"recipe",
                  "name":"uranium-processing",
                  "energy_required":12,
                  "categories":["centrifuging"],
                  "ingredients":[{"type":"item","name":"uranium-ore","amount":10}],
                  "subgroup":"uranium-processing",
                  "results":[
                    {"type":"item","name":"uranium-235","shared_probability":{"min":0,"max":0.007},"amount":1},
                    {"type":"item","name":"uranium-238","shared_probability":{"min":0.007,"max":1},"amount":1}
                  ]
                }}}
                """,
            ),
        )

    @Test
    fun parseParameterRecipeIsSkipped() =
        assertEquals(
            emptyList(),
            parse(
                """
                {"recipe":{"parameter-0":{
                  "type":"recipe","name":"parameter-0","parameter":true,"categories":["parameters"]
                }}}
                """,
            ),
        )

    @Test
    fun parseRecipeWithoutCategoriesDefaultsToCrafting() =
        assertEquals(
            setOf(Category.CRAFTING),
            parse(
                    """
                    {"recipe":{"iron-gear-wheel":{
                      "type":"recipe","name":"iron-gear-wheel",
                      "ingredients":[{"type":"item","name":"iron-plate","amount":2}],
                      "results":[{"type":"item","name":"iron-gear-wheel","amount":1}]
                    }}}
                    """,
                )
                .single()
                .categories,
        )

    @Test
    fun parseHiddenRecyclingRecipe() {
        val recipes =
            parse(
                    """
                    {"recipe":{
                      "scrap-recycling":{
                        "type":"recipe","name":"scrap-recycling","categories":["recycling"],
                        "ingredients":[{"type":"item","name":"scrap","amount":1}],
                        "results":[{"type":"item","name":"iron-gear-wheel","amount":1}]
                      },
                      "iron-gear-wheel-recycling":{
                        "type":"recipe","name":"iron-gear-wheel-recycling","hidden":true,
                        "categories":["recycling"],
                        "ingredients":[{"type":"item","name":"iron-gear-wheel","amount":1}],
                        "results":[
                          {"type":"item","name":"iron-plate","amount":0,"extra_count_fraction":0.5}
                        ]
                      }
                    }}
                    """,
                )
                .associateBy(Recipe::name)

        assertAll(
            { assertContains(recipes, "iron-gear-wheel-recycling") },
            // amount (0) + extra_count_fraction (0.5) is the expected yield
            {
                assertEquals(
                    mapOf(Material("iron-plate") to 0.5),
                    recipes.getValue("iron-gear-wheel-recycling").result,
                )
            },
        )
    }

    @Test
    fun parseSkipsSelfRecyclingRecipe() {
        val recipes =
            parse(
                """
                {"recipe":{
                  "scrap-recycling":{
                    "type":"recipe","name":"scrap-recycling","categories":["recycling"],
                    "ingredients":[{"type":"item","name":"scrap","amount":1}],
                    "results":[{"type":"item","name":"iron-gear-wheel","amount":1}]
                  },
                  "iron-ore-recycling":{
                    "type":"recipe","name":"iron-ore-recycling","hidden":true,
                    "categories":["recycling"],
                    "ingredients":[{"type":"item","name":"iron-ore","amount":1}],
                    "results":[
                      {"type":"item","name":"iron-ore","amount":1,"independent_probability":0.25}
                    ]
                  }
                }}
                """,
            )

        assertAll(
            { assertContains(recipes.map(Recipe::name), "scrap-recycling") },
            { assertContainsNot(recipes.map(Recipe::name), "iron-ore-recycling") },
        )
    }

    @Test
    fun parseSkipsRecyclingNotReachableFromScrap() {
        val recipes =
            parse(
                """
                {"recipe":{
                  "scrap-recycling":{
                    "type":"recipe","name":"scrap-recycling","categories":["recycling"],
                    "ingredients":[{"type":"item","name":"scrap","amount":1}],
                    "results":[{"type":"item","name":"iron-gear-wheel","amount":1}]
                  },
                  "accumulator-recycling":{
                    "type":"recipe","name":"accumulator-recycling","hidden":true,
                    "categories":["recycling"],
                    "ingredients":[{"type":"item","name":"accumulator","amount":1}],
                    "results":[
                      {"type":"item","name":"battery","amount":0,"extra_count_fraction":0.2}
                    ]
                  }
                }}
                """,
            )

        assertAll(
            { assertContains(recipes.map(Recipe::name), "scrap-recycling") },
            { assertContainsNot(recipes.map(Recipe::name), "accumulator-recycling") },
        )
    }

    @Test
    fun parseSurfaceConditionsAndIndependentProbability() =
        assertEquals(
            listOf(
                Recipe(
                    name = "copper-bacteria",
                    ingredients = mapOf(Material("yumako-mash") to 3.0),
                    result = mapOf(Material("copper-bacteria") to 0.1, Material("spoilage") to 1.0),
                    time = 1.0,
                    categories = setOf(Category.CRAFTING, Category.ORGANIC),
                    subgroup = Subgroup("agriculture-processes"),
                    surfaceConditions =
                        listOf(
                            SurfaceCondition(
                                property = SurfaceProperty.PRESSURE,
                                range = 2000..2000,
                            ),
                            SurfaceCondition(property = SurfaceProperty.GRAVITY),
                        ),
                ),
            ),
            parse(
                """
                {"recipe":{"copper-bacteria":{
                  "type":"recipe",
                  "name":"copper-bacteria",
                  "categories":["organic","crafting"],
                  "energy_required":1,
                  "ingredients":[{"type":"item","name":"yumako-mash","amount":3}],
                  "results":[
                    {"type":"item","name":"copper-bacteria","amount":1,"independent_probability":0.1},
                    {"type":"item","name":"spoilage","amount":1}
                  ],
                  "subgroup":"agriculture-processes",
                  "surface_conditions":[
                    {"property":"pressure","min":2000,"max":2000},
                    {"property":"gravity"},
                    {"property":"unknown","min":1}
                  ]
                }}}
                """,
            ),
        )

    @Test
    fun parseMultipleCategories() {
        val recipe =
            parse(
                    """
                    {"recipe":{"carbon":{
                      "type":"recipe",
                      "name":"carbon",
                      "categories":["chemistry","cryogenics"],
                      "ingredients":[{"type":"item","name":"coal","amount":2}],
                      "results":[{"type":"item","name":"carbon","amount":1}]
                    }}}
                    """,
                )
                .single()

        assertEquals(setOf(Category.CHEMISTRY, Category.CRYOGENICS), recipe.categories)
    }
}
