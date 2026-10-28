package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.fixtures.BeltCase
import de.bjrke.factorio.optimizer.common.fixtures.beltCountAtOwnStack
import de.bjrke.factorio.optimizer.common.fixtures.beltThroughputAtOwnStack
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Logistics
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Belt, inserter and placing throughput, and the counts derived from them. */
class LogisticsTest {

    @Test
    fun beltThroughputPerTierLanesAndStacking() =
        assertTableOf(
            // 0.03125 tiles/tick * 60 * 4 items per lane
            BeltCase(BeltTier.TRANSPORT_BELT, 1, 1, 7.5),
            BeltCase(BeltTier.TRANSPORT_BELT, 2, 1, 15.0),
            BeltCase(BeltTier.TURBO_TRANSPORT_BELT, 1, 1, 30.0),
            BeltCase(BeltTier.TURBO_TRANSPORT_BELT, 2, 1, 60.0),
            // stacking multiplies the throughput
            BeltCase(BeltTier.TRANSPORT_BELT, 1, 4, 30.0),
        ) { (belt, lanes, stack, expected) ->
            assertEquals(
                expected,
                calculator(
                        LogisticsCalculatorSelection(belt = belt, lanes = lanes, beltStack = stack),
                    )
                    .beltThroughputAtOwnStack(),
                TOLERANCE,
            )
        }

    @Test
    fun inserterThroughputPerTierAndStacking() =
        assertTableOf(
            Triple(InserterTier.INSERTER, 1, 0.84), // 0.014 turns/tick * 60 * 1
            Triple(InserterTier.FAST_INSERTER, 1, 2.4),
            Triple(InserterTier.STACK_INSERTER, 6, 14.4), // 0.04 * 60 * 6 (its base hand size)
            Triple(InserterTier.BURNER_INSERTER, 1, 0.78),
        ) { (inserter, stack, expected) ->
            assertEquals(
                expected,
                calculator(LogisticsCalculatorSelection(inserter = inserter, inserterStack = stack))
                    .inserterThroughput(),
                TOLERANCE,
            )
        }

    @Test
    fun placingThroughputIsBeltLimitedAndStackingAware() =
        assertAll(
            // 8 hand items placed one at a time on a turbo belt: 25 arm ticks + 8 * 2 slot ticks
            {
                assertEquals(
                    8 * 60.0 / 41.0,
                    calculator(
                            LogisticsCalculatorSelection(
                                belt = BeltTier.TURBO_TRANSPORT_BELT,
                                inserter = InserterTier.BULK_INSERTER,
                                inserterStack = 8,
                            ),
                        )
                        .placingThroughput(),
                    TOLERANCE,
                )
            },
            // the same hand as whole stacks of the belt's four items only needs two slots
            {
                assertEquals(
                    8 * 60.0 / 29.0,
                    calculator(
                            LogisticsCalculatorSelection(
                                belt = BeltTier.TURBO_TRANSPORT_BELT,
                                inserter = InserterTier.STACK_INSERTER,
                                inserterStack = 8,
                                beltStack = 4,
                            ),
                        )
                        .placingThroughput(),
                    TOLERANCE,
                )
            },
        )

    @Test
    fun aStackInserterPlacesFasterThanABulkInserterWithTheSameHand() {
        val bulk =
            calculator(
                LogisticsCalculatorSelection(
                    belt = BeltTier.EXPRESS_TRANSPORT_BELT,
                    inserter = InserterTier.BULK_INSERTER,
                    inserterStack = 8,
                    beltStack = 4,
                ),
            )
        val stack =
            calculator(
                LogisticsCalculatorSelection(
                    belt = BeltTier.EXPRESS_TRANSPORT_BELT,
                    inserter = InserterTier.STACK_INSERTER,
                    inserterStack = 8,
                    beltStack = 4,
                ),
            )

        assertTrue(stack.placingThroughput() > bulk.placingThroughput())
    }

    @Test
    fun aBulkInserterPlacesOneItemPerSlotSoBeltStackingDoesNotHelp() =
        assertAll(
            {
                assertEquals(
                    calculator(
                            LogisticsCalculatorSelection(
                                inserter = InserterTier.BULK_INSERTER,
                                inserterStack = 4,
                            ),
                        )
                        .placingThroughput(),
                    calculator(
                            LogisticsCalculatorSelection(
                                inserter = InserterTier.BULK_INSERTER,
                                inserterStack = 4,
                                beltStack = 4,
                            ),
                        )
                        .placingThroughput(),
                    TOLERANCE,
                )
            },
            // a slower belt makes the inserter wait longer between placements
            {
                assertTrue(
                    calculator(
                            LogisticsCalculatorSelection(
                                belt = BeltTier.TURBO_TRANSPORT_BELT,
                                inserter = InserterTier.BULK_INSERTER,
                                inserterStack = 4,
                            ),
                        )
                        .placingThroughput() >
                        calculator(
                                LogisticsCalculatorSelection(
                                    belt = BeltTier.TRANSPORT_BELT,
                                    inserter = InserterTier.BULK_INSERTER,
                                    inserterStack = 4,
                                ),
                            )
                            .placingThroughput(),
                )
            },
            // placing onto a belt is never faster than moving between storages
            {
                val calculator =
                    calculator(
                        LogisticsCalculatorSelection(
                            inserter = InserterTier.BULK_INSERTER,
                            inserterStack = 4,
                        ),
                    )
                assertTrue(calculator.placingThroughput() <= calculator.inserterThroughput())
            },
        )

    @Test
    fun countsAreRatesDividedByThroughput() =
        assertAll(
            {
                assertTableOf(
                    Triple(1, 15.0, 1.0),
                    Triple(1, 30.0, 2.0),
                    Triple(2, 60.0, 2.0),
                ) { (lanes, rate, expected) ->
                    assertEquals(
                        expected,
                        calculator(LogisticsCalculatorSelection(lanes = lanes))
                            .beltCountAtOwnStack(rate),
                        TOLERANCE,
                    )
                }
            },
            {
                assertEquals(
                    5.0,
                    calculator(LogisticsCalculatorSelection(inserter = InserterTier.FAST_INSERTER))
                        .inserterCount(12.0),
                    TOLERANCE,
                )
            },
            { assertEquals(1.0, calculator().pumpCount(1200.0), TOLERANCE) },
        )

    @Test
    fun throughputComesFromTheDump() {
        val logistics =
            Logistics(
                belts = mapOf("transport-belt" to 0.05),
                inserters =
                    mapOf(
                        "inserter" to
                            InserterStats(
                                0.03,
                                minStack = 1,
                                maxStack = 3,
                                stacksOnBelt = false,
                            ),
                    ),
                pumpRate = 600.0,
            )
        val calculator =
            LogisticsCalculator(
                logistics,
                LogisticsCalculatorSelection(
                    belt = BeltTier.TRANSPORT_BELT,
                    inserter = InserterTier.INSERTER,
                    lanes = 1,
                ),
            )

        assertAll(
            // 0.05 tiles/tick * 60 * 4 (one lane)
            { assertEquals(12.0, calculator.beltThroughputAtOwnStack(), TOLERANCE) },
            // 0.03 turns/tick * 60
            { assertEquals(1.8, calculator.inserterThroughput(), TOLERANCE) },
            { assertEquals(600.0, calculator.pumpThroughput(), TOLERANCE) },
        )
    }
}
