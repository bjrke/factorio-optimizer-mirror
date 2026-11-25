package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.test.TOLERANCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The arrangement search's pruning and its cached ratio penalty. */
class BandSearchTest : BaseGameCtxTest() {

    private val logistics = calculator()

    @Test
    fun anArrangedPlanCarriesItsRatioPenalty() {
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val cable = production(copperCableRecipe, amount = 6.0)
        val context = DirectFeedContext(optimizationResult(circuit, cable), logistics = logistics)
        val plan = assertNotNull(context.arrange(context.candidateLinks()))

        // the 6:4 need is the exact 3:2 cycle, so the stored penalty is zero
        assertEquals(0.0, plan.ratioPenalty, TOLERANCE)
    }

    @Test
    fun arrangePicksTheShortestWindow() {
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val cable = production(copperCableRecipe, amount = 6.0)
        val context = DirectFeedContext(optimizationResult(circuit, cable), logistics = logistics)
        val plan = assertNotNull(context.arrange(context.candidateLinks()))

        // 6 cable + 4 circuit is the shortest window; a longer valid window must not win
        assertEquals(10, plan.machines.size)
    }

    @Test
    fun arrangeRealizesEveryLinkAroundTheCycle() {
        // three recipes in a triangle: one link is only adjacent across the cycle's wrap boundary,
        // so the search must close the cycle before it accepts the arrangement
        val advanced = production(advancedCircuitRecipe)
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val cable = production(copperCableRecipe, amount = 6.0)
        val context =
            DirectFeedContext(optimizationResult(advanced, circuit, cable), logistics = logistics)
        val links = context.candidateLinks()
        val plan = assertNotNull(context.arrange(links))

        assertEquals(
            emptyList(),
            links.filterNot { link -> plan.feeds.any { it.link == link } },
            links.toString(),
        )
    }
}
