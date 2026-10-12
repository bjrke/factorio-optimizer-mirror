package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.MachineStats
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup
import de.bjrke.factorio.optimizer.common.model.SurfaceCondition
import de.bjrke.factorio.optimizer.common.model.SurfaceProperty
import de.bjrke.factorio.optimizer.common.model.extractionCategories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeParserTest {

    private fun parse(s: String) =
        parsePrototypes(s, defaultRecipesProvider = { listOf() })
            .recipes
            // resource, harvesting, asteroid and spoilage recipes are derived separately
            .filter { it.category !in extractionCategories && it.category != Category.ROTTING }

    @Test fun emptyDumpShouldReturnEmptyRecipeList() = assertTrue(parse("{}").isEmpty())

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
                {"recipe":{"copper-plate":{
                  "type":"recipe",
                  "name":"copper-plate",
                  "categories":["smelting"],
                  "energy_required":3.2,
                  "ingredients":[{"type":"item","name":"copper-ore","amount":1}],
                  "results":[{"type":"item","name":"copper-plate","amount":1}]
                }}}
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
                    subgroup = Subgroup("uranium-processing"),
                )
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
                """
            ),
        )

    @Test
    fun parseParameterRecipeIsSkipped() =
        assertTrue(
            parse(
                    """
                    {"recipe":{"parameter-0":{
                      "type":"recipe","name":"parameter-0","parameter":true,"categories":["parameters"]
                    }}}
                    """
                )
                .isEmpty()
        )

    @Test
    fun parseRecipeWithoutCategoriesDefaultsToCrafting() =
        assertEquals(
            Category.CRAFTING,
            parse(
                    """
                    {"recipe":{"iron-gear-wheel":{
                      "type":"recipe","name":"iron-gear-wheel",
                      "ingredients":[{"type":"item","name":"iron-plate","amount":2}],
                      "results":[{"type":"item","name":"iron-gear-wheel","amount":1}]
                    }}}
                    """
                )
                .single()
                .category,
        )

    @Test
    fun parseSurfaceConditionsAndIndependentProbability() =
        assertEquals(
            listOf(
                Recipe(
                    name = "copper-bacteria",
                    ingredients = mapOf(Material("yumako-mash") to 3.0),
                    result = mapOf(Material("copper-bacteria") to 0.1, Material("spoilage") to 1.0),
                    time = 1.0,
                    category = Category.ORGANIC,
                    additionalCategories = setOf(Category.CRAFTING),
                    subgroup = Subgroup("agriculture-processes"),
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
                """
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
                    """
                )
                .single()

        assertEquals(Category.CHEMISTRY, recipe.category)
        assertEquals(setOf(Category.CHEMISTRY, Category.CRYOGENICS), recipe.categories)
    }

    @Test
    fun parseMachineStats() {
        val stats =
            parsePrototypes(
                    """
                    {
                      "assembling-machine": {
                        "assembling-machine-3": {"crafting_categories": ["crafting", "advanced-crafting", "crafting-with-fluid"], "crafting_speed": 1.25},
                        "foundry": {"crafting_categories": ["metallurgy"], "crafting_speed": 4, "effect_receiver": {"base_effect": {"productivity": 0.5}}}
                      },
                      "mining-drill": {
                        "big-mining-drill": {"resource_categories": ["basic-solid", "hard-solid"], "mining_speed": 2.5}
                      }
                    }
                    """,
                    defaultRecipesProvider = { listOf() },
                )
                .machineStats

        assertEquals(
            MachineStats(
                setOf(Category.CRAFTING, Category.ADVANCED_CRAFTING, Category.CRAFTING_WITH_FLUID),
                1.25,
                0.0,
            ),
            stats.getValue("assembling-machine-3"),
        )
        assertEquals(
            MachineStats(setOf(Category.METALLURGY), 4.0, 0.5),
            stats.getValue("foundry"),
        )
        assertEquals(
            MachineStats(setOf(Category.MINING_BASIC_SOLID, Category.MINING_HARD_SOLID), 2.5, 0.0),
            stats.getValue("big-mining-drill"),
        )
    }
}
