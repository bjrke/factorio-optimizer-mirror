package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.blueprintPipeFluids
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.gear
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plate
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plumbingComponents
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.water
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.north
import de.bjrke.factorio.optimizer.common.model.south
import de.bjrke.factorio.optimizer.common.model.west
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.service.fixtures.testLogistics
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fluid imports, pipe routing and underground pipe runs. */
class BlueprintFluidLayoutTest {

    @Test
    fun fluidImportMarkersHideTheirPipeUnderTheLabel() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(boil, gear, Factory.CHEMICAL_PLANT), 1)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        val dump = blueprintLayoutDump(fluidPlan, logistics)
        val marker = dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.fluid }
        val input =
            dump.entities.filterIsInstance<PipeToGroundEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            dump.entities.filterIsInstance<PipeToGroundEntity>().single {
                it.area.topLeft == marker.area.topLeft.east()
            }

        assertAll(
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(EAST, input.direction) },
            { assertEquals(EAST, output.direction) },
            { assertEquals(input.material, output.material) },
        )
    }

    @Test
    fun fluidImportMarkerEntranceFacesAwayFromTheLabel() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(boil, gear, Factory.CHEMICAL_PLANT), 1)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        // the pipe-to-ground carries no input/output type, so its direction is the side the
        // surface pipe attaches on: the entrance faces west (away from the label), the exit east
        val blueprint = fromPlaced(fluidPlan, logistics)
        val label =
            blueprint.entities.single {
                it.name == "constant-combinator" && it.markerFluid
            }
        val west = blueprint.entities.single { it.area.topLeft == label.area.topLeft.west() }
        val east = blueprint.entities.single { it.area.topLeft == label.area.topLeft.east() }

        assertAll(
            { assertEquals("pipe-to-ground", west.name) },
            { assertEquals(WEST, west.direction) },
            { assertEquals(EAST, east.direction) },
        )
    }

    @Test
    fun anElectromagneticPlantIsFedOnItsTopPort() {
        val wire =
            recipe("electro", Category.ELECTROMAGNETICS, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow.copies(planned(wire, gear, Factory.ELECTROMAGNETIC_PLANT), 1),
                    ),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "electro",
            )
        val dump = blueprintLayoutDump(fluidPlan, logistics)
        val machine = dump.entities.filterIsInstance<MachineEntity>().single()
        val left = machine.area.topLeft.x
        val top = machine.area.topLeft.y
        // the prototype's top pipe is a BOTH port at (1, -1), which the lane above feeds
        val port = Coordinate(left + 1, top - 1)

        assertAll(
            { assertEquals(emptyList(), dump.failures) },
            {
                assertTrue(
                    dump.entities.any { it is Plumbing && it.area.topLeft == port },
                    "no pipe at the machine's top port $port",
                )
            },
        )
    }

    @Test
    fun theFluidMarkerJoinsTheColumnOfTheSolidImportMarker() {
        val wet =
            recipe(
                "wet-gear",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(plate to 1.0, water to 1.0),
            )
        val plan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(PlannedRow.copies(planned(wet, gear, Factory.ASSEMBLING_MACHINE_3), 1)),
                sources =
                    listOf(
                        PlannedSource(plate, 1.0, fluid = false),
                        PlannedSource(water, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "wet-gear",
            )
        val dump = blueprintLayoutDump(plan, logistics)
        val markers = dump.entities.filterIsInstance<MarkerEntity>()
        val solidImport = markers.single { it.signal.material == plate }
        val fluidMarker = markers.single { it.signal.fluid }
        val machine = dump.entities.filterIsInstance<MachineEntity>().single()
        val laneRow = fluidMarker.area.topLeft.y

        assertAll(
            // the constant combinator sits in the same west column as the solid marker's
            { assertEquals(solidImport.area.topLeft.x, fluidMarker.area.topLeft.x) },
            // the marker's output is joined to the lane head along its own row, underground
            {
                assertTrue(
                    dump.entities.any {
                        it is PipeToGroundEntity &&
                            it.area.topLeft.y == laneRow &&
                            it.area.topLeft.x > fluidMarker.area.topLeft.x &&
                            it.area.topLeft.x < machine.area.topLeft.x
                    },
                    "no underground link between the fluid marker and the lane head",
                )
            },
            { assertEquals(emptyList(), dump.failures) },
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }

    @Test
    fun theCheatFluidSourceIsASingleInfinityPipe() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(boil, gear, Factory.CHEMICAL_PLANT), 1)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        val cheatLogistics =
            LogisticsCalculator(
                testLogistics.copy(fluids = setOf(water), fluidTemperatures = mapOf(water to 15)),
                BeltTier.EXPRESS_TRANSPORT_BELT,
                InserterTier.FAST_INSERTER,
                lanes = 1,
                beltStack = 1,
                inserterStack = 1,
            )
        val entities =
            fromPlaced(fluidPlan, cheatLogistics, beltEnd = BeltEnd.INFINITY_CHEST).entities

        assertAll(
            { assertEquals(1, entities.count { it.name == "infinity-pipe" }) },
            {
                assertTrue(
                    entities.none { it.name == "constant-combinator" && it.markerFluid },
                    "the fluid label should be replaced",
                )
            },
            {
                val pipe = entities.single { it.name == "infinity-pipe" }
                assertEquals(InfinityPipeSettings(water, temperature = 15), pipe.infinityPipe)
            },
        )
    }

    @Test
    fun aFluidSourceBecomesAPipeLane() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(boil, gear, Factory.CHEMICAL_PLANT), 1)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )

        val names = fromPlaced(fluidPlan, logistics).entities.map { it.name }
        assertContains(names, "pipe-to-ground")
    }

    @Test
    fun aFluidTargetGetsAPipeOutputInsteadOfABelt() {
        val crack =
            recipe(
                "lubricant",
                Category.CHEMISTRY,
                mapOf(lubricant to 1.0),
                mapOf(water to 1.0),
            )
        val fluidPlan =
            BlueprintPlan(
                target = lubricant,
                rows =
                    listOf(PlannedRow.copies(planned(crack, lubricant, Factory.CHEMICAL_PLANT), 1)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "lubricant",
            )

        val names = fromPlaced(fluidPlan, logistics).entities.map { it.name }
        assertAll(
            { assertContains(names, "pipe") },
            { assertContainsNot(names, BeltTier.EXPRESS_TRANSPORT_BELT.identifier) },
        )
    }

    @Test
    fun differentFluidLanesDoNotTouch() {
        val coolant =
            recipe(
                "coolant",
                Category.CHEMISTRY,
                mapOf(gear to 1.0),
                mapOf(water to 1.0, lubricant to 1.0),
            )
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(coolant, gear, Factory.CHEMICAL_PLANT), 1)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(lubricant, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "coolant",
            )

        val entities = fromPlaced(fluidPlan, logistics).entities
        val fluids = blueprintPipeFluids(fluidPlan, logistics)
        val components = plumbingComponents(entities)

        // each connected pipe network must carry exactly one fluid, so two fluids never merge
        assertAll(
            { assertEquals(setOf(water, lubricant), fluids.values.toSet()) },
            {
                assertTrue(
                    components.all { component ->
                        component.mapNotNull { fluids[it] }.distinct().size <= 1
                    },
                    "two different fluids share one pipe network",
                )
            },
        )
    }

    @Test
    fun aFluidLaneConnectsToItsMachine() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(boil, gear, Factory.CHEMICAL_PLANT), 1)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        val entities = fromPlaced(fluidPlan, logistics).entities
        val machineTiles =
            entities
                .filter { it.name == Factory.CHEMICAL_PLANT.identifier }
                .flatMapTo(mutableSetOf()) { it.area }
        val components = plumbingComponents(entities)

        fun touchesMachine(tile: Coordinate) =
            listOf(tile.west(), tile.east(), tile.north(), tile.south()).any { it in machineTiles }

        assertAll(
            { assertEquals(1, components.size, "the fluid lane is split") },
            {
                assertTrue(components.single().any(::touchesMachine), "no pipe reaches the machine")
            },
        )
    }

    @Test
    fun fluidRunsUseUndergroundPipes() {
        val coolant =
            recipe(
                "coolant",
                Category.CHEMISTRY,
                mapOf(gear to 1.0),
                mapOf(water to 1.0, lubricant to 1.0),
            )
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(coolant, gear, Factory.CHEMICAL_PLANT), 1)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(lubricant, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "coolant",
            )
        val entities = fromPlaced(fluidPlan, logistics).entities
        val grounds = entities.count { it.name == "pipe-to-ground" }
        val components = plumbingComponents(entities)

        // more pipe-to-ground ends than the two source markers, and one network per fluid
        assertAll(
            { assertTrue(grounds > 2, "no underground pipes were used: $grounds") },
            { assertEquals(2, components.size, "the pipe runs are split") },
        )
    }

    @Test
    fun pipeRunsAreStretched() {
        val coolant =
            recipe(
                "coolant",
                Category.CHEMISTRY,
                mapOf(gear to 1.0),
                mapOf(water to 1.0, lubricant to 1.0),
            )
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(coolant, gear, Factory.CHEMICAL_PLANT), 1)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(lubricant, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "coolant",
            )
        assertUndergroundRunsAreStretched(blueprintLayoutDump(fluidPlan, logistics).entities)
    }
}
