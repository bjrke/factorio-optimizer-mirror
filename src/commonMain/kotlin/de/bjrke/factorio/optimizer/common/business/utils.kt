package de.bjrke.factorio.optimizer.common.business

import kotlin.math.round

fun superRound(d: Double, to: Double = 1000.0): Double =
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
