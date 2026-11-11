package de.bjrke.factorio.optimizer.common.blueprint.placing.route

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.BeltRouteRules
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.Sink
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutRouterTest {

    private fun board() =
        LayoutBoard(
            LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
            undergroundRange = 4,
            fluidPredicate = { false },
            temperatureOf = { 0 },
        )

    /**
     * The belt beside the goal faces the goal, not the direction it arrived from. A foreign belt
     * under the corner would catch a straight-on belt, but the corner turns west into the feed
     * before it leaves that side.
     */
    @Test
    fun theTileBesideTheGoalFacesTheGoalNotTheArrival() {
        val board = board()
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

    /**
     * A corner belt also faces the tile it turns into, not the one the route arrived from. A
     * foreign belt below the corner must not read as a side-load, or the route jogs two extra tiles
     * around a turn it can take; the reported logistic-science layout had that jog before its
     * circuit feed.
     */
    @Test
    fun aCornerMayTurnOverAForeignBeltItDoesNotFeed() {
        val board = board()
        board.placeBelt(Coordinate(5, 3), copperPlate, EAST)
        val sink = Sink(Coordinate(2, 2), Coordinate(3, 2), WEST)

        val route =
            LayoutRouter(board)
                .findRoute(
                    Coordinate(5, 0),
                    SOUTH,
                    sink.feed,
                    BeltRouteRules(board).allowed(ironPlate, sink),
                )

        assertEquals(
            listOf(Coordinate(5, 1), Coordinate(5, 2), Coordinate(4, 2), Coordinate(3, 2)),
            route,
        )
    }

    /**
     * A bend is its own belt piece, so it costs more than a straight tile: a route pays one extra
     * tile rather than one extra bend. Together with the tunnel entry this keeps the reported
     * logistic-science gear belt out of the tunnels it would otherwise need.
     */
    @Test
    fun aBeltTurnCostsMoreThanAStraightTile() {
        val costs = LayoutRouter(board()).beltProfile.costs

        assertTrue(costs.turnCost > costs.surfaceCost)
    }

    /**
     * The compact search box reaches [ROUTE_MARGIN] tiles past everything placed. A route that only
     * exists beyond it is still found with the wide box, but it is reported: every layout test
     * asserts the failures are empty, so a layout that needs the room cannot pass quietly.
     */
    @Test
    fun aRoutePastTheCompactBoxIsFoundButReported() =
        assertTableOf(
            ROUTE_MARGIN to emptyList<String>(),
            ROUTE_MARGIN + 1 to
                listOf(
                    "route ${Coordinate(3, 0)} -> ${Coordinate(3, ROUTE_MARGIN + 1)} needed the " +
                        "wide search box",
                ),
        ) { (reach, expected) ->
            val board = board()
            // one belt sets the extent: the compact box ends ROUTE_MARGIN rows below it
            board.placeBelt(Coordinate(5, 0), ironPlate, EAST)
            val failures = mutableListOf<String>()
            val goal = Coordinate(3, reach)

            val route =
                LayoutRouter(board, failures)
                    .findRoute(Coordinate(3, 0), SOUTH, goal, allowed = { _, _ -> true })

            assertNotNull(route, "the route lies inside the wide box, so it is still found")
            assertEquals(expected, failures)
        }

    /**
     * A route may not reverse back onto the tile it just surfaced on. In the reported
     * utility-science layout that backtrack made a tunnel exit and the next entrance share a tile,
     * so the first run lost its exit and the layout became unpaired.
     */
    @Test
    fun aPipeRouteDoesNotDoubleBackOntoItsLanding() {
        val board =
            LayoutBoard(
                LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
                undergroundRange = 4,
                fluidPredicate = { true },
                temperatureOf = { 0 },
            )
        val router = LayoutRouter(board)
        // an empty board has no extent, so the route region would be empty; these tiles only set it
        board.placeBelt(Coordinate(10, 3), ironPlate, EAST)
        board.placeBelt(Coordinate(20, 20), ironPlate, EAST)
        val allowed: (Coordinate, Direction) -> Boolean = { tile, _ ->
            (tile.y == 0 && tile.x in 2..7) || tile == Coordinate(10, 0) || tile == Coordinate(7, 1)
        }

        val route =
            router.route(
                RouteRequest(
                    from = Coordinate(10, 0),
                    heading = WEST,
                    to = Coordinate(7, 1),
                    allowed = allowed,
                    profile = router.pipeProfile,
                ),
            )

        assertNull(route)
    }
}
