package de.bjrke.factorio.optimizer.common.model

import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The production maps, the dense primitive maps the band search counts with, and its slot set. */
class ProductionMapTest {

    private val dense = FullProductionMap(arrayOf("a", "b"))

    @Test
    fun denseLookupKeepsProductionOrderAndContainsBoundsChecks() {
        assertAll(
            { assertEquals("a", dense[RecipeIndex(0)]) },
            { assertEquals("b", dense.getValue(RecipeIndex(1))) },
            { assertEquals(listOf("a", "b"), dense.values.toList()) },
            { assertEquals(2, dense.arraySize) },
            { assertTrue(RecipeIndex(1) in dense) },
            { assertFalse(RecipeIndex(2) in dense) },
            { assertFalse(RecipeIndex(Int.MAX_VALUE) in dense) },
        )
    }

    @Test
    fun sparseLookupHidesTheHolesAndListsOnlyPresentValues() {
        val sparse = SparseProductionMap<String>(3)
        sparse[RecipeIndex(0)] = "a"
        sparse[RecipeIndex(2)] = "c"

        assertAll(
            { assertEquals("a", sparse[RecipeIndex(0)]) },
            { assertNull(sparse[RecipeIndex(1)]) },
            { assertNull(sparse[RecipeIndex(3)]) },
            { assertTrue(RecipeIndex(2) in sparse) },
            { assertFalse(RecipeIndex(1) in sparse) },
            { assertEquals(listOf("a", "c"), sparse.values.toList()) },
            { assertEquals(3, sparse.arraySize) },
            { assertFailsWith<NoSuchElementException> { sparse.getValue(RecipeIndex(1)) } },
        )
    }

    @Test
    fun theSparseBlockFactoryReturnsTheReadOnlyMapType() {
        val map: ProductionMap<String> =
            SparseProductionMap<String>(2) { this[RecipeIndex(1)] = "b" }

        assertAll(
            { assertNull(map[RecipeIndex(0)]) },
            { assertEquals(listOf("b"), map.values.toList()) },
        )
    }

    @Test
    fun indexSetMarksAndClearsSlots() {
        val assigned = IndexSet(3)
        assigned[RecipeIndex(0)] = true
        assigned[RecipeIndex(2)] = true
        assigned[RecipeIndex(2)] = false

        assertAll(
            { assertTrue(RecipeIndex(0) in assigned) },
            { assertFalse(RecipeIndex(1) in assigned) },
            { assertFalse(RecipeIndex(2) in assigned) },
        )
    }

    @Test
    fun indexSetAddReportsWhetherTheSlotWasNew() {
        val assigned = IndexSet(3)

        assertAll(
            { assertTrue(assigned.add(RecipeIndex(1))) },
            { assertFalse(assigned.add(RecipeIndex(1))) },
            { assertTrue(RecipeIndex(1) in assigned) },
            { assertFalse(RecipeIndex(0) in assigned) },
        )
    }

    @Test
    fun denseMapsReadAndWritePrimitiveValues() {
        val counts = ProductionIntMap(3)
        val rates = ProductionDoubleMap(3)
        counts[RecipeIndex(1)] += 1
        counts[RecipeIndex(1)] += 1
        rates[RecipeIndex(2)] = 1.5

        assertAll(
            { assertEquals(0, counts[RecipeIndex(0)]) },
            { assertEquals(2, counts[RecipeIndex(1)]) },
            { assertEquals(0.0, rates[RecipeIndex(1)]) },
            { assertEquals(1.5, rates[RecipeIndex(2)]) },
        )
    }
}
