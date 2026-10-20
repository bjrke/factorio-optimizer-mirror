package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.InserterStats
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.Logistics

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

internal data class BeltCase(
    val belt: BeltTier,
    val lanes: Int,
    val stack: Int,
    val expected: Double,
)

/** The calculator the logistics suites build their cases from. */
internal fun calculator(
    belt: BeltTier = BeltTier.TRANSPORT_BELT,
    inserter: InserterTier = InserterTier.INSERTER,
    lanes: Int = 1,
    beltStack: Int = 1,
    inserterStack: Int = 1,
    logistics: Logistics = testLogistics,
) = LogisticsCalculator(logistics, belt, inserter, lanes, beltStack, inserterStack)

/** The items per second a belt carries at the calculator's own stacking. */
internal fun LogisticsCalculator.beltThroughput() = beltThroughput(beltStack)

/** The belts needed for [rate] at the calculator's own stacking. */
internal fun LogisticsCalculator.beltCount(rate: Double) = beltCount(rate, beltStack)
