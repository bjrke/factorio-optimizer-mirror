package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeltRowsTest {

    private val inputs = (1..6).map { Material("input-$it") }
    private val output = Material("output")

    @Test
    fun pairsAreOnlyNeededAboveFourMaterials() =
        assertTable(
            listOf(4 to 0, 5 to 1, 6 to 2, 7 to 3, 10 to 6),
        ) { (materials, expected) ->
            assertEquals(expected, pairsNeeded(materials))
        }

    @Test
    fun sixInputsAndAProductNeedFourRows() {
        val budget =
            beltRowBudget(
                inputs = inputs.associateWith { 1.0 },
                outputs = mapOf(output to 1.0),
                pairCapacity = 10.0,
            )

        assertAll(
            { assertEquals(7, budget.materialCount) },
            { assertEquals(3, budget.pairsNeeded) },
            { assertEquals(3, budget.pairablePairs) },
            { assertTrue(budget.fits) },
            { assertEquals(4, budget.rows) },
        )
    }

    @Test
    fun aMaterialThatNeedsMoreThanOneLaneKeepsItsOwnBelt() {
        val budget =
            beltRowBudget(
                inputs = inputs.associateWith { 50.0 },
                outputs = mapOf(output to 1.0),
                pairCapacity = 10.0,
            )

        assertAll(
            { assertEquals(0, budget.pairablePairs) },
            { assertFalse(budget.fits) },
            { assertEquals(7, budget.rows) },
        )
    }

    @Test
    fun anOutputMaterialIsNeverPaired() {
        val both = inputs.first()
        val budget =
            beltRowBudget(
                inputs = (inputs + both).associateWith { 1.0 },
                outputs = mapOf(both to 1.0, output to 1.0),
                pairCapacity = 10.0,
            )

        assertAll(
            { assertEquals(7, budget.materialCount) },
            { assertEquals(3, budget.pairsNeeded) },
            // both has its own output belt, so only the other five inputs are pairable
            { assertEquals(2, budget.pairablePairs) },
            { assertFalse(budget.fits) },
        )
    }
}
