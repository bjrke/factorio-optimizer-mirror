package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reported logistic-science issue: the foundry's transport belts and the circuit row's
 * electronic circuits left their producers eastwards while the consumer took the mixed feed from
 * the west end, so both trunks reversed to reach the merge at `(7,20)`. The network split puts both
 * in the east-fed half, and each producer exits east to meet it.
 */
class BlueprintLayoutFeedSideTest {

    @Test
    fun theReportedLogisticScienceOutputsFollowTheirFeedSide() {
        val layout = layoutFromUrl(REPORTED_URL)
        val entities = layout.entities
        val machines = entities.filterIsInstance<MachineEntity>()
        val belts = entities.filterIsInstance<BeltEntity>()
        val scienceTop =
            machines.filter { it.recipe == "logistic-science-pack" }.minOf { it.area.topLeft.y }
        val foundry = machines.single { it.recipe == "transport-belt" }
        val circuit = machines.single { it.recipe == "electronic-circuit" }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(entities)) },
            { assertMixedLane(belts, scienceTop - 2, Direction.WEST) },
            {
                assertProductRow(
                    belts,
                    "transport-belt",
                    foundry.area.topLeft.y - 2,
                    Direction.EAST,
                )
            },
            {
                assertProductRow(
                    belts,
                    "electronic-circuit",
                    circuit.area.bottomRight.y + 2,
                    Direction.EAST,
                )
            },
        )
    }

    /** A row's mixed input lane: present, and fed from the end [direction] names. */
    private fun assertMixedLane(belts: List<BeltEntity>, y: Int, direction: Direction) {
        val lane = belts.filter { it.load.isMixed && it.area.topLeft.y == y }
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
        material: String,
        y: Int,
        direction: Direction,
    ) {
        val product = belts.filter {
            it.material == Material(material) && !it.load.isFull && it.area.topLeft.y == y
        }
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
