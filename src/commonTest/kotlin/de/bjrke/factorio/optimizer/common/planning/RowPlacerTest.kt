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
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Recipe
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

    @Test
    fun theNearLaneCarriesTheMostConsumedIngredient() {
        val heavy = Material("heavy-input")
        val light = Material("light-input")
        val twoInputs =
            Recipe(
                "two-inputs",
                ingredients = mapOf(light to 1.0, heavy to 10.0),
                result = mapOf(gear to 1.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            )
        val plan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(twoInputs, gear, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(heavy, 1.0, fluid = false),
                        PlannedSource(light, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "two inputs",
            )

        val row = place(plan).single()

        assertAll(
            { assertEquals(listOf(heavy, light), row.solids) },
            { assertEquals(row.factoryTop - 2, row.laneNearRow) },
        )
    }

    @Test
    fun aBandRanksLanesByConsumptionRateNotRawAmount() {
        val heavy = Material("heavy-input")
        val light = Material("light-input")
        val product = Material("band-product")
        val intermediate = Material("band-intermediate")
        // the slow machine uses ten heavy items per craft, but only one per second; the fast one
        // uses a single light item per craft at two per second, so the light lane is the busier one
        val slow =
            Recipe(
                "slow-band-machine",
                ingredients = mapOf(heavy to 10.0, intermediate to 1.0),
                result = mapOf(product to 1.0),
                time = 10.0,
                categories = setOf(Category.CRAFTING),
            )
        val fast =
            Recipe(
                "fast-band-machine",
                ingredients = mapOf(light to 1.0),
                result = mapOf(intermediate to 1.0),
                time = 0.5,
                categories = setOf(Category.CRAFTING),
            )
        val plan =
            BlueprintPlan(
                target = product,
                rows =
                    listOf(
                        PlannedRow(
                            machines =
                                listOf(
                                    planned(fast, intermediate, Factory.ASSEMBLING_MACHINE_3),
                                    planned(slow, product, Factory.ASSEMBLING_MACHINE_3),
                                ),
                            links =
                                listOf(
                                    PlannedLink(
                                        gap = 0,
                                        material = intermediate,
                                        flow = 0.1,
                                        producerIsLeft = true,
                                    ),
                                ),
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(heavy, 1.0, fluid = false),
                        PlannedSource(light, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "band",
            )

        val row = place(plan).single()

        assertEquals(listOf(light, heavy), row.solids)
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
