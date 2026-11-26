package de.bjrke.factorio.optimizer.common.util

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

/** Every way to give [elements] at least one each in [length] slots. */
fun <T> countVectors(elements: Array<T>, length: Int) = buildList {
    if (elements.isEmpty()) return@buildList
    val current = IntArray(elements.size)

    fun assign(index: Int, left: Int, max: Int) {
        when {
            index != elements.lastIndex -> {
                for (count in 1..max) {
                    current[index] = count
                    assign(index + 1, left - count, max + 1 - count)
                }
            }
            left == max -> {
                current[index] = left
                add(current.withIndex().associate { (i, c) -> elements[i] to c })
            }
        }
    }

    assign(0, length, length + 1 - elements.size)
}

/** The greatest common divisor of [first] and [second]. */
fun greatestCommonDivisor(first: Int, second: Int): Int =
    if (second == 0) first else greatestCommonDivisor(second, first % second)

/** Slack for a floating-point value that should be whole but carries rounding noise. */
const val EPSILON = 1e-9

/**
 * Snaps a count of at least one to the whole number it is within [EPSILON] of, leaving any other
 * value unchanged. A count below one stays fractional: a tiny but required inserter rate must still
 * place one inserter, and a caller that feeds the value into a pattern must not lose that last
 * small share. A count such as `2.0000000000000004` is floating-point noise from a plan rate, so a
 * caller that rounds it up must not get a third inserter for a flow two can carry.
 */
fun snapToWhole(count: Double) =
    if (count < 1.0) count
    else {
        val nearest = round(count)
        if (abs(count - nearest) <= EPSILON) nearest else count
    }

/**
 * The smallest whole number not below [d], treating a value within [EPSILON] of a whole number as
 * that number. A plan rate that should need two inserters can be `2.0000000000000004`; it still
 * needs two, not three.
 */
fun epsilonCeil(d: Double) = ceil(snapToWhole(d)).toInt()

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
