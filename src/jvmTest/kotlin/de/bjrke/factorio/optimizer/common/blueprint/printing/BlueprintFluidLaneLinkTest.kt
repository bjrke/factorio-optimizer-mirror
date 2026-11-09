package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidNetworkProblems
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.coalLiquefactionRecipe
import de.bjrke.factorio.optimizer.common.fixtures.heavyOil
import de.bjrke.factorio.optimizer.common.fixtures.lightOil
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.steam
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** A fluid that is both an input and an output needs a feed lane and a drain lane, joined. */
class BlueprintFluidLaneLinkTest {

    @Test
    fun aFluidThatIsBothInputAndOutputJoinsItsTwoLanes() {
        // coal liquefaction takes and gives heavy oil: the drain lane has to feed the input lane
        val plan =
            BlueprintPlan(
                target = heavyOil,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(coalLiquefactionRecipe, heavyOil, Factory.OIL_REFINERY),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(coal, 1.0, fluid = false),
                        PlannedSource(heavyOil, 1.0, fluid = true),
                        PlannedSource(steam, 1.0, fluid = true),
                    ),
                label = "coal-liquefaction",
            )
        val fluids =
            LogisticsCalculator(
                testLogistics.copy(fluids = setOf(heavyOil, lightOil, petroleum, steam)),
            )
        val layout = Layout(plan, fluids)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(emptyList(), fluidNetworkProblems(layout.entities)) },
        )
    }
}
