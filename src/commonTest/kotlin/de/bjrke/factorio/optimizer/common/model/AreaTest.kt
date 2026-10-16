package de.bjrke.factorio.optimizer.common.model

import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

class AreaTest {

    @Test
    fun tileIsAOneTileAreaAtItsTopLeft() {
        val area = AreaTile(3, 4)
        assertAll(
            { assertEquals(Coordinate(3, 4), area.topLeft) },
            { assertEquals(1, area.size) },
            { assertEquals(setOf(Coordinate(3, 4)), area.toSet()) },
        )
    }
}
