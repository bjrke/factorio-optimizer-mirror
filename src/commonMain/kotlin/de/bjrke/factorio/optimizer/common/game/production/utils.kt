package de.bjrke.factorio.optimizer.common.game.production

import kotlin.math.abs
import kotlin.math.round

const val DEFAULT_NORMALIZE_TARGET = 1.0

fun validNormalizeTarget(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0.0 }

fun normalizeTargetFromUrl(raw: String?): Double =
    validNormalizeTarget(raw?.toDoubleOrNull()) ?: DEFAULT_NORMALIZE_TARGET

/**
 * Decimal places kept when serializing the normalize target into the URL. Absolute rounding with
 * the display precision of [superRound] is too coarse for small targets: a single input step then
 * rounds back to the same URL and the field appears stuck. Twelve places keep every row adjustable
 * while the parameter stays short.
 */
private const val NORMALIZE_TARGET_URL_PRECISION = 1_000_000_000_000.0

fun normalizeTargetToUrl(value: Double): String {
    val rounded = superRound(value, NORMALIZE_TARGET_URL_PRECISION)
    return if (rounded > 0.0) rounded.toString() else value.toString()
}

/**
 * Rounds [d] to a few significant decimals: below a magnitude of one to [to] places, above [to] to
 * whole numbers, and in between to the first power of ten that reaches [to]. The magnitude picks
 * the precision, so a negative value rounds like its positive counterpart.
 *
 * [d] must be finite. A non-finite value has no rounding to give back, and `NaN` would never
 * satisfy the recursion's exit condition, so it used to loop forever instead of returning a display
 * value.
 */
fun superRound(d: Double, to: Double = 1000.0): Double {
    require(d.isFinite()) { "superRound needs a finite value but got $d" }
    val magnitude = abs(d)
    return when {
        magnitude < 1.0 -> round(d * to) / to
        magnitude > to -> round(d)
        else -> {
            tailrec fun recursion(f: Double): Double {
                val scaled = magnitude * f
                return if (scaled > to) round(d * f) / f else recursion(f * 10.0)
            }
            recursion(10.0)
        }
    }
}
