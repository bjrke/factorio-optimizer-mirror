package de.bjrke.factorio.optimizer.common.service.fixtures

import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.prototype.Logistics
import de.bjrke.factorio.optimizer.common.prototype.LogisticsConfig

/** The prototype stats the real dump provides, plus the calculator the logistics suites build. */
internal val testLogistics =
    Logistics(
        belts =
            mapOf(
                "transport-belt" to 0.03125,
                "fast-transport-belt" to 0.0625,
                "express-transport-belt" to 0.09375,
                "turbo-transport-belt" to 0.125,
            ),
        inserters =
            mapOf(
                "burner-inserter" to
                    InserterStats(
                        0.013,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                "inserter" to
                    InserterStats(
                        0.014,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                "long-handed-inserter" to
                    InserterStats(
                        0.02,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                "fast-inserter" to
                    InserterStats(
                        0.04,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                // the bulk inserter holds several items but cannot stack them on a belt
                "bulk-inserter" to
                    InserterStats(
                        0.04,
                        minStack = 2,
                        maxStack = 12,
                        stacksOnBelt = false,
                    ),
                "stack-inserter" to
                    InserterStats(
                        0.04,
                        minStack = 6,
                        maxStack = 16,
                        stacksOnBelt = true,
                    ),
            ),
        pumpRate = 1200.0,
    )

internal val testLogisticsConfig = LogisticsConfig(testLogistics)

internal data class BeltCase(
    val belt: BeltTier,
    val lanes: Int,
    val stack: Int,
    val expected: Double,
)

/** The calculator the logistics suites build their cases from. */
internal fun calculator(config: LogisticsCalculatorSelection = LogisticsCalculatorSelection()) =
    LogisticsCalculator(
        testLogistics,
        config,
    )

/** The items per second a belt carries at the calculator's own stacking. */
internal fun LogisticsCalculator.beltThroughputAtOwnStack() = beltThroughput(beltStack)

/** The belts needed for [rate] at the calculator's own stacking. */
internal fun LogisticsCalculator.beltCountAtOwnStack(rate: Double) = beltCount(rate, beltStack)
