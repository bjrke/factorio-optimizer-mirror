package de.bjrke.factorio.optimizer.common.blueprint.placing.route

import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIssue
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeEnd
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.BeltRouteRules
import de.bjrke.factorio.optimizer.common.blueprint.placing.routing.Sink
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutRouterTest : BaseGameCtxTest() {

    /**
     * A tile east of the router's west bound: it never steps west of [IMPORT_FEED], so the generic
     * coordinates of these tests start on that column instead of at zero.
     */
    private fun tile(x: Int, y: Int) = Coordinate(IMPORT_FEED + x, y)

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
        board.placeBelt(tile(3, 3), copperPlate, EAST)
        val sink = Sink(tile(1, 2), tile(2, 2), WEST)

        val route =
            LayoutRouter(board)
                .findRoute(
                    tile(3, 0),
                    SOUTH,
                    sink.feed,
                    BeltRouteRules(board).allowed(ironPlate, sink),
                )

        assertEquals(
            listOf(tile(3, 1), tile(3, 2), tile(2, 2)),
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
        board.placeBelt(tile(5, 3), copperPlate, EAST)
        val sink = Sink(tile(2, 2), tile(3, 2), WEST)

        val route =
            LayoutRouter(board)
                .findRoute(
                    tile(5, 0),
                    SOUTH,
                    sink.feed,
                    BeltRouteRules(board).allowed(ironPlate, sink),
                )

        assertEquals(
            listOf(tile(5, 1), tile(5, 2), tile(4, 2), tile(3, 2)),
            route,
        )
    }

    /**
     * A buried entrance dives instead of feeding the tile it faces, so it must not keep a route off
     * that tile. In the reported utility-science layout the product belt crosses the low-density
     * lane's entrance column; treating the entrance as a side-load left the product row
     * unconnected. An underground *output* does surface and pour onto the next tile, so it stays a
     * side-load.
     */
    @Test
    fun aBuriedEntranceDoesNotBlockARouteOverItsRun() {
        val board = board()
        board.placeEnd(copperPlate, tile(3, 3), NORTH, Io.INPUT)
        val sink = Sink(tile(4, 2), tile(3, 2), EAST)

        val route =
            LayoutRouter(board)
                .findRoute(
                    tile(1, 2),
                    EAST,
                    sink.feed,
                    BeltRouteRules(board).allowed(ironPlate, sink),
                )

        assertEquals(listOf(tile(2, 2), tile(3, 2)), route)
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
            ROUTE_MARGIN to emptyList<LayoutIssue>(),
            ROUTE_MARGIN + 1 to
                listOf(
                    LayoutIssue(
                        "route ${tile(3, 0)} -> ${tile(3, ROUTE_MARGIN + 1)} needed the " +
                            "wide search box",
                        Area(tile(3, 0).x, 0, 1, ROUTE_MARGIN + 2),
                    ),
                ),
        ) { (reach, expected) ->
            val board = board()
            // one belt sets the extent: the compact box ends ROUTE_MARGIN rows below it
            board.placeBelt(tile(5, 0), ironPlate, EAST)
            val failures = mutableListOf<LayoutIssue>()
            val goal = tile(3, reach)

            val route =
                LayoutRouter(board, failures)
                    .findRoute(tile(3, 0), SOUTH, goal, allowed = { _, _ -> true })

            assertNotNull(route, "the route lies inside the wide box, so it is still found")
            assertEquals(expected, failures)
        }

    /**
     * The search region's north bound floors at row zero: a route that only exists above the top
     * row is not found, so no blueprint tile ever carries a negative y. Without the floor the
     * compact box would reach [ROUTE_MARGIN] rows past the extent, which starts at zero here.
     */
    @Test
    fun aRouteNeverRunsNorthOfRowZero() {
        val board = board()
        board.placeBelt(tile(5, 0), ironPlate, EAST)
        val goal = tile(2, 0)

        // the only tiles this route may use are the goal and the rows above the top
        val route =
            LayoutRouter(board)
                .findRoute(
                    tile(5, 0),
                    NORTH,
                    goal,
                    allowed = { candidate, _ -> candidate == goal || candidate.y < 0 },
                )

        assertNull(route, "a route above row zero is outside the search region")
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
        board.placeBelt(tile(10, 3), ironPlate, EAST)
        board.placeBelt(tile(20, 20), ironPlate, EAST)
        val allowed: (Coordinate, Direction) -> Boolean = { candidate, _ ->
            (candidate.y == 0 && candidate.x in tile(2, 0).x..tile(7, 0).x) ||
                candidate == tile(10, 0) ||
                candidate == tile(7, 1)
        }

        val route =
            router.route(
                RouteRequest(
                    from = tile(10, 0),
                    heading = WEST,
                    to = tile(7, 1),
                    allowed = allowed,
                    profile = router.pipeProfile,
                ),
            )

        assertNull(route)
    }
}
