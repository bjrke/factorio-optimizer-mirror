package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.blueprint.blueprintLayoutDump
import de.bjrke.factorio.optimizer.common.blueprint.validateLayout
import de.bjrke.factorio.optimizer.common.business.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.business.fixtures.coal
import de.bjrke.factorio.optimizer.common.business.fixtures.gear
import de.bjrke.factorio.optimizer.common.business.fixtures.logistics
import de.bjrke.factorio.optimizer.common.business.fixtures.ore
import de.bjrke.factorio.optimizer.common.business.fixtures.planned
import de.bjrke.factorio.optimizer.common.business.fixtures.recipe
import de.bjrke.factorio.optimizer.common.layout.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.PoleEntity
import de.bjrke.factorio.optimizer.common.layout.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.direction
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.west
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
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
        val dump = blueprintLayoutDump(basePlan, logistics)
        val marker =
            dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.material == ore }
        val input =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
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
        val dump = blueprintLayoutDump(basePlan, logistics)
        val marker =
            dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.material == gear }
        val input =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
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
        val dump = blueprintLayoutDump(basePlan, logistics)
        val marker =
            dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.material == ore }
        val poles = dump.entities.filterIsInstance<PoleEntity>()

        assertAll(
            { assertTrue(poles.isNotEmpty(), "no poles were placed") },
            {
                assertTrue(
                    poles.all { it.area.topLeft.x >= marker.area.topLeft.x + 2 },
                    "a pole sits in the import extension: $poles",
                )
            },
        )
    }

    @Test
    fun importMarkersShareOneColumnAfterThePrefixIsTrimmed() {
        val twoImports =
            recipe(
                "gear",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ore to 1.0, coal to 1.0),
            )
        val mixedPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(twoImports, gear, Factory.ASSEMBLING_MACHINE_3)),
                sources =
                    listOf(
                        PlannedSource(ore, 1.0, fluid = false),
                        PlannedSource(coal, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "gear",
            )
        val dump = blueprintLayoutDump(mixedPlan, logistics)
        val markers =
            dump.entities.filterIsInstance<MarkerEntity>().filter {
                it.signal.material == ore || it.signal.material == coal
            }
        val machine = dump.entities.filterIsInstance<MachineEntity>().single { it.recipe == "gear" }

        assertAll(
            { assertEquals(2, markers.size, "one marker per imported input") },
            {
                assertEquals(
                    emptyList(),
                    markers.map { it.area.topLeft.x }.distinct().drop(1),
                    "import markers share one column",
                )
            },
            { assertTrue(markers.all { it.area.topLeft.x < machine.area.topLeft.x }) },
            { assertEquals(emptyList(), dump.failures) },
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }

    @Test
    fun aProducedLaneIsFedFacingIntoTheLane() {
        val cable = Material("copper-cable")
        val top = Material("top")
        val makeCable =
            recipe("copper-cable", Category.CRAFTING, mapOf(cable to 1.0), mapOf(ore to 1.0))
        val makeTop = recipe("top", Category.CRAFTING, mapOf(top to 1.0), mapOf(cable to 1.0))
        val produced =
            BlueprintPlan(
                target = top,
                recipes =
                    listOf(
                        planned(makeTop, top, Factory.ASSEMBLING_MACHINE_3),
                        planned(makeCable, cable, Factory.ASSEMBLING_MACHINE_3),
                    ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                scale = 1.0,
                label = "top",
            )

        val entities = blueprintLayoutDump(produced, logistics).entities
        val machineArea =
            entities.filterIsInstance<MachineEntity>().single { it.recipe == "top" }.area
        val laneRow = machineArea.topLeft.y - 2

        // a produced lane runs west into the machine; compaction may shorten it, so take the
        // eastmost remaining lane belt rather than a fixed coordinate
        val feed =
            assertNotNull(
                entities
                    .filterIsInstance<BeltEntity>()
                    .filter { it.material == cable && it.area.topLeft.y == laneRow }
                    .maxByOrNull { it.area.topLeft.x },
                "no feed at the produced lane",
            )
        assertEquals(WEST, feed.direction, "the feed does not face the lane")
    }
}
