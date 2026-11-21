package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The reported chemical-science issue: the plastic trunk is a producer-consumer belt, but the
 * import-anchored mixed lane made it the U-turn feeder, so it turned west into the merge after the
 * necessary corner into the lane. A produced turn feeder on an outer belt row now feeds the merge
 * from its open side instead, so the merge is still the straight belt fed from both sides.
 */
class BlueprintLayoutMergeTest {

    @Test
    fun theReportedChemicalScienceUrlSideLoadsItsProducedTurnFeed() {
        val layout = layoutFromUrl(REPORTED_URL)
        val belts = layout.entities.filterIsInstance<BeltEntity>()
        val machines = layout.entities.filterIsInstance<MachineEntity>()
        val circuitTop = machines.filter { it.recipe == "advanced-circuit" }.minOf { it.area.y }
        val machineLeft = machines.filter { it.recipe == "advanced-circuit" }.minOf { it.area.x }
        // the far north input lane of the circuit row
        val laneY = circuitTop - 3
        val lane =
            assertNotNull(
                belts.firstOrNull { it.area.y == laneY && it.load.isMixed },
                "no mixed lane above the circuit row",
            )
        val merge =
            assertNotNull(
                belts.firstOrNull { it.area.y == laneY - 1 && it.load.isMixed },
                "no mixed merge above the lane",
            )
        val feedDirection = if (lane.direction == EAST) WEST else EAST
        val feedX = if (lane.direction == EAST) merge.area.x + 1 else merge.area.x - 1
        val feed =
            assertNotNull(
                belts.firstOrNull {
                    it.area.topLeft == Coordinate(feedX, merge.area.y) &&
                        it.load.isFull &&
                        !it.load.isMixed &&
                        it.material == plastic
                },
                "the plastic trunk must feed the merge from the side, not the lane directly",
            )

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(setOf(ironPlate, plastic), lane.load.materials) },
            { assertEquals(feedDirection, feed.direction) },
            { assertTrue(feed.area.x < machineLeft, "the feed joins downstream") },
            { assertEquals(setOf(ironPlate, plastic), merge.load.materials) },
        )
    }

    /**
     * The same two-sided merge below the chemical-science row: sulfur enters from the west and the
     * produced engine-unit trunk from the east, so neither known-full belt has to side-load the
     * lane directly.
     */
    @Test
    fun theReportedChemicalScienceUrlFeedsItsLowerMergeFromBothSides() {
        val layout = layoutFromUrl(REPORTED_URL)
        val belts = layout.entities.filterIsInstance<BeltEntity>()
        val machines = layout.entities.filterIsInstance<MachineEntity>()
        val chemBottom =
            machines.filter { it.recipe == "chemical-science-pack" }.maxOf { it.area.endY }
        val lane =
            assertNotNull(
                belts
                    .filter { it.area.y >= chemBottom && it.load.isMixed }
                    .minByOrNull { it.area.y },
                "no mixed lane below the chemical-science row",
            )
        val merge =
            assertNotNull(
                belts.firstOrNull {
                    it.area.y == lane.area.y + 1 && it.load.isMixed
                },
                "no mixed merge below the lane",
            )
        val feed =
            assertNotNull(
                belts.firstOrNull {
                    it.area.topLeft == Coordinate(merge.area.x + 1, merge.area.y) &&
                        it.load.isFull &&
                        !it.load.isMixed
                },
                "the turn feeder does not reach the merge",
            )
        val straight =
            assertNotNull(
                belts.firstOrNull {
                    it.area.topLeft == Coordinate(merge.area.x - 1, merge.area.y)
                },
                "the straight feeder does not reach the merge",
            )

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(WEST, feed.direction) },
            { assertEquals(EAST, straight.direction) },
            { assertEquals(lane.load.materials, merge.load.materials) },
        )
    }

    private companion object {
        val ironPlate = Material("iron-plate")
        val plastic = Material("plastic-bar")
        const val REPORTED_URL = "http://localhost:8080/#/1/chemical-science-pack/CFSbmr/1/1/10/"
    }
}
