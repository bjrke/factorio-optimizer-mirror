package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.beltend.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.planning.LayoutStage
import de.bjrke.factorio.optimizer.common.planning.RowPlacer
import de.bjrke.factorio.optimizer.common.planning.RowPlanner
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.route.LayoutRouter
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
        val first = Material("first-product")
        val second = Material("second-product")
        val firstResult = Material("first-result")
        val secondResult = Material("second-result")
        val split = recipe("split", Category.CRAFTING, mapOf(first to 1.0, second to 1.0))
        val consumeFirst =
            recipe(
                "consume-first",
                Category.CRAFTING,
                mapOf(firstResult to 1.0),
                mapOf(first to 1.0),
            )
        val consumeSecond =
            recipe(
                "consume-second",
                Category.CRAFTING,
                mapOf(secondResult to 1.0),
                mapOf(second to 1.0),
            )
        return BlueprintPlan(
            target = firstResult,
            rows =
                listOf(
                    PlannedRow.copies(planned(split, first, Factory.ASSEMBLING_MACHINE_3), 1),
                    PlannedRow.copies(
                        planned(consumeFirst, firstResult, Factory.ASSEMBLING_MACHINE_3),
                        1,
                    ),
                    PlannedRow.copies(
                        planned(consumeSecond, secondResult, Factory.ASSEMBLING_MACHINE_3),
                        1,
                    ),
                ),
            sources = emptyList(),
            scale = 1.0,
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
