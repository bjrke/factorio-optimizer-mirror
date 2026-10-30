package de.bjrke.factorio.optimizer.common.service

import de.bjrke.factorio.optimizer.common.fixtures.factoryOf
import de.bjrke.factorio.optimizer.common.fixtures.ice
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.testRecipe
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.ParsedRecipe
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimizerTest {

    private val miningIron =
        testRecipe("iron-ore", Category.MINING_BASIC_SOLID, mapOf(ironOre to 1.0))
    private val pumpWater = testRecipe("water", Category.OFFSHORE_PUMPING, mapOf(water to 1.0))
    private val meltIce =
        testRecipe(
            "ice-melting",
            Category.CHEMISTRY,
            mapOf(water to 20.0),
            ingredients = mapOf(ice to 1.0),
        )

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
                // sorted by name like RecipeLoader does: ice-melting before water
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

    private val pumpLava = testRecipe("lava", Category.OFFSHORE_PUMPING, mapOf(lava to 1.0))
    private val mineCalcite =
        testRecipe("calcite", Category.MINING_BASIC_SOLID, mapOf(calcite to 1.0))
    private val fromLava =
        testRecipe(
            "molten-iron-from-lava",
            Category.METALLURGY,
            mapOf(moltenIron to 1.0),
            ingredients = mapOf(lava to 500.0, calcite to 1.0),
        )
    private val fromOre =
        testRecipe(
            "iron-ore-melting",
            Category.METALLURGY,
            mapOf(moltenIron to 1.0),
            ingredients = mapOf(ironOre to 50.0, calcite to 1.0),
        )
    private val castingSteel =
        testRecipe(
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
        val byproduct = testRecipe("byproduct", Category.CRAFTING, mapOf(a to 2.0, b to 1.0))
        val consumer =
            testRecipe(
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
            { assertEquals(0.8, byproducts.single().amount, TOLERANCE) },
        )
    }

    @Test
    fun sharedIntermediateMachinesAreSummed() {
        val ore = Material("synthetic-ore")
        val plate = Material("synthetic-plate")
        val x = Material("synthetic-x")
        val y = Material("synthetic-y")
        val product = Material("synthetic-shared-product")
        val mine = testRecipe("mine", Category.MINING_BASIC_SOLID, mapOf(ore to 1.0))
        val smelt =
            testRecipe(
                "smelt",
                Category.SMELTING,
                mapOf(plate to 1.0),
                ingredients = mapOf(ore to 2.0),
            )
        val makeX =
            testRecipe(
                "make-x",
                Category.CRAFTING,
                mapOf(x to 1.0),
                ingredients = mapOf(plate to 2.0),
            )
        val makeY =
            testRecipe(
                "make-y",
                Category.CRAFTING,
                mapOf(y to 1.0),
                ingredients = mapOf(plate to 3.0),
            )
        val assemble =
            testRecipe(
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
        assertEquals(2.5, smelting.amount, TOLERANCE)
    }
}
