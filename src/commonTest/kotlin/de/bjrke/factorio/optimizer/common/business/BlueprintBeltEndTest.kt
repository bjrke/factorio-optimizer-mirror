package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.blueprint.blueprintLayoutDump
import de.bjrke.factorio.optimizer.common.blueprint.buildBlueprint
import de.bjrke.factorio.optimizer.common.business.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.business.fixtures.gear
import de.bjrke.factorio.optimizer.common.business.fixtures.logistics
import de.bjrke.factorio.optimizer.common.business.fixtures.ore
import de.bjrke.factorio.optimizer.common.business.fixtures.planned
import de.bjrke.factorio.optimizer.common.business.fixtures.recipe
import de.bjrke.factorio.optimizer.common.business.fixtures.water
import de.bjrke.factorio.optimizer.common.layout.ChestEntity
import de.bjrke.factorio.optimizer.common.layout.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.Plumbing
import de.bjrke.factorio.optimizer.common.layout.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.layout.validateLayout
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.BlueprintWire
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.CircuitCondition
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.RequestFilter
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.direction
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The belt-end markers: labels, infinity chests and chests the inserters feed. */
class BlueprintBeltEndTest {

    @Test
    fun infinityChestsAreOptIn() {
        val without = buildBlueprint(basePlan, logistics).entities
        val with = buildBlueprint(basePlan, logistics, beltEnd = BeltEnd.INFINITY_CHEST).entities

        assertAll(
            { assertTrue(without.none { it.name == "infinity-chest" }, "chests by default") },
            {
                assertTrue(
                    with.any { it.name == "infinity-chest" && it.infinity?.item == ore },
                    "no input chest for the imported ore",
                )
            },
            { assertTrue(with.any { it.name.endsWith("-loader") }, "no loader") },
        )
    }

    @Test
    fun aChestMarkerFeedsTheBeltOutOfTheChest() {
        val dump = blueprintLayoutDump(basePlan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest =
            dump.entities.filterIsInstance<ChestEntity>().single {
                it.requestFilter?.name == ore.name
            }
        val row = chest.area.topLeft.y
        val left = chest.area.topLeft.x - 2
        val byTile = dump.entities.associateBy { it.area.topLeft }

        assertAll(
            { assertEquals(BeltEnd.BUFFER_CHEST.identifier, chest.name) },
            {
                assertEquals(
                    RequestFilter(ore.name, 100, trashNotRequested = true),
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
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }

    @Test
    fun theTargetOutputUsesTheChestOutputVariant() {
        val dump = blueprintLayoutDump(basePlan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest =
            dump.entities.filterIsInstance<ChestEntity>().single {
                it.requestFilter?.name == gear.name
            }
        val row = chest.area.topLeft.y
        val left = chest.area.topLeft.x - 2
        val byTile = dump.entities.associateBy { it.area.topLeft }
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
        assertTable(
            listOf(BeltEnd.BUFFER_CHEST to 100, BeltEnd.STORAGE_CHEST to 1),
        ) { (beltEnd, count) ->
            val chest =
                buildBlueprint(basePlan, logistics, beltEnd = beltEnd).entities.single {
                    it.name == beltEnd.identifier && it.requestFilter?.name == ore.name
                }
            assertEquals(
                RequestFilter(ore.name, count, trashNotRequested = beltEnd == BeltEnd.BUFFER_CHEST),
                chest.requestFilter,
            )
        }

    @Test
    fun plainChestMarkersAreBarred() =
        assertTableOf(BeltEnd.WOODEN_CHEST, BeltEnd.STEEL_CHEST) { beltEnd ->
            val chests =
                buildBlueprint(basePlan, logistics, beltEnd = beltEnd).entities.filter {
                    it.name == beltEnd.identifier
                }
            assertAll(
                { assertEquals(2, chests.size, "one input and one output chest") },
                { assertTrue(chests.all { it.bar == 4 }, chests.toString()) },
                { assertTrue(chests.all { it.requestFilter == null }, chests.toString()) },
            )
        }

    @Test
    fun fluidImportsKeepTheCombinatorLabelEvenWithAChestMarker() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(boil, gear, Factory.CHEMICAL_PLANT)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        val dump = blueprintLayoutDump(fluidPlan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val marker = dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.fluid }
        // the label still hides the fluid under a buried run that reaches the lane next to it
        val lane = marker.area.topLeft.east().east()

        assertAll(
            { assertEquals(water, marker.signal.material) },
            { assertEquals(emptyList(), dump.failures) },
            {
                assertTrue(
                    dump.entities.any { it is Plumbing && it.area.topLeft == lane },
                    "the label's buried run does not reach the lane at $lane",
                )
            },
        )
    }

    @Test
    fun theChestOutputWiresBothInsertersToTheChest() {
        val blueprint = buildBlueprint(basePlan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest = blueprint.entities.indexOfFirst { it.requestFilter?.name == gear.name } + 1

        assertEquals(
            listOf(
                BlueprintWire(chest - 2, 1, chest, 1),
                BlueprintWire(chest, 1, chest - 1, 1),
            ),
            blueprint.wires,
        )
    }

    @Test
    fun theChestInputNeedsNoCircuitWire() =
        assertTable(listOf(BeltEnd.BUFFER_CHEST, BeltEnd.STORAGE_CHEST)) { beltEnd ->
            val blueprint = buildBlueprint(basePlan, logistics, beltEnd = beltEnd)
            val inputChest =
                blueprint.entities.indexOfFirst { it.requestFilter?.name == ore.name } + 1
            val wired = blueprint.wires.flatMap { listOf(it.first, it.second) }

            assertContainsNot(wired, inputChest)
        }

    @Test
    fun plainAndCheatMarkersNeedNoCircuitWire() =
        assertTableOf(BeltEnd.WOODEN_CHEST, BeltEnd.STEEL_CHEST, BeltEnd.INFINITY_CHEST) { beltEnd
            ->
            assertEquals(
                emptyList<BlueprintWire>(),
                buildBlueprint(basePlan, logistics, beltEnd).wires,
            )
        }
}
