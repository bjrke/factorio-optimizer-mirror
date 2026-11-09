package de.bjrke.factorio.optimizer.common.blueprint.placing.route

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.BeltRouteRules
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.Sink
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutRouterTest {

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
        board.placeBelt(Coordinate(3, 3), copperPlate, EAST)
        val sink = Sink(Coordinate(1, 2), Coordinate(2, 2), WEST)

        val route =
            LayoutRouter(board)
                .findRoute(
                    Coordinate(3, 0),
                    SOUTH,
                    sink.feed,
                    BeltRouteRules(board).allowed(ironPlate, sink),
                )

        assertEquals(
            listOf(Coordinate(3, 1), Coordinate(3, 2), Coordinate(2, 2)),
            route,
        )
    }
}
