package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
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
            { assertEquals(3, budget.capacitySafePairs) },
            { assertEquals(3, budget.pairablePairs) },
            { assertTrue(budget.fits) },
            { assertEquals(4, budget.rows) },
        )
    }

    /**
     * A fast input only shares a belt when the row cannot fit otherwise: a belt that carries half
     * the plan rate is still better than a material without a belt or a dropped product.
     */
    @Test
    fun anOverCapacityInputPairsBeforeTheRowLosesABelt() {
        val budget =
            beltRowBudget(
                inputs = inputs.associateWith { 50.0 },
                outputs = mapOf(output to 1.0),
                pairCapacity = 10.0,
            )

        assertAll(
            { assertEquals(0, budget.capacitySafePairs) },
            { assertEquals(3, budget.pairablePairs) },
            { assertTrue(budget.fits) },
            { assertEquals(4, budget.rows) },
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

    @Test
    fun capacitySafeInputsPairBeforeOverCapacityOnes() {
        val safe = (1..2).map { Material("safe-$it") }
        val fast = (1..2).map { Material("fast-$it") }
        val rates = safe.associateWith { 1.0 } + fast.associateWith { 50.0 }

        val packing =
            packRowBelts(
                solids = rates.keys.toList() + output,
                inputs = rates,
                outputs = mapOf(output to 1.0),
                directions = rates.keys.associateWith { EAST },
                pairCapacity = 10.0,
            )

        assertAll(
            { assertTrue(packing.fits) },
            { assertEquals(4, packing.groups.size) },
            { assertEquals(1, packing.groups.count { it.materials.toSet() == safe.toSet() }) },
        )
    }

    /** With six inputs the row needs three pairs: the safe pair plus the two it cannot avoid. */
    @Test
    fun overCapacityInputsPairOnlyAsFarAsTheRowNeeds() {
        val safe = (1..2).map { Material("safe-$it") }
        val fast = (1..4).map { Material("fast-$it") }
        val rates = safe.associateWith { 1.0 } + fast.associateWith { 50.0 }

        val packing =
            packRowBelts(
                solids = rates.keys.toList() + output,
                inputs = rates,
                outputs = mapOf(output to 1.0),
                directions = rates.keys.associateWith { EAST },
                pairCapacity = 10.0,
            )

        assertAll(
            { assertTrue(packing.fits) },
            { assertEquals(4, packing.groups.size) },
            { assertEquals(1, packing.groups.count { it.materials.toSet() == safe.toSet() }) },
            {
                assertEquals(
                    2,
                    packing.groups.count { group ->
                        group.materials.size == 2 && group.materials.all { it in fast }
                    },
                )
            },
        )
    }
}
