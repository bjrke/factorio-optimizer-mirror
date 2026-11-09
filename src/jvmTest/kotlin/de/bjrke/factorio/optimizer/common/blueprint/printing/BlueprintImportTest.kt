package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.core.model.west
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.explosives
import de.bjrke.factorio.optimizer.common.fixtures.explosivesRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.sulfur
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Import markers, their trimmed belt prefixes and the pins they leave for the rest of the layout.
 */
class BlueprintImportTest {

    @Test
    fun solidImportMarkersHideTheirBeltUnderTheLabel() {
        val layout = Layout(basePlan)
        val marker =
            layout.entities.filterIsInstance<MarkerEntity>().single {
                it.signal.material == ironOre
            }
        val input =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().single {
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
    fun targetOutputHidesItsBeltUnderTheLabel() {
        val layout = Layout(basePlan)
        val marker =
            layout.entities.filterIsInstance<MarkerEntity>().single { it.signal.material == gear }
        val input =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.east()
            }

        assertAll(
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(EAST, input.direction) },
            { assertEquals(EAST, output.direction) },
            { assertEquals(gear, input.material) },
            { assertEquals(input.material, output.material) },
        )
    }

    @Test
    fun polesStayEastOfTheImportExtension() {
        val layout = Layout(basePlan)
        val marker =
            layout.entities.filterIsInstance<MarkerEntity>().single {
                it.signal.material == ironOre
            }
        val poles = layout.entities.filterIsInstance<PoleEntity>()

        assertAll(
            { assertTrue(poles.isNotEmpty(), "no poles were placed") },
            {
                assertEquals(
                    emptyList(),
                    poles.filterNot { it.area.topLeft.x >= marker.area.topLeft.x + 2 },
                    "a pole sits in the import extension: $poles",
                )
            },
        )
    }

    @Test
    fun importMarkersShareOneColumnAfterThePrefixIsTrimmed() {
        val mixedPlan =
            BlueprintPlan(
                target = explosives,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(explosivesRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(sulfur, 1.0, fluid = false),
                        PlannedSource(coal, 1.0, fluid = false),
                    ),
                label = "explosives",
            )
        val layout = Layout(mixedPlan)
        val markers =
            layout.entities.filterIsInstance<MarkerEntity>().filter {
                it.signal.material == sulfur || it.signal.material == coal
            }
        val machine =
            layout.entities.filterIsInstance<MachineEntity>().single {
                it.recipe == "explosives"
            }

        assertAll(
            { assertEquals(2, markers.size, "one marker per imported input") },
            {
                assertEquals(
                    emptyList(),
                    markers.map { it.area.topLeft.x }.distinct().drop(1),
                    "import markers share one column",
                )
            },
            {
                assertEquals(
                    emptyList(),
                    markers.filterNot { it.area.topLeft.x < machine.area.topLeft.x },
                )
            },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    /**
     * The advanced-circuit row feeds its third solid input through the lane below the machine
     * (`sinkOf`'s `NORTH` sink). The connector, its feed tile and the first lane tile must stay
     * adjacent, so a vertical corridor cannot slip between them and leave the material on a
     * different lane tile than the route filled.
     */
    @Test
    fun theImportConnectorKeepsTheLaneTileItFeeds() {
        val plan =
            BlueprintPlan(
                target = advancedCircuit,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(
                                advancedCircuitRecipe,
                                Factory.ASSEMBLING_MACHINE_3,
                            ),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(copperCable, 1.0, fluid = false),
                        PlannedSource(electronicCircuit, 1.0, fluid = false),
                        PlannedSource(plasticBar, 1.0, fluid = false),
                    ),
                label = "advanced-circuit",
            )
        val layout = Layout(plan)
        val machine =
            layout.entities.filterIsInstance<MachineEntity>().single {
                it.recipe == "advanced-circuit"
            }
        val laneRow = machine.area.topLeft.y - 3
        val lane =
            layout.entities.filterIsInstance<BeltEntity>().filter {
                it.material == plasticBar && it.area.topLeft.y == laneRow
            }
        val marker =
            layout.entities.filterIsInstance<MarkerEntity>().single {
                it.signal.material == plasticBar
            }
        // the connector's own tiles sit between the label column and the feed, so the lane must run
        // unbroken from the feed tile on: a hole would mean compaction cut a belt out of it
        val byColumn = lane.map { it.area.topLeft.x }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            {
                assertEquals(
                    byColumn.max() - byColumn.min() + 1,
                    byColumn.size,
                    "the lane runs unbroken from the connector to its end",
                )
            },
            {
                assertEquals(
                    laneRow,
                    marker.area.topLeft.y,
                    "the marker row is the lane row: ${marker.area.topLeft}",
                )
            },
        )
    }

    @Test
    fun aProducedLaneIsFedFacingIntoTheLane() {
        val produced =
            BlueprintPlan(
                target = electronicCircuit,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(electronicCircuitRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                        PlannedRow.copies(
                            planned(copperCableRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(copperPlate, 1.0, fluid = false),
                        PlannedSource(ironPlate, 1.0, fluid = false),
                    ),
                label = "electronic-circuit",
            )

        val entities = Layout(produced).entities
        val machineArea =
            entities
                .filterIsInstance<MachineEntity>()
                .single { it.recipe == "electronic-circuit" }
                .area
        val laneRow = machineArea.topLeft.y - 2

        // a produced lane runs west into the machine; compaction may shorten it, so take the
        // eastmost remaining lane belt rather than a fixed coordinate
        val feed =
            assertNotNull(
                entities
                    .filterIsInstance<BeltEntity>()
                    .filter { it.material == copperCable && it.area.topLeft.y == laneRow }
                    .maxByOrNull { it.area.topLeft.x },
                "no feed at the produced lane",
            )
        assertEquals(WEST, feed.direction, "the feed does not face the lane")
    }
}
