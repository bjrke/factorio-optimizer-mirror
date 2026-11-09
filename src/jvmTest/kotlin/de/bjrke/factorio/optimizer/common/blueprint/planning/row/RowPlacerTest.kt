package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.pipe
import de.bjrke.factorio.optimizer.common.fixtures.splitter
import de.bjrke.factorio.optimizer.common.fixtures.splitterRecipe
import de.bjrke.factorio.optimizer.common.fixtures.steelPlate
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier
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
                            planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3),
                            3,
                        ),
                    ),
                sources = listOf(PlannedSource(ironPlate, 1.0, fluid = false)),
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
        val gearMachine = planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3)
        val plateMachine = planned(ironPlateRecipe, Factory.ASSEMBLING_MACHINE_3)
        val plan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow(
                            machines = listOf(gearMachine, plateMachine, plateMachine),
                            links =
                                listOf(
                                    PlannedLink(0, ironPlate, flow = 0.1, producerIsLeft = false),
                                ),
                        ),
                    ),
                sources = listOf(PlannedSource(ironOre, 1.0, fluid = false)),
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

    @Test
    fun theNearLaneCarriesTheMostConsumedIngredient() {
        val plan =
            BlueprintPlan(
                target = electronicCircuit,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(
                                electronicCircuitRecipe,
                                Factory.ASSEMBLING_MACHINE_3,
                            ),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(copperCable, 1.0, fluid = false),
                        PlannedSource(ironPlate, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "electronic-circuit",
            )

        val row = place(plan).single()

        assertAll(
            {
                assertEquals(
                    listOf(copperCable, ironPlate),
                    row.belts.filter { it.io == Io.INPUT }.map { it.material }.toList(),
                    "order of belts doesn't reflect the expected priority of materials",
                )
            },
            {
                assertEquals(
                    mapOf(
                        copperCable to row.factoryTop - 2,
                        ironPlate to row.factoryBottom + 2,
                        electronicCircuit to row.factoryBottom + 3,
                    ),
                    row.belts.associateBy({ it.material }, { it.y }),
                )
            },
        )
    }

    @Test
    fun theInputRankingIgnoresProductivity() {
        // productivity doubles the transport-belt output, but not its iron plate and gear inputs;
        // the gear lane therefore still ranks below the splitter's circuit lane
        val plan =
            BlueprintPlan(
                target = splitter,
                rows =
                    listOf(
                        PlannedRow(
                            machines =
                                listOf(
                                    planned(
                                            transportBeltRecipe,
                                            Factory.ASSEMBLING_MACHINE_3,
                                        )
                                        .copy(productivity = 1.0),
                                    planned(splitterRecipe, Factory.ASSEMBLING_MACHINE_3),
                                ),
                            links =
                                listOf(
                                    PlannedLink(
                                        gap = 0,
                                        material = transportBelt,
                                        flow = 0.1,
                                        producerIsLeft = true,
                                    ),
                                ),
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(gear, 1.0, fluid = false),
                        PlannedSource(electronicCircuit, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "productivity-ranking",
            )

        val row = place(plan).single()

        assertEquals(
            listOf(ironPlate, electronicCircuit, gear),
            row.belts.filter { it.io == Io.INPUT }.map { it.material }.toList(),
        )
    }

    @Test
    fun aBandRanksLanesByConsumptionRateNotRawAmount() {
        // both recipes take two source items per craft, but the gear machine's 1 s craft makes
        // its iron-plate lane 2.5/s while the engine machine's 10 s craft makes the pipe lane
        // only 0.25/s, so the iron plate lane is the busier one
        val plan =
            BlueprintPlan(
                target = engineUnit,
                rows =
                    listOf(
                        PlannedRow(
                            machines =
                                listOf(
                                    planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3),
                                    planned(engineUnitRecipe, Factory.ASSEMBLING_MACHINE_3),
                                ),
                            links =
                                listOf(
                                    PlannedLink(
                                        gap = 0,
                                        material = gear,
                                        flow = 0.1,
                                        producerIsLeft = true,
                                    ),
                                ),
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(pipe, 1.0, fluid = false),
                        PlannedSource(steelPlate, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "band",
            )

        val row = place(plan).single()

        assertEquals(
            listOf(ironPlate, pipe, steelPlate),
            row.belts.filter { it.io == Io.INPUT }.map { it.material }.toList(),
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
