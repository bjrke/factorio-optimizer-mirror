package de.bjrke.factorio.optimizer.common.business

import de.bjrke.test.assertTableOf
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsKtTest {

    @Test
    fun superRoundShouldRoundTheNumber() =
        assertTableOf(
            0.00001234567 to 0.0,
            0.1234567890 to round(0.1234567890 * 1000.0) / 1000.0,
            1.5 to 1.5,
            100.12345 to 100.1,
            9876.5432 to 9877.0,
            123456789.1234 to 123456789.0
        ) { (from, expected) ->
            assertEquals(expected, superRound(from))
        }
}
