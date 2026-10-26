package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.gear
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.ore
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.js.component.fixtures.OptimizationResultTestBase
import de.bjrke.factorio.optimizer.js.component.fixtures.craftingRecipe
import de.bjrke.factorio.optimizer.js.component.fixtures.miningRecipe
import de.bjrke.factorio.optimizer.js.component.fixtures.optimizationResult
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The belt and inserter counts the block shows, and how stacking changes them. */
class OptimizationResultLogisticsTest : OptimizationResultTestBase() {

    @Test
    fun aStackInserterNeedsFewerOutputInsertersThanABulkInserter() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        val rendered =
            renderResult(
                result,
                calculator(InserterTier.BULK_INSERTER, beltStack = 4, inserterStack = 8),
            )
        val bulkOutput = inserterInput(1)

        rendered.rerender(
            resultElement(
                result,
                calculator(InserterTier.STACK_INSERTER, beltStack = 4, inserterStack = 8),
            ),
        )
        val stackOutput = inserterInput(1)

        // same hand size; the stack inserter places whole belt stacks while the bulk places
        // one item per belt slot
        assertTrue(stackOutput < bulkOutput)
    }

    @Test
    fun miningOutputNeedsNoInserter() {
        renderResult(optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL))

        assertNotNull(screen.queryByTitle("belts"))
        assertNull(screen.queryByTitle("inserters"))
    }

    @Test
    fun theBigMiningDrillHonorsTheBeltStackWithoutAnInserter() {
        val result = optimizationResult(miningRecipe, ore, Factory.BIG_MINING_DRILL)
        val rendered = renderResult(result, calculator(InserterTier.FAST_INSERTER, beltStack = 1))
        val atSingle = beltInput(0)

        rendered.rerender(
            resultElement(result, calculator(InserterTier.FAST_INSERTER, beltStack = 4)),
        )
        val atStacked = beltInput(0)

        // the big mining drill stacks the belt itself, so stacking means a quarter of the belts
        assertEquals(atSingle, atStacked * 4, 0.01)
    }

    @Test
    fun theOtherMiningDrillsIgnoreTheBeltStack() {
        val result = optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL)
        val rendered = renderResult(result, calculator(InserterTier.FAST_INSERTER, beltStack = 1))
        val atSingle = beltInput(0)

        rendered.rerender(
            resultElement(result, calculator(InserterTier.FAST_INSERTER, beltStack = 4)),
        )

        // an electric mining drill places single items, so stacking does not change its belts
        assertEquals(atSingle, beltInput(0), 0.01)
    }

    @Test
    fun otherOutputsOnlyStackWithAStackingInserter() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        val rendered = renderResult(result, calculator(InserterTier.STACK_INSERTER, beltStack = 4))
        val inputStacked = beltInput(0)
        val outputStacked = beltInput(1)

        rendered.rerender(
            resultElement(result, calculator(InserterTier.FAST_INSERTER, beltStack = 4)),
        )
        val inputUnstacked = beltInput(0)
        val outputUnstacked = beltInput(1)

        // inputs always use the stacked belt; a non-stacking inserter leaves the output unstacked
        assertEquals(inputStacked, inputUnstacked, 0.01)
        assertEquals(outputUnstacked, outputStacked * 4, 0.01)
    }
}
