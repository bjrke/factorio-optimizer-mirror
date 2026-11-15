package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.util.superRound

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
