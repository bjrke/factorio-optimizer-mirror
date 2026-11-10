package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.TOLERANCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private fun production(
    recipe: Recipe,
    material: Material,
    amount: Double = 1.0,
) =
    FactoryProduction(
        material,
        recipe,
        Factory.ASSEMBLING_MACHINE_3,
        amount,
        0,
        1.0,
        0.0,
    )

private fun factories(vararg productions: FactoryProduction) = productions.associateBy {
    it.recipe
}

/** The arrangement search's pruning and its cached ratio penalty. */
class BandSearchTest {

    private val logistics = calculator()

    @Test
    fun anArrangedPlanCarriesItsRatioPenalty() {
        val circuit = production(electronicCircuitRecipe, electronicCircuit, amount = 4.0)
        val cable = production(copperCableRecipe, copperCable, amount = 6.0)
        val context = DirectFeedContext(circuit, factories(circuit, cable), logistics)
        val plan = assertNotNull(context.arrange(context.candidateLinks()))

        // the 6:4 need is the exact 3:2 cycle, so the stored penalty is zero
        assertEquals(0.0, plan.ratioPenalty, TOLERANCE)
    }

    @Test
    fun arrangePicksTheShortestWindow() {
        val circuit = production(electronicCircuitRecipe, electronicCircuit, amount = 4.0)
        val cable = production(copperCableRecipe, copperCable, amount = 6.0)
        val context = DirectFeedContext(circuit, factories(circuit, cable), logistics)
        val plan = assertNotNull(context.arrange(context.candidateLinks()))

        // 6 cable + 4 circuit is the shortest window; a longer valid window must not win
        assertEquals(10, plan.machines.size)
    }
}
