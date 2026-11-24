package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.pipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The side a row takes its feeds from, and the routes that brings. The reported logistic-science
 * issue: the foundry's transport belts and the circuit row's electronic circuits left their
 * producers eastwards while the consumer took the mixed feed from the west end, so both trunks
 * reversed to reach the merge at `(7,20)`. The network split puts both in the east-fed half, and
 * each producer exits east to meet it.
 */
class BlueprintLayoutFeedSideTest : BaseGameCtxTest() {

    @Test
    fun theReportedLogisticScienceOutputsFollowTheirFeedSide() {
        val layout = layoutFromUrl(REPORTED_URL)
        val entities = layout.entities
        val machines = entities.filterIsInstance<MachineEntity>()
        val belts = entities.filterIsInstance<BeltEntity>()
        val scienceTop =
            machines.filter { it.recipe == "logistic-science-pack" }.minOf { it.area.y }
        val foundry = machines.single { it.recipe == "transport-belt" }
        val circuit = machines.single { it.recipe == "electronic-circuit" }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(entities)) },
            { assertMixedLane(belts, scienceTop - 2, Direction.WEST) },
            { assertProductRow(belts, transportBelt, foundry.area.y - 2, EAST) },
            { assertProductRow(belts, electronicCircuit, circuit.area.endY + 1, EAST) },
        )
    }

    /**
     * Vulcanus builds its pipe in a foundry row and feeds it to the rocket silo and to the engine
     * row below it, so the silo's share splits off the pipe belt. The rows in between wall off the
     * split points next to the silo, and only a point further down the belt reaches its pipe feed;
     * when the search gives up at the nearest points, the silo's pipe input stays unrouted.
     */
    @Test
    fun theVulcanusRocketSiloSplitsThePipeFurtherDownItsBelt() {
        val layout = Layout("rocket-silo", surface = Surface.VULCANUS)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                val splitters = layout.entities.filterIsInstance<SplitterEntity>()
                assertNotNull(
                    splitters.firstOrNull { it.material == pipe },
                    splitters.toString(),
                )
            },
        )
    }

    /** A row's mixed input lane: present, and fed from the end [direction] names. */
    private fun assertMixedLane(belts: List<BeltEntity>, y: Int, direction: Direction) {
        val lane = belts.filter { it.load.isMixed && it.area.y == y }
        assertAll(
            { assertTrue(lane.isNotEmpty(), "no mixed lane on row $y") },
            {
                assertEquals(
                    setOf(direction),
                    lane.map { it.direction }.toSet(),
                    "the mixed lane on row $y must face $direction",
                )
            },
        )
    }

    /** The one-lane product belt of [material] on row [y]: present, and exiting [direction]. */
    private fun assertProductRow(
        belts: List<BeltEntity>,
        material: Material,
        y: Int,
        direction: Direction,
    ) {
        val product = belts.filter { it.material == material && !it.load.isFull && it.area.y == y }
        assertAll(
            { assertTrue(product.isNotEmpty(), "no $material product belt on row $y") },
            {
                assertEquals(
                    setOf(direction),
                    product.map { it.direction }.toSet(),
                    "$material must exit $direction on row $y",
                )
            },
        )
    }

    private companion object {
        const val REPORTED_URL = "http://localhost:8080/#/1/logistic-science-pack/CFSbmr/1/1/10/-"
    }
}
