package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.uranium235
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.forSurface
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The uranium chain is the plan's worst-conditioned one: uranium processing yields 0.000583
 * uranium-235 per second per machine, while the same matrix carries rates in the thousands. Before
 * the solver normalized its rows, every target that needs uranium-235 made it report "no feasible
 * production plan" once the intermediate limit let the whole chain in.
 */
class OptimizerUraniumTest {

    private val nauvis by lazy { RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS) }

    @Test
    fun uraniumTargetsBuildTheChainInsteadOfFailing() =
        assertTable(listOf("atomic-bomb", "nuclear-fuel")) { name ->
            val target = Material(name)
            val result = Optimizer(nauvis, maxIntermediates = MAX_INTERMEDIATE_LIMIT)[target]
            val uranium = result.production(uranium235)
            assertAll(
                { assertEquals(name, result.production(target).recipe.name) },
                { assertNotEquals(Factory.NO_FACTORY, result.production(target).factory) },
                { assertEquals("uranium-processing", uranium.recipe.name) },
                { assertEquals(Factory.CENTRIFUGE, uranium.factory) },
            )
        }
}
