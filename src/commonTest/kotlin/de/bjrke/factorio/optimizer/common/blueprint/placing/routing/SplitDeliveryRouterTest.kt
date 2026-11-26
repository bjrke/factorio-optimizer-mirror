package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIssue
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.placing.route.LayoutRouter
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SplitDeliveryRouterTest : BaseGameCtxTest() {

    /**
     * A split off a half product trunk keeps the half lane on the splitter and on the branch, so
     * the stretcher cannot later swallow the branch as a full belt.
     */
    @Test
    fun aSplitOffAHalfTrunkKeepsTheHalfLoad() {
        val half = LaneLoad.half(ironPlate, left = false)
        val board = board()
        // the router only runs east of the import feed column
        for (x in 30..32) board.placeBelt(Coordinate(x, 0), half, EAST)
        val networks = DeliveryNetworks(setOf(ironPlate))
        networks.record(
            ironPlate,
            listOf(Coordinate(30, 0), Coordinate(31, 0), Coordinate(32, 0)),
            Sink(Coordinate(34, 0), Coordinate(34, 0), EAST),
        )
        val failures = mutableListOf<LayoutIssue>()
        val router =
            SplitDeliveryRouter(
                board,
                LayoutRouter(board),
                networks,
                BeltRouteRules(board),
                failures,
            )

        val routed = router.deliver(ironPlate, Sink(Coordinate(33, 1), Coordinate(33, 1), EAST))

        assertTrue(routed, failures.joinToString { it.message })
        val splitter = assertIs<SplitterEntity>(board.entityAt(Coordinate(31, 0)))
        val branch = assertIs<BeltEntity>(board.entityAt(Coordinate(32, 1)))
        assertAll(
            { assertEquals(emptyList(), failures) },
            { assertEquals(half, splitter.load) },
            { assertEquals(half, branch.load) },
        )
    }

    private fun board() =
        LayoutBoard(
            LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
            undergroundRange = 4,
            temperatureOf = { 0 },
        )
}
