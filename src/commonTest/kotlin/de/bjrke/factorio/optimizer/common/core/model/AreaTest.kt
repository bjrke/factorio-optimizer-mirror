package de.bjrke.factorio.optimizer.common.core.model

import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AreaTest {

    @Test
    fun tileIsAOneTileAreaAtItsTopLeft() {
        val area = areaTile(3, 4)
        assertAll(
            { assertEquals(Coordinate(3, 4), area.topLeft) },
            { assertEquals(1, area.size) },
            { assertEquals(setOf(Coordinate(3, 4)), area.toSet()) },
        )
    }

    @Test
    fun iterationVisitsNegativeTilesColumnMajor() {
        val area = Area(-2, -1, 2, 2)
        assertEquals(
            listOf(
                Coordinate(-2, -1),
                Coordinate(-2, 0),
                Coordinate(-1, -1),
                Coordinate(-1, 0),
            ),
            area.toList(),
        )
    }

    @Test
    fun zeroSizedAreasVisitNoTiles() =
        assertTableOf(Dimension(0, 0), Dimension(0, 2), Dimension(2, 0)) { dimension ->
            assertEquals(
                emptyList(),
                Area(Coordinate(-1, -2), dimension).toList(),
            )
        }

    @Test
    fun forEachTileVisitsTheTilesInIteratorOrder() {
        val area = Area(-2, -1, 2, 2)
        val tiles = mutableListOf<Coordinate>()
        area.forEachTile { x, y -> tiles += Coordinate(x, y) }
        assertEquals(area.toList(), tiles)
    }

    @Test
    fun forEachTileVisitsNoTileOfAnEmptyAreaAndTheOneTileOfATileArea() =
        assertTableOf(
            emptyList<Coordinate>() to Area(1, 2, 0, 3),
            listOf(Coordinate(3, 4)) to areaTile(3, 4),
        ) { (expected, area) ->
            val tiles = mutableListOf<Coordinate>()
            area.forEachTile { x, y -> tiles += Coordinate(x, y) }
            assertEquals(expected, tiles)
        }

    @Test
    fun neighborsAreTheRingOutsideTheAreaWithoutCorners() {
        val area = Area(2, 3, 2, 2)
        assertEquals(
            setOf(
                Coordinate(2, 2),
                Coordinate(3, 2),
                Coordinate(2, 5),
                Coordinate(3, 5),
                Coordinate(1, 3),
                Coordinate(1, 4),
                Coordinate(4, 3),
                Coordinate(4, 4),
            ),
            area.neighbors(),
        )
    }

    @Test
    fun oneTileAreaNeighborsAreItsFourOrthogonalTiles() {
        val tile = Coordinate(3, 4)
        assertEquals(tile.neighbors.toSet(), tile.area().neighbors())
    }

    @Test
    fun zeroSizedAreasHaveNoNeighbors() =
        assertTableOf(Dimension(0, 0), Dimension(0, 2), Dimension(2, 0)) { dimension ->
            assertEquals(
                emptySet(),
                Area(Coordinate(-1, -2), dimension).neighbors(),
            )
        }

    @Test
    fun boundingBoxCoversEveryArea() {
        val areas = listOf(Area(2, 3, 2, 2), Area(-1, 6, 3, 1))
        assertAll(
            { assertEquals(Area(-1, 3, 5, 4), areas.boundingBox()) },
            { assertEquals(Area(-1, 3, 5, 4), areas.iterator().boundingBox()) },
        )
    }

    @Test
    fun boundingBoxOfASingleAreaIsThatArea() {
        val area = Area(2, 3, 4, 5)
        assertEquals(area, listOf(area).boundingBox())
    }

    @Test
    fun boundingBoxSkipsEmptyAreas() {
        val tile = Area(4, 5, 1, 1)
        assertEquals(tile, listOf(tile, Area(-9, -9, 0, 3)).boundingBox())
    }

    @Test
    fun boundingBoxWithoutATileIsNull() =
        assertTableOf(
            emptyList(),
            listOf(Area(1, 2, 0, 0)),
            listOf(Area(1, 2, 2, 0), Area(5, 6, 0, 1)),
        ) { areas ->
            assertNull(areas.boundingBox())
        }
}
