package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Dimension
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.areaTile
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BELT = "transport-belt"
private const val MARKER = "constant-combinator"
private const val MACHINE = "assembling-machine-3"

class LayoutIndexTest : BaseGameCtxTest() {

    private fun belt(x: Int, y: Int) = BeltEntity(areaTile(x, y), BELT, ironOre, EAST)

    private fun marker(x: Int, y: Int) = MarkerEntity(areaTile(x, y), MARKER, Signal(ironOre))

    private fun machine(x: Int, y: Int) =
        MachineEntity(
            Factory.ASSEMBLING_MACHINE_3,
            Area(Coordinate(x, y), Dimension(3, 3)),
            MACHINE,
        )

    @Test
    fun theGridSpansTheExtentAndMissesEveryTileOutsideIt() {
        val assembler = machine(-3, -2)
        val carrier = belt(2, 2)
        val index = LayoutIndex(listOf(assembler, carrier))

        assertAll(
            { assertEquals(assembler, index.at(Coordinate(-3, -2))) },
            { assertEquals(assembler, index.at(Coordinate(-2, -1))) },
            { assertEquals(assembler, index.at(Coordinate(-1, 0))) },
            { assertEquals(carrier, index.at(Coordinate(2, 2))) },
            // inside the extent, but no entity covers the tile
            { assertNull(index.at(Coordinate(0, 0)), "gap") },
            // one tile past each side of the extent
            { assertNull(index.at(Coordinate(-4, -2)), "west") },
            { assertNull(index.at(Coordinate(3, 2)), "east") },
            { assertNull(index.at(Coordinate(-2, -3)), "north") },
            { assertNull(index.at(Coordinate(-3, 3)), "south") },
            { assertEquals(-3, index.minX) },
            { assertEquals(2, index.maxX) },
            { assertEquals(-2, index.minY) },
            { assertEquals(2, index.maxY) },
        )
    }

    @Test
    fun theLaterEntityOwnsATileTwoEntitiesClaim() {
        val first = marker(0, 0)
        val second = belt(0, 0)

        assertEquals(second, LayoutIndex(listOf(first, second)).at(Coordinate.origin))
    }

    @Test
    fun theOverlapCallbackHearsEachTileTwoEntitiesClaim() {
        val overlaps = mutableListOf<Coordinate>()

        LayoutIndex(listOf(marker(0, 0), belt(0, 0))) { overlaps += it }

        assertEquals(listOf(Coordinate.origin), overlaps)
    }

    @Test
    fun theSameEntityListedTwiceIsNotAnOverlap() {
        val entity = marker(0, 0)
        val overlaps = mutableListOf<Coordinate>()

        LayoutIndex(listOf(entity, entity)) { overlaps += it }

        assertEquals(emptyList(), overlaps)
    }

    @Test
    fun theExtentIsTheBoundingAreaAndIsNullWhenNoTileIsCovered() {
        assertAll(
            { assertNull(layoutExtent(emptyList())) },
            { assertEquals(Area(-3, -2, 6, 5), layoutExtent(listOf(machine(-3, -2), belt(2, 2)))) },
        )
    }

    @Test
    fun anIndexWithoutTilesIsEmpty() {
        val empty = LayoutIndex(emptyList())
        // an entity whose area covers no tile contributes nothing, just as iterating it would
        val noTiles =
            LayoutIndex(
                listOf(
                    MachineEntity(Factory.ASSEMBLING_MACHINE_3, Area(0, 0, 0, 0), MACHINE),
                ),
            )

        assertAll(
            { assertTrue(empty.isEmpty) },
            { assertNull(empty.at(Coordinate.origin)) },
            { assertTrue(noTiles.isEmpty) },
            { assertNull(noTiles.at(Coordinate.origin)) },
        )
    }
}
