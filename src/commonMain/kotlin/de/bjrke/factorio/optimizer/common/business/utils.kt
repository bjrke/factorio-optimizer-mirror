package de.bjrke.factorio.optimizer.common.business

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

fun superRound(d: Double, to: Double = 1000.0) =
    when {
        d < 1.0 -> round(d * to) / to
        d > to -> round(d)
        else -> {
            tailrec fun recursion(f: Double): Double {
                val df = d * f
                return if (df > to) round(df) / f else recursion(f * 10.0)
            }
            recursion(10.0)
        }
    }
