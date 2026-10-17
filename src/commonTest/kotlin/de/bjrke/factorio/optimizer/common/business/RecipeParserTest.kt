package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTableOf
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

    /**
     * Items that only exist inside hidden or cheat recipes and must never appear in the material
     * list. Base game items first, then the Space Age cheats; the absent ones are simply a no-op
     * for the other dump.
     */
    private val phantomMaterials =
        listOf(
            // the loaders and the starting pistol only have hidden recipes
            "loader",
            "fast-loader",
            "express-loader",
            "turbo-loader",
            "pistol",
            // creative/debug entities, tools and placeholders from hidden cheat recipes
            "blueprint",
            "blueprint-book",
            "bottomless-chest",
            "burner-generator",
            "coin",
            "deconstruction-planner",
            "electric-energy-interface",
            "electric-energy-interface-equipment",
            "empty-module-slot",
            "heat-interface",
            "infinity-cargo-wagon",
            "infinity-chest",
            "infinity-pipe",
            "item-unknown",
            "lane-splitter",
            "linked-belt",
            "linked-chest",
            "one-way-valve",
            "overflow-valve",
            "proxy-container",
            "science",
            "selection-tool",
            "simple-entity-with-force",
            "simple-entity-with-owner",
            "space-platform-hub",
            "top-up-valve",
            "upgrade-planner",
        )

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
    fun materialsExcludeItemsFromDroppedHiddenRecipes() {
        val parsed =
            parsePrototypes(
                """
                {"recipe":{
                  "loader":{
                    "type":"recipe","name":"loader","hidden":true,
                    "ingredients":[{"type":"item","name":"iron-plate","amount":5}],
                    "results":[{"type":"item","name":"loader","amount":1}]},
                  "iron-gear-wheel":{
                    "type":"recipe","name":"iron-gear-wheel",
                    "ingredients":[{"type":"item","name":"iron-plate","amount":2}],
                    "results":[{"type":"item","name":"iron-gear-wheel","amount":1}]}
                }}
                """,
            )

        val names = parsed.materials.map(Material::name)
        assertAll(
            // the hidden loader recipe is dropped, so no loader-ish item may be offered
            {
                assertEquals(
                    emptyList(),
                    names.filter { it.contains("loader") },
                    "hidden loader recipe leaked a material",
                )
            },
            // only produced materials are offered, and the gear wheel is produced
            { assertContains(names, "iron-gear-wheel", "produced material is missing") },
            // iron-plate is only an ingredient; no recipe yields it, so it has no factory to run
            {
                assertEquals(
                    emptyList(),
                    names.filter { it == "iron-plate" },
                    "ingredient-only material leaked",
                )
            },
        )
    }

    /**
     * Items that exist only inside hidden or cheat recipes the parser drops (loaders, planners,
     * creative entities, ...) must never be offered: they have no surface or factory to produce
     * them. This is a curated list, so a wrong pseudo recipe adds a failure instead of hiding.
     */
    @Test
    fun realPrototypeDumpsDoNotListPhantomMaterials() =
        assertTableOf(
            "base" to RecipeProvider.forActiveMods(false),
            "space-age" to RecipeProvider.forActiveMods(),
        ) { (label, parsed) ->
            assertEquals(
                emptyList(),
                parsed.materials.map(Material::name).filter { it in phantomMaterials },
                "$label lists phantom materials",
            )
        }

    /** The agricultural tower recipe puts wood back as a producible Space Age material. */
    @Test
    fun spaceAgePrototypeDumpKeepsHarvestableWood() =
        assertContains(
            RecipeProvider.forActiveMods().materials.map(Material::name),
            "wood",
            "wood must be producible by the agricultural tower",
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
                )
                .machineStats

        assertTableOf(
            "assembling-machine-3" to
                MachineStats(
                    setOf(
                        Category.CRAFTING,
                        Category.ADVANCED_CRAFTING,
                        Category.CRAFTING_WITH_FLUID,
                    ),
                    1.25,
                    0.0,
                ),
            "foundry" to MachineStats(setOf(Category.METALLURGY), 4.0, 0.5),
            "big-mining-drill" to
                MachineStats(
                    setOf(Category.MINING_BASIC_SOLID, Category.MINING_HARD_SOLID),
                    2.5,
                    0.0,
                ),
        ) { (name, expected) ->
            assertEquals(expected, stats.getValue(name))
        }
    }

    @Test
    fun parseLogisticsStats() {
        val logistics =
            parsePrototypes(
                    """
                    {
                      "transport-belt": {
                        "transport-belt": {"speed": 0.03125},
                        "turbo-transport-belt": {"speed": 0.125}
                      },
                      "inserter": {
                        "inserter": {"rotation_speed": 0.014, "extension_speed": 0.035},
                        "stack-inserter": {"rotation_speed": 0.04, "bulk": true, "max_belt_stack_size": 4, "stack_size_bonus": 4}
                      },
                      "technology": {
                        "inserter-capacity-bonus-1": {"effects": [{"type": "bulk-inserter-capacity-bonus", "modifier": 1}]},
                        "inserter-capacity-bonus-2": {"effects": [{"type": "inserter-stack-size-bonus", "modifier": 1}, {"type": "bulk-inserter-capacity-bonus", "modifier": 1}]},
                        "transport-belt-capacity-2": {"effects": [{"type": "belt-stack-size-bonus", "modifier": 1}, {"type": "inserter-stack-size-bonus", "modifier": 1}]}
                      },
                      "pump": {"pump": {"pumping_speed": 20}},
                      "offshore-pump": {"offshore-pump": {"pumping_speed": 20}},
                      "fluid": {"water": {}, "crude-oil": {}}
                    }
                    """,
                )
                .logistics

        assertAll(
            { assertEquals(0.03125, logistics.belts["transport-belt"]) },
            { assertEquals(0.125, logistics.belts["turbo-transport-belt"]) },
            {
                // a plain inserter starts at one item and gains the generic stack size bonus
                assertEquals(
                    InserterStats(
                        0.014,
                        bulk = false,
                        minStack = 1,
                        maxStack = 2,
                        stacksOnBelt = false,
                    ),
                    logistics.inserters["inserter"],
                )
            },
            {
                // the stack inserter starts at 2 + stack_size_bonus and gains the bulk capacity
                // bonus; only it carries max_belt_stack_size
                assertEquals(
                    InserterStats(
                        0.04,
                        bulk = true,
                        minStack = 6,
                        maxStack = 8,
                        stacksOnBelt = true,
                    ),
                    logistics.inserters["stack-inserter"],
                )
            },
            // pumping_speed is fluid per tick, so the rate is 60 times as much
            { assertEquals(1200.0, logistics.pumpRate) },
            {
                assertEquals(
                    setOf(Material("water"), Material("crude-oil")),
                    logistics.fluids,
                )
            },
        )
    }
}
