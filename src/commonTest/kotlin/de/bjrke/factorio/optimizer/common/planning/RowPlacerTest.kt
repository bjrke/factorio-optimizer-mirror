package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.craftRecipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.gear
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.ore
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plate
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.smeltRecipe
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

class RowPlacerTest {

    @Test
    fun aPlainRowKeepsItsMachinesTouching() {
        val plan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(craftRecipe, gear, Factory.ASSEMBLING_MACHINE_3),
                            3,
                        ),
                    ),
                sources = listOf(PlannedSource(plate, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )

        val row = place(plan).single()

        assertAll(
            { assertEquals(40, row.left) },
            { assertEquals(3, row.width) },
            { assertEquals(listOf(40, 43, 46), row.machines.map { it.left }) },
        )
    }

    @Test
    fun onlyALinkedGapGetsASeparatingColumn() {
        val gearMachine = planned(craftRecipe, gear, Factory.ASSEMBLING_MACHINE_3)
        val plateMachine = planned(smeltRecipe, plate, Factory.ASSEMBLING_MACHINE_3)
        val plan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow(
                            machines = listOf(gearMachine, plateMachine, plateMachine),
                            links =
                                listOf(PlannedLink(0, plate, flow = 0.1, producerIsLeft = false)),
                        ),
                    ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )

        val row = place(plan).single()

        assertAll(
            { assertEquals(40, row.left) },
            { assertEquals(3, row.width) },
            { assertEquals(listOf(40, 44, 47), row.machines.map { it.left }) },
        )
    }

    private fun place(plan: BlueprintPlan): List<Row> {
        val board =
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
        val planner = RowPlanner(plan, logistics)
        return RowPlacer(board, plan, logistics, planner).run {
            placeAll()
            placedRows
        }
    }
}
