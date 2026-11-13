package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.game.prototype.BeltStats
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.game.prototype.Logistics
import de.bjrke.factorio.optimizer.common.game.prototype.LogisticsConfig
import de.bjrke.factorio.optimizer.common.game.prototype.PoleStats

/** The prototype stats the real dump provides, plus the calculator the logistics suites build. */
internal val testLogistics =
    Logistics(
        belts =
            mapOf(
                "transport-belt" to BeltStats(speed = 0.03125, undergroundRange = 5),
                "fast-transport-belt" to BeltStats(speed = 0.0625, undergroundRange = 7),
                "express-transport-belt" to BeltStats(speed = 0.09375, undergroundRange = 9),
                "turbo-transport-belt" to BeltStats(speed = 0.125, undergroundRange = 11),
            ),
        poles =
            mapOf(
                "small-electric-pole" to PoleStats(supplyRadius = 2, wireReach = 7.5),
                "medium-electric-pole" to PoleStats(supplyRadius = 3, wireReach = 9.0),
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
