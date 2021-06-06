package de.bjrke.factorio.optimizer.business

import kotlin.math.round

fun roundN(d: Double, to: Double = 10000.0) = round(d * to) / to
