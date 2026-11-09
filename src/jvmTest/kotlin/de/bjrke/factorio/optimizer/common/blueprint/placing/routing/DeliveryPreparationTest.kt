package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.placing.route.LayoutRouter
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LayoutStage
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlanner
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.fixtures.uranium235
import de.bjrke.factorio.optimizer.common.fixtures.uraniumFuelCell
import de.bjrke.factorio.optimizer.common.fixtures.uraniumFuelCellRecipe
import de.bjrke.factorio.optimizer.common.fixtures.uraniumProcessingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.uraniumRoundsMagazineRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier
import kotlin.test.Test
import kotlin.test.assertEquals

class DeliveryPreparationTest {

    @Test
    fun everyProductStartsOnItsOwnOutputBelt() {
        val plan = twoProductPlan()
        val board = board()
        val planner = RowPlanner(plan, logistics)
        val rowPlacer = RowPlacer(board, plan, logistics, planner)
        val stage = LayoutStage(board, plan, logistics, planner, rowPlacer, LayoutRouter(board))
        rowPlacer.placeAll()
        val producer = rowPlacer.placedRows.first()
        val expected =
            producer.belts
                .filter { it.io == Io.OUTPUT }
                .associate { it.material to Coordinate(producer.right + 1, it.y) }

        val deliveries =
            DeliveryPreparation(
                    stage,
                    BeltEndConnector(board, BeltEnd.CONSTANT_COMBINATOR),
                    ImportExtension(),
                    BeltEnd.CONSTANT_COMBINATOR,
                )
                .prepareDeliveries()

        assertEquals(
            expected,
            deliveries.produced.associate { it.material to it.start },
        )
    }

    private fun twoProductPlan(): BlueprintPlan {
        return BlueprintPlan(
            target = uraniumFuelCell,
            rows =
                listOf(
                    PlannedRow.copies(
                        planned(
                            uraniumProcessingRecipe,
                            uranium235,
                            Factory.ASSEMBLING_MACHINE_3,
                        ),
                        1,
                    ),
                    PlannedRow.copies(
                        planned(uraniumFuelCellRecipe, Factory.ASSEMBLING_MACHINE_3),
                        1,
                    ),
                    PlannedRow.copies(
                        planned(uraniumRoundsMagazineRecipe, Factory.ASSEMBLING_MACHINE_3),
                        1,
                    ),
                ),
            sources = emptyList(),
            label = "two products",
        )
    }

    private fun board() =
        LayoutBoard(
            LayoutNames(
                logistics.belt.identifier,
                logistics.inserter.identifier,
                InserterTier.LONG_HANDED_INSERTER.identifier,
            ),
            undergroundRange = 9,
            logistics::isFluid,
            logistics::fluidTemperature,
        )
}
