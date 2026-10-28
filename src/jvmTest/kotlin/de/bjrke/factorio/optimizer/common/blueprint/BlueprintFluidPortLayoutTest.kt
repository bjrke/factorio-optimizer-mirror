package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planFromUrl
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four reported fluid layouts, replayed from their web-UI URLs. Each case pins the machine
 * rotation and the fluid at the machine's real pipe connections, so a port-table or lane change
 * cannot silently move a fluid to another port.
 */
class BlueprintFluidPortLayoutTest {

    private class Layout(url: String) {
        private val args = planFromUrl(url)
        private val dump = blueprintLayoutDump(args.first, args.second, args.third)
        val entities: List<LayoutEntity> = dump.entities
        val failures: List<String> = dump.failures
        val blueprint = fromPlaced(args.first, args.second, args.third)

        fun machine(name: String): MachineEntity =
            entities.filterIsInstance<MachineEntity>().single {
                it.name == name
            }

        fun plumbingAt(tile: Coordinate): Material? =
            entities.filterIsInstance<Plumbing>().singleOrNull { it.area.topLeft == tile }?.material

        fun direction(machine: MachineEntity): Direction? =
            blueprint.entities
                .single {
                    it.area.topLeft == machine.area.topLeft && it.name == machine.name
                }
                .direction

        fun insertersAbove(machine: MachineEntity): List<InserterEntity> =
            entities.filterIsInstance<InserterEntity>().filter {
                it.area.topLeft.y == machine.area.topLeft.y - 1 &&
                    it.area.topLeft.x in machine.area.xRange
            }
    }

    @Test
    fun theReportedLayoutsKeepEveryFluidRouted() =
        assertTableOf(
            "http://localhost:8080/#/0.03511777708/plastic-bar/CFSXmr/1/1",
            "http://localhost:8080/#/0.03511777708/sulfuric-acid/FSXmr/1/1",
            "http://localhost:8080/#/0.03511777708/light-oil/FSXmor/1/1",
            "http://localhost:8080/#/6/electrolyte/FSXmor/1/1",
        ) { url ->
            assertEquals(emptyList(), Layout(url).failures)
        }

    @Test
    fun thePlasticCryogenicPlantIsTurnedAround() {
        val layout = Layout("http://localhost:8080/#/0.03511777708/plastic-bar/CFSXmr/1/1")
        val machine = layout.machine("cryogenic-plant")
        val north = machine.area.topLeft + Coordinate(2, -1)

        assertAll(
            { assertEquals(Direction.SOUTH, layout.direction(machine)) },
            { assertEquals(Material("petroleum-gas"), layout.plumbingAt(north)) },
        )
    }

    @Test
    fun theSulfuricChemicalPlantGetsItsLongInserter() {
        val layout = Layout("http://localhost:8080/#/0.03511777708/sulfuric-acid/FSXmr/1/1")
        val machine = layout.machine("chemical-plant")
        val top = layout.insertersAbove(machine)

        assertAll(
            { assertEquals(2, top.size) },
            {
                assertEquals(
                    setOf("fast-inserter", "long-handed-inserter"),
                    top.map { it.name }.toSet(),
                )
            },
            {
                assertEquals(
                    machine.area.topLeft.x + 2,
                    top.single { it.name == "long-handed-inserter" }.area.topLeft.x,
                )
            },
        )
    }

    @Test
    fun theLightOilChemicalPlantFeedsWaterFromTheNorthLane() {
        val layout = Layout("http://localhost:8080/#/0.03511777708/light-oil/FSXmor/1/1")
        val machine = layout.machine("chemical-plant")
        val left = machine.area.topLeft + Coordinate(0, -1)
        val right = machine.area.topLeft + Coordinate(2, -1)

        assertAll(
            { assertEquals(Material("water"), layout.plumbingAt(left)) },
            { assertEquals(Material("heavy-oil"), layout.plumbingAt(right)) },
        )
    }

    /**
     * Heavy oil is the chemical plant's second top corner. It gets a lane like the water import,
     * stacked one row further out with its marker in the same west column, instead of a routed feed
     * that loops around the row.
     */
    @Test
    fun theLightOilHeavyOilLaneMirrorsTheWaterLane() {
        val layout = Layout("http://localhost:8080/#/0.03511777708/light-oil/FSXmor/1/1")
        val machine = layout.machine("chemical-plant")
        val left = machine.area.topLeft.x
        val top = machine.area.topLeft.y
        val heavyOil = Material("heavy-oil")
        val water = Material("water")
        val marker =
            layout.entities.filterIsInstance<InfinityPipeEntity>().single {
                it.material == heavyOil
            }
        val waterMarker =
            layout.entities.filterIsInstance<InfinityPipeEntity>().single { it.material == water }
        val markerRow = marker.area.topLeft.y
        val pipes = layout.entities.filterIsInstance<Plumbing>().filter { it.material == heavyOil }

        assertAll(
            // the same west column as the water marker, one lane further out
            { assertEquals(waterMarker.area.topLeft.x, marker.area.topLeft.x) },
            { assertTrue(markerRow < waterMarker.area.topLeft.y) },
            // a junction pipe above the second top corner and a shaft down onto the port
            {
                assertTrue(
                    pipes.any {
                        it is PipeEntity && it.area.topLeft == Coordinate(left + 2, markerRow)
                    },
                    "no heavy-oil junction above the second top corner",
                )
            },
            {
                assertTrue(
                    pipes.any {
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
        val layout = Layout("http://localhost:8080/#/60/light-oil/FSXmor/1/1")
        val heavyOil = Material("heavy-oil")
        val water = Material("water")
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
                assertTrue(
                    secondPorts.all { port -> heavy.any { it.area.topLeft == port } },
                    "a second top corner has no heavy-oil pipe",
                )
            },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun theElectrolyteElectromagneticPlantTurnsClockwiseAndSwapsItsPipes() {
        val layout = Layout("http://localhost:8080/#/6/electrolyte/FSXmor/1/1")
        val machine = layout.machine("electromagnetic-plant")
        val topLeft = machine.area.topLeft

        assertAll(
            { assertEquals(Direction.EAST, layout.direction(machine)) },
            { assertEquals(Material("heavy-oil"), layout.plumbingAt(topLeft + Coordinate(1, -1))) },
            {
                assertEquals(
                    Material("holmium-solution"),
                    layout.plumbingAt(topLeft + Coordinate(2, 4)),
                )
            },
            {
                assertEquals(
                    Material("electrolyte"),
                    layout.plumbingAt(topLeft + Coordinate(-1, 2)),
                )
            },
            { assertNull(layout.plumbingAt(topLeft + Coordinate(4, 1))) },
        )
    }

    @Test
    fun theElectrolyteOutputKeepsItsWestSource() {
        val layout = Layout("http://localhost:8080/#/6/electrolyte/FSXmor/1/1")
        val machine = layout.machine("electromagnetic-plant")
        val outputRow = machine.area.topLeft.y + 2
        val sources =
            layout.entities.filterIsInstance<InfinityPipeEntity>().filter {
                it.material == Material("electrolyte") &&
                    it.area.topLeft.y == outputRow &&
                    it.area.topLeft.x < machine.area.topLeft.x
            }

        assertEquals(1, sources.size, "the electrolyte output lost its west infinity pipe")
    }
}
