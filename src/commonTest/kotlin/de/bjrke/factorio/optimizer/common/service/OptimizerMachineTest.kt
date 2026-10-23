package de.bjrke.factorio.optimizer.common.service

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.ore
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plate
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.water
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.fixtures.factoryOf
import de.bjrke.factorio.optimizer.common.service.fixtures.production
import de.bjrke.factorio.optimizer.common.service.fixtures.testRecipe
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimizerMachineTest {

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
                listOf(testRecipe("circuit", Category.CRAFTING, mapOf(product to 1.0))),
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
                listOf(testRecipe("plastic", Category.CHEMISTRY, mapOf(product to 1.0))),
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
            testRecipe(
                "concrete",
                Category.CRAFTING_WITH_FLUID,
                mapOf(concrete to 1.0),
                ingredients = mapOf(water to 5.0),
            )
        val pumpWater = testRecipe("water", Category.OFFSHORE_PUMPING, mapOf(water to 1.0))

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
            testRecipe(
                "composite",
                Category.CRAFTING,
                mapOf(product to 1.0),
                ingredients = ingredients,
            )

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
            { assertEquals(1.0 / 3.0, production.amount, TOLERANCE) },
            { assertEquals(1.0, production.production(plate), TOLERANCE) },
            // the bonus does not change the input consumption
            { assertEquals(2.0 / 3.0, production.consumption(ore), TOLERANCE) },
        )
    }
}
