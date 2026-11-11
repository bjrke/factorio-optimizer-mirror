package de.bjrke.factorio.optimizer.common.blueprint.placing.special

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PathPlacerTest {

    /**
     * A belt tile carries one entity, so a route that comes back onto a tile it already used would
     * silently drop the first run's belt there (in the reported rocket-silo layout, the belt a
     * tunnel exit empties onto). Such a path is rejected before anything is placed.
     */
    @Test
    fun aBeltPathThatRevisitsATileIsRejected() {
        val board = board(fluid = false)
        val nodes =
            listOf(
                Coordinate(0, 0),
                Coordinate(1, 0),
                Coordinate(1, 1),
                Coordinate(1, 0),
                Coordinate(2, 0),
            )

        val conflicts = placePath(board, ironPlate, nodes, finalHeading = EAST)

        assertAll(
            { assertEquals(setOf(Coordinate(1, 0)), conflicts) },
            { assertEquals(emptyList(), board.placed) },
        )
    }

    /** A pipe tile is a junction, so a pipe route may revisit a tile and still be placed. */
    @Test
    fun aPipePathMayRevisitATile() {
        val board = board(fluid = true)
        val nodes =
            listOf(
                Coordinate(0, 0),
                Coordinate(1, 0),
                Coordinate(1, 1),
                Coordinate(1, 0),
                Coordinate(2, 0),
            )

        val conflicts = placePath(board, water, nodes)

        assertAll(
            { assertEquals(emptySet(), conflicts) },
            { assertNotNull(board.placed.firstOrNull(), "nothing was placed") },
        )
    }

    private fun board(fluid: Boolean) =
        LayoutBoard(
            LayoutNames("transport-belt", "inserter", "long-handed-inserter"),
            undergroundRange = 4,
            fluidPredicate = { fluid },
            temperatureOf = { 0 },
        )
}
