package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidNetworkProblems
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
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

    /**
     * The processing-unit row puts a mixed belt on its far north row, so the gas lane used to land
     * on that belt's U-turn and be dropped. The source block must move west to the shared marker
     * column and its connection run must tunnel under the turn. The U-turn feeder enters the turn
     * from the north, so the tile beside the turn (on the lane row) never cuts it off.
     */
    @Test
    fun aMixedBeltTurnRowKeepsItsFluidLaneConnected() {
        val layout = layoutFromUrl("http://localhost:8080/#/4.5/processing-unit/S/1/1/10")
        val gasMarker =
            layout.entities.filterIsInstance<MarkerEntity>().single {
                it.signal.fluid && it.signal.material == petroleum
            }
        val coalMarker =
            layout.entities.filterIsInstance<MarkerEntity>().single {
                !it.signal.fluid && it.signal.material == coal
            }
        val belts = layout.entities.filterIsInstance<BeltEntity>()
        // the mixed merge at (7,9) turns at (8,8); its feeder drops in from (8,7)
        val turn = Coordinate(8, 8)
        val turnFeed = Coordinate(8, 7)
        val plastic =
            layout.entities.filterIsInstance<MachineEntity>().filter {
                it.recipe == "plastic-bar"
            }
        val ports = plastic.map { it.area.topLeft + Coordinate(0, -1) }
        val gasPipes =
            layout.entities.filterIsInstance<Plumbing>().filter { it.material == petroleum }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(emptyList(), fluidNetworkProblems(layout.entities)) },
            // the fluid marker ends on the same east-aligned import column as the solid marker
            { assertEquals(coalMarker.area.topLeft.x, gasMarker.area.topLeft.x) },
            {
                assertEquals(
                    emptyList(),
                    ports.filterNot { port -> gasPipes.any { it.area.topLeft == port } },
                    "a plastic-bar machine has no gas pipe on its port",
                )
            },
            // the U-turn keeps both tiles: the turn and the feeder that drops into it
            {
                assertEquals(
                    listOf(SOUTH, SOUTH),
                    listOf(turn, turnFeed).map { tile ->
                        belts.single { it.area.topLeft == tile }.direction
                    },
                )
            },
            // no belt route may run west of the import marker column
            {
                assertEquals(
                    emptyList(),
                    belts.filter { it.area.topLeft.x < gasMarker.area.topLeft.x },
                    "a belt runs west of the import markers",
                )
            },
        )
    }
}
