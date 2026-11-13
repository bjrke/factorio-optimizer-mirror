package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.plumbingComponents
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.core.model.north
import de.bjrke.factorio.optimizer.common.core.model.south
import de.bjrke.factorio.optimizer.common.core.model.west
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.fixtures.steam
import de.bjrke.factorio.optimizer.common.fixtures.steamCondensationRecipe
import de.bjrke.factorio.optimizer.common.fixtures.sulfur
import de.bjrke.factorio.optimizer.common.fixtures.sulfurRecipe
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcidRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fluid imports, pipe routing and underground pipe runs. */
class BlueprintFluidLayoutTest {

    @Test
    fun fluidImportMarkersHideTheirPipeUnderTheLabel() {
        val fluidPlan =
            BlueprintPlan(
                target = solidFuel,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(
                                solidFuelFromPetroleumGasRecipe,
                                Factory.CHEMICAL_PLANT,
                            ),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(petroleum, 1.0, fluid = true)),
                label = "solid-fuel-from-petroleum-gas",
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
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(EAST, input.direction) },
            { assertEquals(EAST, output.direction) },
            { assertEquals(input.material, output.material) },
        )
    }

    @Test
    fun fluidImportMarkerEntranceFacesAwayFromTheLabel() {
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
                label = "solid-fuel-from-petroleum-gas",
            )
        // the pipe-to-ground carries no input/output type, so its direction is the side the
        // surface pipe attaches on: the entrance faces west (away from the label), the exit east
        val layout = Layout(fluidPlan)
        val blueprint = layout.blueprint
        val label =
            blueprint.entities.single {
                it.name == "constant-combinator" && it.markerFluid
            }
        val west = blueprint.entities.single { it.area.topLeft == label.area.topLeft.west() }
        val east = blueprint.entities.single { it.area.topLeft == label.area.topLeft.east() }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals("pipe-to-ground", west.name) },
            { assertEquals(WEST, west.direction) },
            { assertEquals(EAST, east.direction) },
        )
    }

    @Test
    fun anElectromagneticPlantIsFedOnItsTopPort() {
        val fluidPlan =
            BlueprintPlan(
                target = processingUnit,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(processingUnitRecipe, Factory.ELECTROMAGNETIC_PLANT),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(electronicCircuit, 1.0, fluid = false),
                        PlannedSource(advancedCircuit, 1.0, fluid = false),
                        PlannedSource(sulfuricAcid, 1.0, fluid = true),
                    ),
                label = "processing-unit",
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
                assertNotNull(
                    layout.entities.firstOrNull { it is Plumbing && it.area.topLeft == port },
                    "no pipe at the machine's top port $port",
                )
            },
        )
    }

    @Test
    fun theFluidMarkerJoinsTheColumnOfTheSolidImportMarker() {
        val plan =
            BlueprintPlan(
                target = plasticBar,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(plasticBarRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(coal, 1.0, fluid = false),
                        PlannedSource(petroleum, 1.0, fluid = true),
                    ),
                label = "plastic-bar",
            )
        val layout = Layout(plan)
        val markers = layout.entities.filterIsInstance<MarkerEntity>()
        val solidImport = markers.single { it.signal.material == coal }
        val fluidMarker = markers.single { it.signal.fluid }
        val machine = layout.entities.filterIsInstance<MachineEntity>().single()
        val laneRow = fluidMarker.area.topLeft.y

        assertAll(
            // the constant combinator sits in the same west column as the solid marker's
            { assertEquals(solidImport.area.topLeft.x, fluidMarker.area.topLeft.x) },
            // the marker's output is joined to the lane head along its own row, underground
            {
                assertNotNull(
                    layout.entities.firstOrNull {
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
        val fluidPlan =
            BlueprintPlan(
                target = solidFuel,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(
                                solidFuelFromPetroleumGasRecipe,
                                Factory.CHEMICAL_PLANT,
                            ),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(petroleum, 1.0, fluid = true)),
                label = "solid-fuel-from-petroleum-gas",
            )
        val cheatLogistics =
            LogisticsCalculator(
                testLogistics.copy(
                    fluids = setOf(petroleum),
                    fluidTemperatures = mapOf(petroleum to 15),
                ),
            )
        val layout = Layout(fluidPlan, cheatLogistics, beltEnd = BeltEnd.INFINITY_CHEST)
        val entities = layout.blueprint.entities

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(1, entities.count { it.name == "infinity-pipe" }) },
            {
                assertEquals(
                    emptyList(),
                    entities.filter { it.name == "constant-combinator" && it.markerFluid },
                    "the fluid label should be replaced",
                )
            },
            {
                val pipe = entities.single { it.name == "infinity-pipe" }
                assertEquals(
                    InfinityPipeSettings(petroleum, temperature = 15),
                    pipe.infinityPipe,
                )
            },
        )
    }

    @Test
    fun aFluidSourceBecomesAPipeLane() {
        val fluidPlan =
            BlueprintPlan(
                target = solidFuel,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(
                                solidFuelFromPetroleumGasRecipe,
                                Factory.CHEMICAL_PLANT,
                            ),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(petroleum, 1.0, fluid = true)),
                label = "solid-fuel-from-petroleum-gas",
            )

        val layout = Layout(fluidPlan)
        val names = layout.blueprint.entities.map { it.name }
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertContains(names, "pipe-to-ground") },
        )
    }

    @Test
    fun aFluidTargetGetsAPipeOutputInsteadOfABelt() {
        val fluidPlan =
            BlueprintPlan(
                target = water,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(steamCondensationRecipe, Factory.CHEMICAL_PLANT),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(steam, 1.0, fluid = true)),
                label = "steam-condensation",
            )

        val fluids = LogisticsCalculator(testLogistics.copy(fluids = setOf(steam, water)))
        val layout = Layout(fluidPlan, fluids)
        val names = layout.blueprint.entities.map { it.name }
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertContains(names, "pipe") },
            { assertContainsNot(names, BeltTier.EXPRESS_TRANSPORT_BELT.identifier) },
        )
    }

    @Test
    fun theCheatFluidOutputIsAnUnfilteredInfinityPipe() {
        val fluidPlan =
            BlueprintPlan(
                target = water,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(steamCondensationRecipe, Factory.CHEMICAL_PLANT),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(steam, 1.0, fluid = true)),
                label = "steam-condensation",
            )
        val fluids = LogisticsCalculator(testLogistics.copy(fluids = setOf(steam, water)))
        val layout = Layout(fluidPlan, fluids, beltEnd = BeltEnd.INFINITY_CHEST)
        val pipes = layout.entities.filterIsInstance<InfinityPipeEntity>()

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // the steam import is a filtered source, the water output an unfiltered sink
            { assertEquals(2, pipes.size) },
            { assertEquals(setOf(steam), pipes.mapNotNull { it.settings?.fluid }.toSet()) },
            { assertEquals(water, pipes.single { it.settings == null }.material) },
        )
    }

    @Test
    fun aSinkMarkerStaysUnfilteredWhenTheTrimmerMovesIt() {
        val plan =
            BlueprintPlan(
                target = sulfuricAcid,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(sulfuricAcidRecipe, Factory.CHEMICAL_PLANT),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(sulfur, 1.0, fluid = false),
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(water, 1.0, fluid = true),
                    ),
                label = "sulfuric-acid",
            )
        val layout = Layout(plan, beltEnd = BeltEnd.INFINITY_CHEST)
        val sink =
            layout.entities.filterIsInstance<InfinityPipeEntity>().single {
                it.material == sulfuricAcid
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
        val fluidPlan =
            BlueprintPlan(
                target = sulfur,
                rows = listOf(PlannedRow.copies(planned(sulfurRecipe, Factory.CHEMICAL_PLANT), 1)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(petroleum, 1.0, fluid = true),
                    ),
                label = "sulfur",
            )

        val layout = Layout(fluidPlan)
        val entities = layout.blueprint.entities
        val components = plumbingComponents(entities)

        // each connected pipe network must carry exactly one fluid, so two fluids never merge
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(setOf(water, petroleum), layout.plumbing.values.toSet()) },
            {
                assertEquals(
                    emptyList(),
                    components.filterNot { component ->
                        component.mapNotNull { layout.plumbing[it] }.distinct().size <= 1
                    },
                    "two different fluids share one pipe network",
                )
            },
        )
    }

    @Test
    fun aSecondFluidOnTheNorthEdgeGetsItsOwnLaneBesideTheFirst() {
        val fluidPlan =
            BlueprintPlan(
                target = sulfur,
                rows = listOf(PlannedRow.copies(planned(sulfurRecipe, Factory.CHEMICAL_PLANT), 1)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(petroleum, 1.0, fluid = true),
                    ),
                label = "sulfur",
            )
        val layout = Layout(fluidPlan)
        val machines = layout.entities.filterIsInstance<MachineEntity>()
        val machineTop = machines.minOf { it.area.topLeft.y }
        val machineLeft = machines.minOf { it.area.topLeft.x }
        val fluidMarkers =
            layout.entities.filterIsInstance<MarkerEntity>().filter { it.signal.fluid }
        val waterMarker = fluidMarkers.single { it.signal.material == water }
        val secondMarker = fluidMarkers.single { it.signal.material == petroleum }
        val secondPipes =
            layout.entities.filterIsInstance<Plumbing>().filter { it.material == petroleum }

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
                assertNotNull(
                    secondPipes.firstOrNull {
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
                label = "solid-fuel-from-petroleum-gas",
            )
        val layout = Layout(fluidPlan)
        val entities = layout.blueprint.entities
        val machineTiles =
            entities
                .filter { it.name == Factory.CHEMICAL_PLANT.identifier }
                .flatMapTo(mutableSetOf()) { it.area }
        val components = plumbingComponents(entities)

        fun touchesMachine(tile: Coordinate) =
            listOf(tile.west(), tile.east(), tile.north(), tile.south()).any { it in machineTiles }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(1, components.size, "the fluid lane is split") },
            {
                assertNotNull(
                    components.single().firstOrNull(::touchesMachine),
                    "no pipe reaches the machine",
                )
            },
        )
    }

    @Test
    fun fluidRunsUseUndergroundPipes() {
        val fluidPlan =
            BlueprintPlan(
                target = sulfur,
                rows = listOf(PlannedRow.copies(planned(sulfurRecipe, Factory.CHEMICAL_PLANT), 1)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(petroleum, 1.0, fluid = true),
                    ),
                label = "sulfur",
            )
        val layout = Layout(fluidPlan)
        val entities = layout.blueprint.entities
        val grounds = entities.count { it.name == "pipe-to-ground" }
        val components = plumbingComponents(entities)

        // more pipe-to-ground ends than the two source markers, and one network per fluid
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertTrue(grounds > 2, "no underground pipes were used: $grounds") },
            { assertEquals(2, components.size, "the pipe runs are split") },
        )
    }

    @Test
    fun pipeRunsAreStretched() {
        val fluidPlan =
            BlueprintPlan(
                target = sulfur,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(sulfurRecipe, Factory.CHEMICAL_PLANT),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(petroleum, 1.0, fluid = true),
                    ),
                label = "sulfur",
            )
        val layout = Layout(fluidPlan)
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertUndergroundRunsAreStretched(
                    layout.entities,
                    layout.logistics.undergroundRange,
                )
            },
        )
    }
}
