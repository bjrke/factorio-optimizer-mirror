package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.fluidNetworkProblems
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** A fluid that is both an input and an output needs a feed lane and a drain lane, joined. */
class BlueprintFluidLaneLinkTest {

    @Test
    fun aFluidThatIsBothInputAndOutputJoinsItsTwoLanes() {
        // coal liquefaction takes and gives heavy oil: the drain lane has to feed the input lane
        val heavyOil = Material("heavy-oil")
        val lightOil = Material("light-oil")
        val steam = Material("steam")
        val liquefy =
            recipe(
                "liquefy",
                Category.OIL_PROCESSING,
                mapOf(heavyOil to 90.0, lightOil to 20.0, petroleum to 10.0),
                mapOf(coal to 10.0, heavyOil to 25.0, steam to 50.0),
            )
        val plan =
            BlueprintPlan(
                target = heavyOil,
                rows =
                    listOf(
                        PlannedRow.copies(planned(liquefy, heavyOil, Factory.OIL_REFINERY), 1),
                    ),
                sources =
                    listOf(
                        PlannedSource(coal, 1.0, fluid = false),
                        PlannedSource(heavyOil, 1.0, fluid = true),
                        PlannedSource(steam, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "liquefy",
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
