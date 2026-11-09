package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.fixtures.calciteMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.castingSteelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.factoryOf
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.iceMeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lavaPumpingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.moltenIron
import de.bjrke.factorio.optimizer.common.fixtures.moltenIronFromLavaRecipe
import de.bjrke.factorio.optimizer.common.fixtures.pipeRecipe
import de.bjrke.factorio.optimizer.common.fixtures.steelPlate
import de.bjrke.factorio.optimizer.common.fixtures.uraniumFuelCell
import de.bjrke.factorio.optimizer.common.fixtures.uraniumFuelCellRecipe
import de.bjrke.factorio.optimizer.common.fixtures.uraniumProcessingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.fixtures.waterPumpingRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OptimizerTest {

    private val miningIron = ironOreMiningRecipe
    private val pumpWater = waterPumpingRecipe
    private val meltIce = iceMeltingRecipe

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

    private val pumpLava = lavaPumpingRecipe
    private val mineCalcite = calciteMiningRecipe

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
                    ParsedRecipe(
                        emptyList(),
                        listOf(
                            ironOreMeltingRecipe,
                            moltenIronFromLavaRecipe,
                            pumpLava,
                            mineCalcite,
                        ),
                    ),
                    vulcanusFactories,
                )
                .compute(moltenIron)

        assertEquals("molten-iron-from-lava", result.recipe.name)
    }

    @Test
    fun vulcanusSteelUsesLavaNotChestOre() {
        val result =
            Optimizer(
                    ParsedRecipe(
                        emptyList(),
                        listOf(
                            castingSteelRecipe,
                            ironOreMeltingRecipe,
                            moltenIronFromLavaRecipe,
                            pumpLava,
                            mineCalcite,
                        ),
                    ),
                    vulcanusFactories,
                )
                .compute(steelPlate)

        assertAll(
            { assertEquals("casting-steel", result.recipe.name) },
            {
                assertEquals(
                    emptyList(),
                    result.factories.values.filter { it.factory == Factory.NO_FACTORY },
                )
            },
            {
                assertNotNull(
                    result.factories.values.firstOrNull {
                        it.recipe.name == "molten-iron-from-lava"
                    },
                )
            },
        )
    }

    @Test
    fun oreBasedRecipeWinsWhenOreIsProducible() {
        // on Nauvis-like surface: iron-ore is mined, lava is not available (free source)
        val result =
            Optimizer(
                    ParsedRecipe(
                        emptyList(),
                        listOf(
                            ironOreMeltingRecipe,
                            moltenIronFromLavaRecipe,
                            mineCalcite,
                            miningIron,
                        ),
                    ),
                    vulcanusFactories,
                )
                .compute(moltenIron)

        assertEquals("iron-ore-melting", result.recipe.name)
    }

    @Test
    fun scaleMultipliesMachinesAndImports() {
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(ironGearWheelRecipe)),
                setOf(Factory.ASSEMBLING_MACHINE_1, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            )

        val atOne = optimizer.compute(gear)
        val atTwo = optimizer.compute(gear, 2.0)

        fun amount(result: OptimizationResult, recipe: String) =
            result.factories.values.single { it.recipe.name == recipe }.amount

        assertAll(
            {
                assertEquals(
                    amount(atOne, "iron-gear-wheel") * 2,
                    amount(atTwo, "iron-gear-wheel"),
                    TOLERANCE,
                )
            },
            {
                // the iron plate is imported, so the import has to scale with the target too
                assertEquals(
                    amount(atOne, "iron-plate") * 2,
                    amount(atTwo, "iron-plate"),
                    TOLERANCE,
                )
            },
        )
    }

    @Test
    fun byproductRecipeRunsOnceForAllItsOutputs() {
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(uraniumProcessingRecipe, uraniumFuelCellRecipe)),
                setOf(
                    Factory.CENTRIFUGE,
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val result = optimizer.compute(uraniumFuelCell)

        val processing = result.factories.values.single { it.recipe.name == "uranium-processing" }
        assertAll(
            // 1 fuel cell/s needs 0.1 U-235/s, and one centrifuge yields 0.007/12 U-235/s, so the
            // U-235 demand alone sets the count. The same run also yields the U-238, so the
            // machines are not summed per output.
            { assertEquals(0.1 * 12.0 / 0.007, processing.amount, TOLERANCE) },
        )
    }

    @Test
    fun sharedIntermediateMachinesAreSummed() {
        val mine = ironOreMiningRecipe
        val optimizer =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(
                        mine,
                        ironPlateSmeltingRecipe,
                        ironGearWheelRecipe,
                        pipeRecipe,
                        engineUnitRecipe,
                    ),
                ),
                setOf(
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.ELECTRIC_FURNACE,
                    Factory.ELECTRIC_MINING_DRILL,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val result = optimizer.compute(engineUnit)

        val smelting = result.factories.values.single { it.recipe.name == "iron-plate" }
        // gear (2 plates) and pipe (1 plate each, 2 per engine) consume 4 plates/s in total
        assertEquals(6.4, smelting.amount, TOLERANCE)
    }
}
