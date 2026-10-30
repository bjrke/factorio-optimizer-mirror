package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plumbingComponents
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
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
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        val layout = Layout(fluidPlan)
        val marker = layout.entities.filterIsInstance<MarkerEntity>().single { it.signal.fluid }
        val input =
            layout.entities.filterIsInstance<PipeToGroundEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            layout.entities.filterIsInstance<PipeToGroundEntity>().single {
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
        val blueprint = Layout(fluidPlan).blueprint
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
        val layout = Layout(fluidPlan)
        val machine = layout.entities.filterIsInstance<MachineEntity>().single()
        val left = machine.area.topLeft.x
        val top = machine.area.topLeft.y
        // the prototype's top pipe is a BOTH port at (1, -1), which the lane above feeds
        val port = Coordinate(left + 1, top - 1)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertTrue(
                    layout.entities.any { it is Plumbing && it.area.topLeft == port },
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
                mapOf(ironPlate to 1.0, water to 1.0),
            )
        val plan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(PlannedRow.copies(planned(wet, gear, Factory.ASSEMBLING_MACHINE_3), 1)),
                sources =
                    listOf(
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(water, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "wet-gear",
            )
        val layout = Layout(plan)
        val markers = layout.entities.filterIsInstance<MarkerEntity>()
        val solidImport = markers.single { it.signal.material == ironPlate }
        val fluidMarker = markers.single { it.signal.fluid }
        val machine = layout.entities.filterIsInstance<MachineEntity>().single()
        val laneRow = fluidMarker.area.topLeft.y

        assertAll(
            // the constant combinator sits in the same west column as the solid marker's
            { assertEquals(solidImport.area.topLeft.x, fluidMarker.area.topLeft.x) },
            // the marker's output is joined to the lane head along its own row, underground
            {
                assertTrue(
                    layout.entities.any {
                        it is PipeToGroundEntity &&
                            it.area.topLeft.y == laneRow &&
                            it.area.topLeft.x > fluidMarker.area.topLeft.x &&
                            it.area.topLeft.x < machine.area.topLeft.x
                    },
                    "no underground link between the fluid marker and the lane head",
                )
            },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
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
            )
        val entities =
            Layout(fluidPlan, cheatLogistics, beltEnd = BeltEnd.INFINITY_CHEST).blueprint.entities

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

        val names = Layout(fluidPlan).blueprint.entities.map { it.name }
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

        val names = Layout(fluidPlan).blueprint.entities.map { it.name }
        assertAll(
            { assertContains(names, "pipe") },
            { assertContainsNot(names, BeltTier.EXPRESS_TRANSPORT_BELT.identifier) },
        )
    }

    @Test
    fun theCheatFluidOutputIsAnUnfilteredInfinityPipe() {
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
        val pipes =
            Layout(fluidPlan, beltEnd = BeltEnd.INFINITY_CHEST)
                .entities
                .filterIsInstance<InfinityPipeEntity>()

        assertAll(
            // the water import is a filtered source, the lubricant output an unfiltered sink
            { assertEquals(2, pipes.size) },
            { assertEquals(setOf(water), pipes.mapNotNull { it.settings?.fluid }.toSet()) },
            { assertEquals(lubricant, pipes.single { it.settings == null }.material) },
        )
    }

    @Test
    fun aSinkMarkerStaysUnfilteredWhenTheTrimmerMovesIt() {
        val drain =
            recipe(
                "drain",
                Category.CHEMISTRY,
                mapOf(lubricant to 1.0),
                mapOf(ironPlate to 1.0, water to 1.0),
            )
        val plan =
            BlueprintPlan(
                target = lubricant,
                rows =
                    listOf(PlannedRow.copies(planned(drain, lubricant, Factory.CHEMICAL_PLANT), 1)),
                sources =
                    listOf(
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(water, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "drain",
            )
        val layout = Layout(plan, beltEnd = BeltEnd.INFINITY_CHEST)
        val sink =
            layout.entities.filterIsInstance<InfinityPipeEntity>().single {
                it.material == lubricant
            }
        val solidImport =
            layout.entities.filterIsInstance<InfinityChestEntity>().single {
                it.settings.item == ironPlate
            }

        assertAll(
            // the trimmer relocates the sink marker but must keep it unfiltered
            { assertNull(sink.settings) },
            // it joins the same west column as the solid import
            { assertEquals(solidImport.area.topLeft.x, sink.area.topLeft.x) },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
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

        val layout = Layout(fluidPlan)
        val entities = layout.blueprint.entities
        val components = plumbingComponents(entities)

        // each connected pipe network must carry exactly one fluid, so two fluids never merge
        assertAll(
            { assertEquals(setOf(water, lubricant), layout.plumbing.values.toSet()) },
            {
                assertTrue(
                    components.all { component ->
                        component.mapNotNull { layout.plumbing[it] }.distinct().size <= 1
                    },
                    "two different fluids share one pipe network",
                )
            },
        )
    }

    @Test
    fun aSecondFluidOnTheNorthEdgeGetsItsOwnLaneBesideTheFirst() {
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
        val layout = Layout(fluidPlan)
        val machines = layout.entities.filterIsInstance<MachineEntity>()
        val machineTop = machines.minOf { it.area.topLeft.y }
        val machineLeft = machines.minOf { it.area.topLeft.x }
        val fluidMarkers =
            layout.entities.filterIsInstance<MarkerEntity>().filter { it.signal.fluid }
        val waterMarker = fluidMarkers.single { it.signal.material == water }
        val secondMarker = fluidMarkers.single { it.signal.material == lubricant }
        val secondPipes =
            layout.entities.filterIsInstance<Plumbing>().filter { it.material == lubricant }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            // both lane markers sit in one west column, the second lane one row further out ...
            { assertEquals(waterMarker.area.topLeft.x, secondMarker.area.topLeft.x) },
            { assertTrue(secondMarker.area.topLeft.y < waterMarker.area.topLeft.y) },
            // ... with a junction above the machine's second top corner and a shaft down to it
            {
                assertTrue(
                    secondPipes.any {
                        it is PipeEntity &&
                            it.area.topLeft ==
                                Coordinate(machineLeft + 2, secondMarker.area.topLeft.y)
                    },
                    "the second fluid got no lane junction",
                )
            },
            {
                assertTrue(
                    secondPipes.any {
                        it is PipeToGroundEntity &&
                            it.area.topLeft == Coordinate(machineLeft + 2, machineTop - 1)
                    },
                    "the second fluid got no shaft onto its port",
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
        val entities = Layout(fluidPlan).blueprint.entities
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
        val entities = Layout(fluidPlan).blueprint.entities
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
        assertUndergroundRunsAreStretched(Layout(fluidPlan).entities)
    }
}
