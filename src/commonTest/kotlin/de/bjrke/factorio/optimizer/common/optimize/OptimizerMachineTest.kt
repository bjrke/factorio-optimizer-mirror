package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.fixtures.concrete
import de.bjrke.factorio.optimizer.common.fixtures.concreteRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.factoryOf
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.nutrients
import de.bjrke.factorio.optimizer.common.fixtures.nutrientsFromBiterEggRecipe
import de.bjrke.factorio.optimizer.common.fixtures.oilRefinery
import de.bjrke.factorio.optimizer.common.fixtures.oilRefineryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromLightOilRecipe
import de.bjrke.factorio.optimizer.common.fixtures.waterPumpingRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

class OptimizerMachineTest {

    @Test
    fun biochamberIsPreferredOverAssembler() {
        assertEquals(
            Factory.BIOCHAMBER,
            factoryOf(
                nutrients,
                listOf(nutrientsFromBiterEggRecipe),
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
        assertEquals(
            Factory.CRYOGENIC_PLANT,
            factoryOf(
                plasticBar,
                listOf(plasticBarRecipe),
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
        assertEquals(
            Factory.ASSEMBLING_MACHINE_3,
            factoryOf(
                gear,
                listOf(ironGearWheelRecipe),
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
        // the cryogenic plant only runs cryogenics recipes, not plain chemistry ones (e.g. solid
        // fuel from light oil)
        assertEquals(
            Factory.CHEMICAL_PLANT,
            factoryOf(
                solidFuel,
                listOf(solidFuelFromLightOilRecipe),
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
        val pumpWater = waterPumpingRecipe

        val result =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(concreteRecipe, pumpWater)),
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
                assertEquals(
                    emptyList(),
                    result.factories.values.filter { it.factory == Factory.ASSEMBLING_MACHINE_1 },
                )
            },
        )
    }

    @Test
    fun assemblingMachine1HasNoIngredientLimit() {
        // Regression: the pre-0.17 caps (AM1 = 2, AM2 = 4 ingredients) were removed, so all
        // assemblers accept any number of ingredients; a five-ingredient crafting recipe runs on
        // AM1.
        assertEquals(
            Factory.ASSEMBLING_MACHINE_1,
            factoryOf(
                oilRefinery,
                listOf(oilRefineryRecipe),
                setOf(Factory.ASSEMBLING_MACHINE_1, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )
    }

    @Test
    fun builtInProductivityBoostsOutputWithoutExtraInput() {
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(copperCableRecipe)),
                setOf(
                    Factory.ELECTROMAGNETIC_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val production = optimizer[copperCable].production(copperCable)
        assertAll(
            { assertEquals(Factory.ELECTROMAGNETIC_PLANT, production.factory) },
            // one machine makes 2 * 1.5 outputs per 0.5 s at speed 2 = 12/s, so 1/s needs 1/12
            { assertEquals(1.0 / 12.0, production.amount, TOLERANCE) },
            { assertEquals(1.0, production.production(copperCable), TOLERANCE) },
            // the bonus does not change the input consumption: 2 / 0.5 = 4 plates/s per machine
            { assertEquals(1.0 / 3.0, production.consumption(copperPlate), TOLERANCE) },
        )
    }
}
