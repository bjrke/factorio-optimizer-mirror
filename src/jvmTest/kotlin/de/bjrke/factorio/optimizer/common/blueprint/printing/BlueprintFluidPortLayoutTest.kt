package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LANE_MARGIN
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.fixtures.electrolyte
import de.bjrke.factorio.optimizer.common.fixtures.heavyOil
import de.bjrke.factorio.optimizer.common.fixtures.holmiumSolution
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.sulfur
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four reported fluid layouts, replayed from their web-UI URLs. Each case pins the machine
 * rotation and the fluid at the machine's real pipe connections, so a port-table or lane change
 * cannot silently move a fluid to another port.
 */
class BlueprintFluidPortLayoutTest {

    @Test
    fun theReportedLayoutsKeepEveryFluidRouted() =
        assertTableOf(
            "http://localhost:8080/#/0.03511777708/plastic-bar/CFSXmr/1/1",
            "http://localhost:8080/#/0.03511777708/sulfuric-acid/FSXmr/1/1",
            "http://localhost:8080/#/0.03511777708/light-oil/FSXmor/1/1",
            "http://localhost:8080/#/6/electrolyte/FSXmor/1/1",
        ) { url ->
            assertEquals(emptyList(), layoutFromUrl(url).failures)
        }

    @Test
    fun thePlasticCryogenicPlantIsTurnedAround() {
        val layout = layoutFromUrl("http://localhost:8080/#/0.03511777708/plastic-bar/CFSXmr/1/1")
        val machine = layout.machine("cryogenic-plant")
        val north = machine.area.topLeft + Coordinate(2, -1)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(Direction.SOUTH, layout.direction(machine)) },
            { assertEquals(petroleum, layout.plumbing[north]) },
        )
    }

    @Test
    fun theSulfuricChemicalPlantFeedsItsSolidInputs() {
        // sulfur is direct-fed in the band; the acid machine still takes iron plate from the row
        // above, and neither solid inserter blocks the chemical plant's fluid ports
        val layout = layoutFromUrl("http://localhost:8080/#/0.03511777708/sulfuric-acid/FSXmr/1/1")
        val inserters = layout.entities.filterIsInstance<InserterEntity>()

        assertAll(
            { assertEquals(setOf("fast-inserter"), inserters.map { it.name }.toSet()) },
            {
                assertEquals(
                    mapOf(
                        Coordinate(LANE_MARGIN + 3, 2) to sulfur,
                        Coordinate(LANE_MARGIN + 5, 1) to ironPlate,
                    ),
                    inserters.associateBy({ it.area.topLeft }, { it.material }),
                )
            },
            { assertEquals(emptyList(), layout.failures) },
        )
    }

    @Test
    fun theLightOilChemicalPlantFeedsWaterFromTheNorthLane() {
        val layout = layoutFromUrl("http://localhost:8080/#/0.03511777708/light-oil/FSXmor/1/1")
        val machine = layout.machine("chemical-plant")
        val left = machine.area.topLeft + Coordinate(0, -1)
        val right = machine.area.topLeft + Coordinate(2, -1)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(water, layout.plumbing[left]) },
            { assertEquals(heavyOil, layout.plumbing[right]) },
        )
    }

    /**
     * Heavy oil is the chemical plant's second top corner. It gets a lane like the water import,
     * stacked one row further out with its marker in the same west column, instead of a routed feed
     * that loops around the row.
     */
    @Test
    fun theLightOilHeavyOilLaneMirrorsTheWaterLane() {
        val layout = layoutFromUrl("http://localhost:8080/#/0.03511777708/light-oil/FSXmor/1/1")
        val machine = layout.machine("chemical-plant")
        val left = machine.area.topLeft.x
        val top = machine.area.topLeft.y
        val marker =
            layout.entities.filterIsInstance<InfinityPipeEntity>().single {
                it.material == heavyOil
            }
        val waterMarker =
            layout.entities.filterIsInstance<InfinityPipeEntity>().single { it.material == water }
        val markerRow = marker.area.topLeft.y
        val pipes = layout.entities.filterIsInstance<Plumbing>().filter { it.material == heavyOil }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // the same west column as the water marker, one lane further out
            { assertEquals(waterMarker.area.topLeft.x, marker.area.topLeft.x) },
            { assertTrue(markerRow < waterMarker.area.topLeft.y) },
            // a junction pipe above the second top corner and a shaft down onto the port
            {
                assertNotNull(
                    pipes.firstOrNull {
                        it is PipeEntity && it.area.topLeft == Coordinate(left + 2, markerRow)
                    },
                    "no heavy-oil junction above the second top corner",
                )
            },
            {
                assertNotNull(
                    pipes.firstOrNull {
                        it is PipeToGroundEntity && it.area.topLeft == Coordinate(left + 2, top - 1)
                    },
                    "no heavy-oil shaft on the second top corner",
                )
            },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun theLightOilLaneReachesEverySecondTopCorner() {
        val layout = layoutFromUrl("http://localhost:8080/#/60/light-oil/FSXmor/1/1")
        val markers = layout.entities.filterIsInstance<InfinityPipeEntity>()
        val heavyMarker = markers.single { it.material == heavyOil }
        val waterMarker = markers.single { it.material == water }
        val heavy = layout.entities.filterIsInstance<Plumbing>().filter { it.material == heavyOil }
        val secondPorts =
            layout.entities.filterIsInstance<MachineEntity>().map {
                it.area.topLeft + Coordinate(2, -1)
            }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // one west column for both lane markers, the heavy lane further out
            { assertEquals(waterMarker.area.topLeft.x, heavyMarker.area.topLeft.x) },
            { assertTrue(heavyMarker.area.topLeft.y < waterMarker.area.topLeft.y) },
            // every machine's second top corner is reached by the lane, not by a routed pipe
            {
                assertEquals(
                    emptyList(),
                    secondPorts.filterNot { port -> heavy.any { it.area.topLeft == port } },
                    "a second top corner has no heavy-oil pipe",
                )
            },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun theElectrolyteElectromagneticPlantTurnsClockwiseAndSwapsItsPipes() {
        val layout = layoutFromUrl("http://localhost:8080/#/6/electrolyte/FSXmor/1/1")
        val machine = layout.machine("electromagnetic-plant")
        val topLeft = machine.area.topLeft

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(Direction.EAST, layout.direction(machine)) },
            { assertEquals(heavyOil, layout.plumbing[topLeft + Coordinate(1, -1)]) },
            { assertEquals(holmiumSolution, layout.plumbing[topLeft + Coordinate(2, 4)]) },
            { assertEquals(electrolyte, layout.plumbing[topLeft + Coordinate(-1, 2)]) },
            { assertNull(layout.plumbing[topLeft + Coordinate(4, 1)]) },
        )
    }

    @Test
    fun theElectrolyteOutputKeepsItsWestSource() {
        val layout = layoutFromUrl("http://localhost:8080/#/6/electrolyte/FSXmor/1/1")
        val machine = layout.machine("electromagnetic-plant")
        val outputRow = machine.area.topLeft.y + 2
        val sources =
            layout.entities.filterIsInstance<InfinityPipeEntity>().filter {
                it.material == electrolyte &&
                    it.area.topLeft.y == outputRow &&
                    it.area.topLeft.x < machine.area.topLeft.x
            }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(1, sources.size, "the electrolyte output lost its west infinity pipe") },
        )
    }
}
