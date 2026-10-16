package de.bjrke.factorio.optimizer.common.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateTest {

    @Test
    fun areaWithoutParamsHasSize1() {
        assertEquals(1, Coordinate(34, 21).area().size)
    }

    @Test
    fun areaGrownBy3HasSize49() {
        assertEquals(49, Coordinate(132, 14).area().grow(3).size)
    }
}
