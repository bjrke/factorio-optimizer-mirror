package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The result always names a production for its target; planning reads it instead of a lookup. */
class OptimizerTargetTest : BaseGameCtxTest() {

    @Test
    fun theTargetProductionIsOneOfItsFactories() {
        val result =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(ironPlateSmeltingRecipe, ironGearWheelRecipe),
                    gameCtx = gameCtx,
                ),
                setOf(Factory.STONE_FURNACE, Factory.ASSEMBLING_MACHINE_1, Factory.NO_FACTORY),
            )[gear]

        assertAll(
            { assertEquals(gear, result.targetProduction.material) },
            {
                assertSame(
                    result.targetProduction,
                    result.factories.getValue(result.targetProduction.recipe),
                )
            },
        )
    }

    @Test
    fun aTargetBelowTheMachineEpsilonIsStillReported() {
        // one machine makes 2e7 items a second, so a rate of one item needs 5e-8 machines: below
        // the epsilon that hides a production, but the target must reach the result regardless
        val target = Material("bulk")
        val bulk =
            Recipe(
                name = "bulk",
                ingredients = emptyMap(),
                result = mapOf(target to 2e7),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            )
        val result =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(bulk), gameCtx = gameCtx),
                setOf(Factory.ASSEMBLING_MACHINE_3),
            )[target]

        assertAll(
            { assertEquals(target, result.targetProduction.material) },
            { assertTrue(result.targetProduction.amount < 1e-7) },
            {
                assertSame(
                    result.targetProduction,
                    result.factories.getValue(result.targetProduction.recipe),
                )
            },
        )
    }
}
