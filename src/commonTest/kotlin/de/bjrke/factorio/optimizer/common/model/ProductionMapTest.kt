package de.bjrke.factorio.optimizer.common.model

import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The dense and sparse production maps, and the bit set the band search assigns slots in. */
class ProductionMapTest {

    private val dense = FullProductionMap(arrayOf("a", "b"))

    @Test
    fun denseLookupBoundsChecksAndKeepsProductionOrder() {
        assertAll(
            { assertEquals("a", dense[RecipeIndex(0)]) },
            { assertEquals("b", dense.getValue(RecipeIndex(1))) },
            { assertNull(dense[RecipeIndex(2)]) },
            { assertNull(dense[RecipeIndex(Int.MAX_VALUE)]) },
            { assertEquals(listOf("a", "b"), dense.values.toList()) },
            { assertEquals(2, dense.arraySize) },
            { assertTrue(RecipeIndex(1) in dense) },
            { assertFalse(RecipeIndex(2) in dense) },
            { assertFailsWith<NoSuchElementException> { dense.getValue(RecipeIndex(2)) } },
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
}
