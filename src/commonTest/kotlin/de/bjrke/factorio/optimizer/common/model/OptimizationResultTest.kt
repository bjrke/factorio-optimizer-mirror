package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import kotlin.test.Test
import kotlin.test.assertEquals

class OptimizationResultTest {

    // the recipes differ in shape and level, which must not influence the display order
    private val rows =
        listOf(
            FactoryProduction(
                material = ironPlate,
                recipe = ironPlateSmeltingRecipe,
                factory = Factory.ELECTRIC_FURNACE,
                level = 2,
            ),
            FactoryProduction(
                material = gear,
                recipe = ironGearWheelRecipe,
                factory = Factory.ASSEMBLING_MACHINE_1,
                level = 0,
            ),
            FactoryProduction(
                material = electronicCircuit,
                recipe = electronicCircuitRecipe,
                factory = Factory.ASSEMBLING_MACHINE_2,
                level = 1,
            ),
        )

    @Test
    fun rowsSortByMaterialName() {
        val order = rows.sortedWith(factoryProductionComparator)

        assertEquals(
            listOf(electronicCircuit.name, gear.name, ironPlate.name),
            order.map { it.material.name },
        )
    }
}
