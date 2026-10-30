package de.bjrke.factorio.optimizer.common.route

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.layout.placeBelt
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.routing.BeltRouteRules
import de.bjrke.factorio.optimizer.common.routing.Sink
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutRouterTest {

    private val material = Material("route")
    private val foreign = Material("foreign")

    /**
     * The belt beside the goal faces the goal, not the direction it arrived from. A foreign belt
     * under the corner would catch a straight-on belt, but the corner turns west into the feed
     * before it leaves that side.
     */
    @Test
    fun theTileBesideTheGoalFacesTheGoalNotTheArrival() {
        val board =
            LayoutBoard(
                LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
                undergroundRange = 4,
                fluidPredicate = { false },
                temperatureOf = { 0 },
            )
        board.placeBelt(Coordinate(3, 3), foreign, EAST)
        val sink = Sink(Coordinate(1, 2), Coordinate(2, 2), WEST)

        val route =
            LayoutRouter(board)
                .findRoute(
                    Coordinate(3, 0),
                    SOUTH,
                    sink.feed,
                    BeltRouteRules(board).allowed(material, sink),
                )

        assertEquals(
            listOf(Coordinate(3, 1), Coordinate(3, 2), Coordinate(2, 2)),
            route,
        )
    }
}
