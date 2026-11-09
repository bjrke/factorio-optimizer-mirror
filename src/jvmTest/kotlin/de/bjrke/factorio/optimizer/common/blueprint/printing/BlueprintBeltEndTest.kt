package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.CircuitCondition
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.RequestFilter
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintWire
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The belt-end markers: labels, infinity chests and chests the inserters feed. */
class BlueprintBeltEndTest {

    @Test
    fun infinityChestsAreOptIn() {
        val without = Layout(basePlan).blueprint.entities
        val with = Layout(basePlan, beltEnd = BeltEnd.INFINITY_CHEST).blueprint.entities

        assertAll(
            {
                assertEquals(
                    emptyList(),
                    without.filter { it.name == "infinity-chest" },
                    "chests by default",
                )
            },
            {
                assertNotNull(
                    with.firstOrNull {
                        it.name == "infinity-chest" && it.infinity?.item == ironOre
                    },
                    "no input chest for the imported ore",
                )
            },
            {
                assertNotNull(
                    with.firstOrNull { it.name.endsWith("-loader") },
                    "no loader",
                )
            },
        )
    }

    @Test
    fun aChestMarkerFeedsTheBeltOutOfTheChest() {
        val layout = Layout(basePlan, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest =
            layout.entities.filterIsInstance<ChestEntity>().single {
                it.requestFilter?.name == ironOre.name
            }
        val row = chest.area.topLeft.y
        val left = chest.area.topLeft.x - 2
        val byTile = layout.entities.associateBy { it.area.topLeft }

        assertAll(
            { assertEquals(BeltEnd.BUFFER_CHEST.identifier, chest.name) },
            {
                assertEquals(
                    RequestFilter(ironOre.name, 100, trashNotRequested = true),
                    chest.requestFilter,
                )
            },
            {
                assertEquals(
                    Io.INPUT,
                    (byTile[Coordinate(left, row)] as? UndergroundBeltEntity)?.io,
                )
            },
            {
                assertEquals(
                    Io.OUTPUT,
                    (byTile[Coordinate(left + 4, row)] as? UndergroundBeltEntity)?.io,
                )
            },
            {
                assertEquals(
                    EAST,
                    (byTile[Coordinate(left + 1, row)] as? InserterEntity)?.direction,
                )
            },
            {
                val far = byTile[Coordinate(left + 3, row)] as? InserterEntity
                assertEquals(WEST, far?.direction)
                assertEquals(true, far?.mirror)
            },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun theTargetOutputUsesTheChestOutputVariant() {
        val layout = Layout(basePlan, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest =
            layout.entities.filterIsInstance<ChestEntity>().single {
                it.requestFilter?.name == gear.name
            }
        val row = chest.area.topLeft.y
        val left = chest.area.topLeft.x - 2
        val byTile = layout.entities.associateBy { it.area.topLeft }
        val near = byTile[Coordinate(left + 1, row)] as? InserterEntity
        val far = byTile[Coordinate(left + 3, row)] as? InserterEntity
        val condition = CircuitCondition(gear.name, "<", 200)

        assertAll(
            {
                assertEquals(
                    RequestFilter(gear.name, 200, trashNotRequested = true),
                    chest.requestFilter,
                )
            },
            { assertEquals(false, chest.circuitConditionEnabled) },
            { assertEquals(WEST, near?.direction) },
            { assertEquals(EAST, far?.direction) },
            { assertEquals(condition, near?.circuitCondition) },
            { assertEquals(condition, far?.circuitCondition) },
        )
    }

    @Test
    fun eachFilteredChestMarkerKeepsItsRequest() =
        assertTable(listOf(BeltEnd.BUFFER_CHEST to 100, BeltEnd.STORAGE_CHEST to 1)) {
            (beltEnd, count) ->
            val chest =
                Layout(basePlan, beltEnd = beltEnd).blueprint.entities.single {
                    it.name == beltEnd.identifier && it.requestFilter?.name == ironOre.name
                }
            assertEquals(
                RequestFilter(
                    ironOre.name,
                    count,
                    trashNotRequested = beltEnd == BeltEnd.BUFFER_CHEST,
                ),
                chest.requestFilter,
            )
        }

    @Test
    fun plainChestMarkersAreBarred() =
        assertTableOf(BeltEnd.WOODEN_CHEST, BeltEnd.STEEL_CHEST) { beltEnd ->
            val chests =
                Layout(basePlan, beltEnd = beltEnd).blueprint.entities.filter {
                    it.name == beltEnd.identifier
                }
            assertAll(
                { assertEquals(2, chests.size, "one input and one output chest") },
                { assertEquals(emptyList(), chests.filterNot { it.bar == 4 }, chests.toString()) },
                {
                    assertEquals(
                        emptyList(),
                        chests.filterNot { it.requestFilter == null },
                        chests.toString(),
                    )
                },
            )
        }

    @Test
    fun fluidImportsKeepTheCombinatorLabelEvenWithAChestMarker() {
        val fluidPlan =
            BlueprintPlan(
                target = solidFuel,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(solidFuelFromPetroleumGasRecipe, Factory.CHEMICAL_PLANT),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(petroleum, 1.0, fluid = true)),
                scale = 1.0,
                label = "solid-fuel-from-petroleum-gas",
            )
        val layout = Layout(fluidPlan, beltEnd = BeltEnd.BUFFER_CHEST)
        val marker = layout.entities.filterIsInstance<MarkerEntity>().single { it.signal.fluid }
        // the label still hides the fluid under a buried run that reaches the lane next to it
        val lane = marker.area.topLeft.east().east()

        assertAll(
            { assertEquals(petroleum, marker.signal.material) },
            { assertEquals(emptyList(), layout.failures) },
            {
                assertNotNull(
                    layout.entities.firstOrNull { it is Plumbing && it.area.topLeft == lane },
                    "the label's buried run does not reach the lane at $lane",
                )
            },
        )
    }

    @Test
    fun theChestOutputWiresBothInsertersToTheChest() {
        val blueprint = Layout(basePlan, beltEnd = BeltEnd.BUFFER_CHEST).blueprint
        val chest = blueprint.entities.indexOfFirst { it.requestFilter?.name == gear.name } + 1

        assertEquals(
            listOf(
                BlueprintWire(chest - 2, 1, chest, 1),
                BlueprintWire(chest - 1, 1, chest, 1),
            ),
            blueprint.wires,
        )
    }

    @Test
    fun theChestInputNeedsNoCircuitWire() =
        assertTable(listOf(BeltEnd.BUFFER_CHEST, BeltEnd.STORAGE_CHEST)) { beltEnd ->
            val blueprint = Layout(basePlan, beltEnd = beltEnd).blueprint
            val inputChest =
                blueprint.entities.indexOfFirst { it.requestFilter?.name == ironOre.name } + 1
            val wired = blueprint.wires.flatMap { listOf(it.first, it.second) }

            assertContainsNot(wired, inputChest)
        }

    @Test
    fun plainAndCheatMarkersNeedNoCircuitWire() =
        assertTableOf(BeltEnd.WOODEN_CHEST, BeltEnd.STEEL_CHEST, BeltEnd.INFINITY_CHEST) {
            assertEquals(
                emptyList(),
                Layout(basePlan, beltEnd = it).blueprint.wires,
            )
        }
}
