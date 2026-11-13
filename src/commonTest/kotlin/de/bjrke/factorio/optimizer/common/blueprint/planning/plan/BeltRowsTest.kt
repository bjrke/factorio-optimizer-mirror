package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
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
                allowOverCapacityPairs = true,
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

    /** A row that already fits still pairs every capacity-safe input, saving belt rows. */
    @Test
    fun everyCapacitySafeInputPairIsFormed() {
        val rates = (1..4).associate { Material("input-$it") to 1.0 }

        val packing = packing(rates)

        assertAll(
            { assertTrue(packing.fits) },
            { assertEquals(3, packing.groups.size) },
            { assertEquals(2, packing.groups.count { it.materials.size == 2 }) },
        )
    }

    @Test
    fun aPairThatFitsOneLaneStillMergesAtTheBoundary() {
        val safe =
            packing(
                mapOf(Material("ore") to 7.5, Material("coal") to 0.2),
                pairCapacity = 7.5,
            )
        val tooFast =
            packing(
                mapOf(Material("ore") to 7.51, Material("coal") to 0.2),
                pairCapacity = 7.5,
            )

        assertAll(
            { assertEquals(1, safe.groups.count { it.materials.size == 2 }) },
            { assertEquals(0, tooFast.groups.count { it.materials.size == 2 }) },
        )
    }

    @Test
    fun anImportAnchorsItsPairToEastAndStaysStraight() {
        val coal = Material("coal")
        val ore = Material("copper-ore")
        val directions = mapOf(coal to EAST, ore to WEST)

        val packing =
            packing(
                mapOf(coal to 0.2, ore to 2.8),
                directions = directions,
                imports = setOf(coal),
            )
        val mixed = packing.groups.single { it.materials.size == 2 }

        assertAll(
            { assertEquals(EAST, mixed.direction) },
            { assertEquals(coal, mixed.materials.first(), "the import must be the straight feed") },
        )
    }

    @Test
    fun twoProducedFeedsFromOppositeDirectionsStaySeparate() {
        val east = Material("east")
        val west = Material("west")

        val packing =
            packing(
                mapOf(east to 1.0, west to 2.0),
                directions = mapOf(east to EAST, west to WEST),
            )

        assertEquals(0, packing.groups.count { it.materials.size == 2 })
    }

    /**
     * A fast input only shares a belt when the row cannot fit otherwise: a belt that carries half
     * the plan rate is still better than a material without a belt or a dropped product.
     */
    @Test
    fun anOverCapacityInputPairsBeforeTheRowLosesABelt() {
        val inputs = inputs.associateWith { 50.0 }
        val slowBelt =
            beltRowBudget(
                inputs = inputs,
                outputs = mapOf(output to 1.0),
                pairCapacity = 10.0,
                allowOverCapacityPairs = false,
            )
        val singleRecipe =
            beltRowBudget(
                inputs = inputs,
                outputs = mapOf(output to 1.0),
                pairCapacity = 10.0,
                allowOverCapacityPairs = true,
            )

        assertAll(
            { assertEquals(0, slowBelt.capacitySafePairs) },
            { assertEquals(3, slowBelt.pairablePairs) },
            { assertFalse(slowBelt.fits, "a band may not share a lane above its capacity") },
            { assertTrue(singleRecipe.fits) },
            { assertEquals(4, singleRecipe.rows) },
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
                allowOverCapacityPairs = true,
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

        val packing = packing(rates, pairCapacity = 10.0, allowOverCapacityPairs = true)

        assertAll(
            { assertTrue(packing.fits) },
            { assertEquals(4, packing.groups.size) },
            { assertEquals(1, packing.groups.count { it.materials.toSet() == safe.toSet() }) },
            { assertEquals(0, packing.groups.count { it.materials.toSet() == fast.toSet() }) },
        )
    }

    /** With six inputs the row needs three pairs: the safe pair plus the two it cannot avoid. */
    @Test
    fun overCapacityInputsPairOnlyAsFarAsTheRowNeeds() {
        val safe = (1..2).map { Material("safe-$it") }
        val fast = (1..4).map { Material("fast-$it") }
        val rates = safe.associateWith { 1.0 } + fast.associateWith { 50.0 }

        val packing = packing(rates, pairCapacity = 10.0, allowOverCapacityPairs = true)

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

    /** A band with several recipes may not rely on the single-recipe over-capacity rescue. */
    @Test
    fun aBandWithoutTheSingleRecipeRescueDoesNotFit() {
        val rates = (1..5).associate { Material("fast-$it") to 50.0 }
        val band = packing(rates, pairCapacity = 10.0, allowOverCapacityPairs = false)
        val singleRecipe = packing(rates, pairCapacity = 10.0, allowOverCapacityPairs = true)

        assertAll(
            { assertFalse(band.fits) },
            { assertTrue(singleRecipe.fits) },
            { assertEquals(4, singleRecipe.groups.size) },
        )
    }

    private fun packing(
        rates: Map<Material, Double>,
        directions: Map<Material, Direction> = rates.keys.associateWith { EAST },
        imports: Set<Material> = emptySet(),
        pairCapacity: Double = 10.0,
        allowOverCapacityPairs: Boolean = true,
    ) =
        packRowBelts(
            BeltPackRequest(
                rates = RowBeltRates(rates, mapOf(output to 1.0), pairCapacity),
                directions = directions,
                imports = imports,
                allowOverCapacityPairs = allowOverCapacityPairs,
            ),
        )
}
