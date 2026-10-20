package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimizerTest {

    private val ironOre = Material("iron-ore")
    private val water = Material("water")
    private val ice = Material("ice")

    private fun recipe(
        name: String,
        category: Category,
        result: Map<Material, Double>,
        ingredients: Map<Material, Double> = emptyMap(),
        time: Double = 1.0,
    ) =
        Recipe(
            name = name,
            ingredients = ingredients,
            result = result,
            time = time,
            categories = setOf(category),
        )

    private val miningIron = recipe("iron-ore", Category.MINING_BASIC_SOLID, mapOf(ironOre to 1.0))
    private val pumpWater = recipe("water", Category.OFFSHORE_PUMPING, mapOf(water to 1.0))
    private val meltIce =
        recipe(
            "ice-melting",
            Category.CHEMISTRY,
            mapOf(water to 20.0),
            ingredients = mapOf(ice to 1.0),
        )

    private fun factoryOf(target: Material, recipes: List<Recipe>, factories: Set<Factory>) =
        Optimizer(ParsedRecipe(emptyList(), recipes), factories)[target].production(target).factory

    @Test
    fun miningDrillIsUsedForResource() =
        assertEquals(
            Factory.ELECTRIC_MINING_DRILL,
            factoryOf(
                ironOre,
                listOf(miningIron),
                setOf(
                    Factory.ELECTRIC_MINING_DRILL,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )

    @Test
    fun freeSourceWhenNoDrillSelected() =
        assertEquals(
            Factory.NO_FACTORY,
            factoryOf(
                ironOre,
                listOf(miningIron),
                setOf(Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )

    @Test
    fun extractionIsPreferredOverProcessing() =
        assertEquals(
            Factory.OFFSHORE_PUMP,
            factoryOf(
                water,
                // sorted by name like RecipeProvider does: ice-melting before water
                listOf(meltIce, pumpWater),
                setOf(
                    Factory.OFFSHORE_PUMP,
                    Factory.CHEMICAL_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )

    @Test
    fun processingIsUsedWhenNoExtractionMachine() =
        assertEquals(
            Factory.CHEMICAL_PLANT,
            factoryOf(
                water,
                listOf(meltIce, pumpWater),
                setOf(Factory.CHEMICAL_PLANT, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )

    private val moltenIron = Material("molten-iron")
    private val steelPlate = Material("steel-plate")
    private val lava = Material("lava")
    private val calcite = Material("calcite")

    private val pumpLava = recipe("lava", Category.OFFSHORE_PUMPING, mapOf(lava to 1.0))
    private val mineCalcite = recipe("calcite", Category.MINING_BASIC_SOLID, mapOf(calcite to 1.0))
    private val fromLava =
        recipe(
            "molten-iron-from-lava",
            Category.METALLURGY,
            mapOf(moltenIron to 1.0),
            ingredients = mapOf(lava to 500.0, calcite to 1.0),
        )
    private val fromOre =
        recipe(
            "iron-ore-melting",
            Category.METALLURGY,
            mapOf(moltenIron to 1.0),
            ingredients = mapOf(ironOre to 50.0, calcite to 1.0),
        )
    private val castingSteel =
        recipe(
            "casting-steel",
            Category.METALLURGY,
            mapOf(steelPlate to 1.0),
            ingredients = mapOf(moltenIron to 100.0),
        )

    private val vulcanusFactories =
        setOf(
            Factory.FOUNDRY,
            Factory.OFFSHORE_PUMP,
            Factory.ELECTRIC_MINING_DRILL,
            Factory.NO_FACTORY,
            Factory.SCIENCE_LAB,
        )

    @Test
    fun avoidsIngredientsFromFreeSource() {
        // iron-ore has no producer here (not on Vulcanus) so it would come from a chest;
        // lava & calcite are extractable, so the lava recipe should win
        val result =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(fromOre, fromLava, pumpLava, mineCalcite)),
                vulcanusFactories,
            )[moltenIron]

        assertEquals("molten-iron-from-lava", result.recipe.name)
    }

    @Test
    fun vulcanusSteelUsesLavaNotChestOre() {
        val result =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(castingSteel, fromOre, fromLava, pumpLava, mineCalcite),
                ),
                vulcanusFactories,
            )[steelPlate]

        assertAll(
            { assertEquals("casting-steel", result.recipe.name) },
            { assertTrue(result.factories.values.none { it.factory == Factory.NO_FACTORY }) },
            {
                assertTrue(
                    result.factories.values.any { it.recipe.name == "molten-iron-from-lava" },
                )
            },
        )
    }

    @Test
    fun oreBasedRecipeWinsWhenOreIsProducible() {
        // on Nauvis-like surface: iron-ore is mined, lava is not available (free source)
        val result =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(fromOre, fromLava, mineCalcite, miningIron)),
                vulcanusFactories,
            )[moltenIron]

        assertEquals("iron-ore-melting", result.recipe.name)
    }

    @Test
    fun byproductRecipeRunsOnceForAllItsOutputs() {
        val a = Material("synthetic-a")
        val b = Material("synthetic-b")
        val product = Material("synthetic-product")
        val byproduct = recipe("byproduct", Category.CRAFTING, mapOf(a to 2.0, b to 1.0))
        val consumer =
            recipe(
                "consumer",
                Category.CRAFTING,
                mapOf(product to 1.0),
                ingredients = mapOf(a to 2.0, b to 1.0),
            )
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(byproduct, consumer)),
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            )

        val result = optimizer[product]

        val byproducts = result.factories.values.filter { it.recipe.name == "byproduct" }
        assertAll(
            { assertEquals(1, byproducts.size) },
            // one machine covers both outputs: max, not sum
            { assertEquals(0.8, byproducts.single().amount, 1e-9) },
        )
    }

    @Test
    fun sharedIntermediateMachinesAreSummed() {
        val ore = Material("synthetic-ore")
        val plate = Material("synthetic-plate")
        val x = Material("synthetic-x")
        val y = Material("synthetic-y")
        val product = Material("synthetic-shared-product")
        val mine = recipe("mine", Category.MINING_BASIC_SOLID, mapOf(ore to 1.0))
        val smelt =
            recipe("smelt", Category.SMELTING, mapOf(plate to 1.0), ingredients = mapOf(ore to 2.0))
        val makeX =
            recipe("make-x", Category.CRAFTING, mapOf(x to 1.0), ingredients = mapOf(plate to 2.0))
        val makeY =
            recipe("make-y", Category.CRAFTING, mapOf(y to 1.0), ingredients = mapOf(plate to 3.0))
        val assemble =
            recipe(
                "assemble",
                Category.CRAFTING,
                mapOf(product to 1.0),
                ingredients = mapOf(x to 1.0, y to 1.0),
            )
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(mine, smelt, makeX, makeY, assemble)),
                setOf(
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.ELECTRIC_FURNACE,
                    Factory.ELECTRIC_MINING_DRILL,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val result = optimizer[product]

        val smelting = result.factories.values.single { it.recipe.name == "smelt" }
        // demand(plate) = 2 * make-x + 3 * make-y, not the max of the two consumers
        assertEquals(2.5, smelting.amount, 1e-9)
    }

    private fun multiCategoryRecipe(
        name: String,
        categories: Set<Category>,
        result: Material,
    ) =
        Recipe(
            name = name,
            ingredients = emptyMap(),
            result = mapOf(result to 1.0),
            time = 1.0,
            categories = categories,
        )

    @Test
    fun biochamberIsPreferredOverAssembler() {
        val product = Material("synthetic-organic-product")
        assertEquals(
            Factory.BIOCHAMBER,
            factoryOf(
                product,
                listOf(
                    multiCategoryRecipe(
                        "organic-recipe",
                        setOf(
                            Category.ORGANIC,
                            Category.CRAFTING,
                        ),
                        product,
                    ),
                ),
                setOf(
                    Factory.BIOCHAMBER,
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun cryogenicPlantIsPreferredOverChemicalPlant() {
        val product = Material("synthetic-cold-product")
        assertEquals(
            Factory.CRYOGENIC_PLANT,
            factoryOf(
                product,
                listOf(
                    multiCategoryRecipe(
                        "cold-recipe",
                        setOf(
                            Category.CHEMISTRY,
                            Category.CRYOGENICS,
                        ),
                        product,
                    ),
                ),
                setOf(
                    Factory.CRYOGENIC_PLANT,
                    Factory.CHEMICAL_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun electromagneticPlantIsNotUsedForCrafting() {
        // the electromagnetic plant only runs electromagnetics recipes, not plain crafting ones
        val product = Material("synthetic-circuit")
        assertEquals(
            Factory.ASSEMBLING_MACHINE_3,
            factoryOf(
                product,
                listOf(recipe("circuit", Category.CRAFTING, mapOf(product to 1.0))),
                setOf(
                    Factory.ELECTROMAGNETIC_PLANT,
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun cryogenicPlantIsNotUsedForChemistry() {
        // the cryogenic plant only runs cryogenics recipes, not plain chemistry ones (e.g. plastic)
        val product = Material("synthetic-plastic")
        assertEquals(
            Factory.CHEMICAL_PLANT,
            factoryOf(
                product,
                listOf(recipe("plastic", Category.CHEMISTRY, mapOf(product to 1.0))),
                setOf(
                    Factory.CRYOGENIC_PLANT,
                    Factory.CHEMICAL_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun assemblingMachine1IsNotUsedForFluidRecipes() {
        // AM1 lacks the crafting-with-fluid category, so a fluid recipe cannot run on it and its
        // product is imported instead. Fluid handling is AM1's only restriction; ingredient slots
        // are unlimited on every assembling machine since Factorio 0.17.
        val concrete = Material("synthetic-concrete")
        val water = Material("synthetic-water")
        val fluidRecipe =
            recipe(
                "concrete",
                Category.CRAFTING_WITH_FLUID,
                mapOf(concrete to 1.0),
                ingredients = mapOf(water to 5.0),
            )
        val pumpWater = recipe("water", Category.OFFSHORE_PUMPING, mapOf(water to 1.0))

        val result =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(fluidRecipe, pumpWater)),
                setOf(
                    Factory.ASSEMBLING_MACHINE_1,
                    Factory.OFFSHORE_PUMP,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )[concrete]

        assertAll(
            { assertEquals(Factory.NO_FACTORY, result.production(concrete).factory) },
            {
                assertTrue(
                    result.factories.values.none { it.factory == Factory.ASSEMBLING_MACHINE_1 },
                )
            },
        )
    }

    @Test
    fun assemblingMachine1HasNoIngredientLimit() {
        // Regression: the pre-0.17 caps (AM1 = 2, AM2 = 4 ingredients) were removed, so all
        // assemblers accept any number of ingredients; a six-ingredient crafting recipe runs on
        // AM1.
        val product = Material("synthetic-composite")
        val ingredients = (1..6).associate { Material("synthetic-part-$it") to 1.0 }
        val manyIngredients =
            recipe("composite", Category.CRAFTING, mapOf(product to 1.0), ingredients = ingredients)

        assertEquals(
            Factory.ASSEMBLING_MACHINE_1,
            factoryOf(
                product,
                listOf(manyIngredients),
                setOf(Factory.ASSEMBLING_MACHINE_1, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )
    }

    @Test
    fun builtInProductivityBoostsOutputWithoutExtraInput() {
        val ore = Material("synthetic-em-ore")
        val plate = Material("synthetic-em-plate")
        val emRecipe =
            Recipe(
                name = "em-recipe",
                ingredients = mapOf(ore to 1.0),
                result = mapOf(plate to 1.0),
                time = 1.0,
                categories = setOf(Category.ELECTROMAGNETICS),
            )
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(emRecipe)),
                setOf(
                    Factory.ELECTROMAGNETIC_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val production = optimizer[plate].production(plate)
        assertAll(
            { assertEquals(Factory.ELECTROMAGNETIC_PLANT, production.factory) },
            // one machine produces 2 * 1.5 = 3/s, so 1/s needs 1/3 of a machine
            { assertEquals(1.0 / 3.0, production.amount, 1e-9) },
            { assertEquals(1.0, production.production(plate), 1e-9) },
            // the bonus does not change the input consumption
            { assertEquals(2.0 / 3.0, production.consumption(ore), 1e-9) },
        )
    }
}
