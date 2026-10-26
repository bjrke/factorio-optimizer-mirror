package de.bjrke.factorio.optimizer.common.production

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
            123456789.1234 to 123456789.0,
        ) { (from, expected) ->
            assertEquals(expected, superRound(from))
        }

    @Test
    fun validNormalizeTargetRejectsNonPositiveOrNonFinite() =
        assertTableOf(
            null to null,
            0.0 to null,
            -0.0 to null,
            -3.0 to null,
            Double.NaN to null,
            Double.POSITIVE_INFINITY to null,
            Double.NEGATIVE_INFINITY to null,
            0.001 to 0.001,
            0.5 to 0.5,
            2.0 to 2.0,
        ) { (value, expected) ->
            assertEquals(expected, validNormalizeTarget(value))
        }

    @Test
    fun normalizeTargetFromUrlFallsBackToDefaultForInvalidValues() =
        assertTableOf(
            null to DEFAULT_NORMALIZE_TARGET,
            "" to DEFAULT_NORMALIZE_TARGET,
            "abc" to DEFAULT_NORMALIZE_TARGET,
            "0" to DEFAULT_NORMALIZE_TARGET,
            "0.0" to DEFAULT_NORMALIZE_TARGET,
            "-3" to DEFAULT_NORMALIZE_TARGET,
            "NaN" to DEFAULT_NORMALIZE_TARGET,
            "Infinity" to DEFAULT_NORMALIZE_TARGET,
            "-Infinity" to DEFAULT_NORMALIZE_TARGET,
            "0.5" to 0.5,
            "2" to 2.0,
        ) { (raw, expected) ->
            assertEquals(expected, normalizeTargetFromUrl(raw))
        }

    @Test
    fun normalizeTargetRoundTripsThroughUrl() =
        assertTableOf(
            0.000305,
            0.000305283950617285,
            0.5,
            1.0,
            2.0,
            123.456,
            1.0e-6,
            1.0e6,
        ) { value ->
            assertEquals(value, normalizeTargetFromUrl(normalizeTargetToUrl(value)), 1.0e-12)
        }

    /**
     * Regression: the URL used to keep only six decimals of [normalizeTarget]. For small targets a
     * single step in an amount input (step 1) then rounded back to the very same URL, so fields
     * like the scrap output of the big mining drill (display 0.999) looked stuck. The stored URL
     * must keep the display of the next step.
     */
    @Test
    fun normalizeTargetUrlKeepsSingleInputStepsAdjustable() =
        assertTableOf(
            // scrap output of the big mining drill, clicked first -> its own display is 1
            3275.6419753086425 to 1.0,
            // next step of that same row
            3275.6419753086425 to 2.0,
            // a larger amount (display 1.998) must behave the same, next step to 2
            6551.283950617285 to 2.0,
            // previous step back to 1
            6551.283950617285 to 1.0,
        ) { (amount, step) ->
            val normalizeTarget = step / amount
            val restored = normalizeTargetFromUrl(normalizeTargetToUrl(normalizeTarget))
            assertEquals(superRound(step), superRound(amount * restored))
        }
}
