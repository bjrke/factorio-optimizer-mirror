package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class OptimizationResultTest : BaseGameCtxTest() {

    // the recipes differ in shape and level, which must not influence the display order
    private val rows =
        listOf(
            production(
                recipe = ironPlateSmeltingRecipe,
                factory = Factory.ELECTRIC_FURNACE,
                level = 2,
            ),
            production(
                recipe = ironGearWheelRecipe,
                factory = Factory.ASSEMBLING_MACHINE_1,
            ),
            production(
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

    @Test
    fun beltedCopiesKeepTheirOwnAmount() {
        val production = rows.first()

        assertAll(
            { assertEquals(production, production.copy()) },
            { assertNotEquals(production, production.copy(amount = production.amount + 1.0)) },
        )
    }

    @Test
    fun aFactoryIsFoundByAnEqualRecipeIndex() {
        val production = rows.first()
        val result = optimizationResult(production)
        val lookup = IndexedRecipe.of(production.recipe.index, ironGearWheelRecipe)

        assertSame(production, result.factories[lookup])
    }
}
