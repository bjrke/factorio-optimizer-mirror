package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Belt
import de.bjrke.factorio.optimizer.common.model.Inserter
import de.bjrke.factorio.optimizer.common.model.InserterStats
import de.bjrke.factorio.optimizer.common.model.Logistics
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-9

/** The prototype stats the real dump provides, used as the stand-in in these tests. */
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
                        bulk = false,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                "inserter" to
                    InserterStats(
                        0.014,
                        bulk = false,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                "long-handed-inserter" to
                    InserterStats(
                        0.02,
                        bulk = false,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                "fast-inserter" to
                    InserterStats(
                        0.04,
                        bulk = false,
                        minStack = 1,
                        maxStack = 3,
                        stacksOnBelt = false,
                    ),
                // the bulk inserter holds several items but cannot stack them on a belt
                "bulk-inserter" to
                    InserterStats(
                        0.04,
                        bulk = true,
                        minStack = 2,
                        maxStack = 12,
                        stacksOnBelt = false,
                    ),
                "stack-inserter" to
                    InserterStats(
                        0.04,
                        bulk = true,
                        minStack = 6,
                        maxStack = 16,
                        stacksOnBelt = true,
                    ),
            ),
        pumpRate = 1200.0,
    )

private data class BeltCase(val belt: Belt, val lanes: Int, val stack: Int, val expected: Double)

class LogisticsTest {

    private fun calculator(
        belt: Belt = Belt.TRANSPORT_BELT,
        inserter: Inserter = Inserter.INSERTER,
        lanes: Int = 1,
        beltStack: Int = 1,
        inserterStack: Int = 1,
        logistics: Logistics = testLogistics,
    ) = LogisticsCalculator(logistics, belt, inserter, lanes, beltStack, inserterStack)

    @Test
    fun beltThroughputPerTierLanesAndStacking() =
        assertTableOf(
            // 0.03125 tiles/tick * 60 * 4 items per lane
            BeltCase(Belt.TRANSPORT_BELT, 1, 1, 7.5),
            BeltCase(Belt.TRANSPORT_BELT, 2, 1, 15.0),
            BeltCase(Belt.TURBO_TRANSPORT_BELT, 1, 1, 30.0),
            BeltCase(Belt.TURBO_TRANSPORT_BELT, 2, 1, 60.0),
            // stacking multiplies the throughput
            BeltCase(Belt.TRANSPORT_BELT, 1, 4, 30.0),
        ) { (belt, lanes, stack, expected) ->
            assertEquals(
                expected,
                calculator(belt = belt, lanes = lanes, beltStack = stack).beltThroughput(),
                TOLERANCE,
            )
        }

    @Test
    fun inserterThroughputPerTierAndStacking() =
        assertTableOf(
            Triple(Inserter.INSERTER, 1, 0.84), // 0.014 turns/tick * 60 * 1
            Triple(Inserter.FAST_INSERTER, 1, 2.4),
            Triple(Inserter.STACK_INSERTER, 6, 14.4), // 0.04 * 60 * 6 (its base hand size)
            Triple(Inserter.BURNER_INSERTER, 1, 0.78),
        ) { (inserter, stack, expected) ->
            assertEquals(
                expected,
                calculator(inserter = inserter, inserterStack = stack).inserterThroughput(),
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
                            belt = Belt.TURBO_TRANSPORT_BELT,
                            inserter = Inserter.BULK_INSERTER,
                            inserterStack = 8,
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
                            belt = Belt.TURBO_TRANSPORT_BELT,
                            inserter = Inserter.STACK_INSERTER,
                            inserterStack = 8,
                            beltStack = 4,
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
                belt = Belt.EXPRESS_TRANSPORT_BELT,
                inserter = Inserter.BULK_INSERTER,
                inserterStack = 8,
                beltStack = 4,
            )
        val stack =
            calculator(
                belt = Belt.EXPRESS_TRANSPORT_BELT,
                inserter = Inserter.STACK_INSERTER,
                inserterStack = 8,
                beltStack = 4,
            )

        assertTrue(stack.placingThroughput() > bulk.placingThroughput())
    }

    @Test
    fun aBulkInserterPlacesOneItemPerSlotSoBeltStackingDoesNotHelp() =
        assertAll(
            {
                assertEquals(
                    calculator(inserter = Inserter.BULK_INSERTER, inserterStack = 4, beltStack = 1)
                        .placingThroughput(),
                    calculator(inserter = Inserter.BULK_INSERTER, inserterStack = 4, beltStack = 4)
                        .placingThroughput(),
                    TOLERANCE,
                )
            },
            // a slower belt makes the inserter wait longer between placements
            {
                assertTrue(
                    calculator(
                            belt = Belt.TURBO_TRANSPORT_BELT,
                            inserter = Inserter.BULK_INSERTER,
                            inserterStack = 4,
                        )
                        .placingThroughput() >
                        calculator(
                                belt = Belt.TRANSPORT_BELT,
                                inserter = Inserter.BULK_INSERTER,
                                inserterStack = 4,
                            )
                            .placingThroughput()
                )
            },
            // placing onto a belt is never faster than moving between storages
            {
                val calculator = calculator(inserter = Inserter.BULK_INSERTER, inserterStack = 4)
                assertTrue(calculator.placingThroughput() <= calculator.inserterThroughput())
            },
        )

    @Test
    fun countsAreRatesDividedByThroughput() =
        assertAll(
            { assertEquals(1.0, calculator(lanes = 1).beltCount(7.5), TOLERANCE) },
            { assertEquals(2.0, calculator(lanes = 1).beltCount(15.0), TOLERANCE) },
            { assertEquals(2.0, calculator(lanes = 2).beltCount(30.0), TOLERANCE) },
            {
                assertEquals(
                    5.0,
                    calculator(inserter = Inserter.FAST_INSERTER).inserterCount(12.0),
                    TOLERANCE,
                )
            },
            { assertEquals(1.0, calculator().pumpCount(1200.0)!!, TOLERANCE) },
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
                                bulk = false,
                                minStack = 1,
                                maxStack = 3,
                                stacksOnBelt = false,
                            )
                    ),
                pumpRate = 600.0,
            )
        val calculator = calculator(logistics = logistics)

        assertAll(
            // 0.05 tiles/tick * 60 * 4 (one lane)
            { assertEquals(12.0, calculator.beltThroughput(), TOLERANCE) },
            // 0.03 turns/tick * 60
            { assertEquals(1.8, calculator.inserterThroughput(), TOLERANCE) },
            { assertEquals(600.0, calculator.pumpThroughput()!!, TOLERANCE) },
        )
    }

    @Test
    fun onlyTheStackInserterStacksOnBelts() =
        assertAll(
            { assertFalse(calculator(inserter = Inserter.INSERTER).stacksOnBelt()) },
            { assertFalse(calculator(inserter = Inserter.FAST_INSERTER).stacksOnBelt()) },
            // the bulk inserter holds several items but cannot stack them on a belt
            { assertFalse(calculator(inserter = Inserter.BULK_INSERTER).stacksOnBelt()) },
            { assertTrue(calculator(inserter = Inserter.STACK_INSERTER).stacksOnBelt()) },
        )

    @Test
    fun bulkAndStackInsertersAlwaysCarryTheirMinimum() =
        assertAll(
            // a requested stack below the hand size is raised to the base hand size
            {
                assertEquals(
                    2,
                    calculator(inserter = Inserter.BULK_INSERTER, inserterStack = 1).handSize,
                )
            },
            {
                assertEquals(
                    6,
                    calculator(inserter = Inserter.STACK_INSERTER, inserterStack = 1).handSize,
                )
            },
            // a plain inserter keeps the requested stack
            { assertEquals(1, calculator(inserterStack = 1).handSize) },
            // the raised stack lowers the required inserter count
            {
                assertEquals(
                    1.0,
                    calculator(inserter = Inserter.BULK_INSERTER, inserterStack = 1)
                        .inserterCount(4.8),
                    TOLERANCE,
                )
            },
        )

    @Test
    fun inserterStackConfigKeepsWithinTheInsertersRange() {
        val config = LogisticsConfig(testLogistics, spaceAge = true)

        assertAll(
            { assertEquals(2, config.inserterStack("1", Inserter.BULK_INSERTER)) },
            { assertEquals(6, config.inserterStack(null, Inserter.STACK_INSERTER)) },
            { assertEquals(12, config.inserterStack("99", Inserter.BULK_INSERTER)) },
            { assertEquals(1, config.inserterStack(null, Inserter.INSERTER)) },
            { assertEquals(16, config.inserterStack("99", Inserter.STACK_INSERTER)) },
        )
    }

    @Test
    fun beltCountCanForceAnUnstackedBelt() {
        val calculator = calculator(lanes = 1, beltStack = 4)

        assertAll(
            { assertEquals(1.0, calculator.beltCount(30.0, 4), TOLERANCE) },
            // a belt that cannot be stacked carries four times the count
            { assertEquals(4.0, calculator.beltCount(30.0, 1), TOLERANCE) },
        )
    }

    @Test
    fun prototypesMissingFromTheDumpAreNotUsable() {
        val partial =
            Logistics(
                belts = mapOf("transport-belt" to 0.03125),
                inserters =
                    mapOf(
                        "inserter" to
                            InserterStats(
                                0.014,
                                bulk = false,
                                minStack = 1,
                                maxStack = 3,
                                stacksOnBelt = false,
                            )
                    ),
            )
        val config = LogisticsConfig(partial, spaceAge = true)

        assertAll(
            { assertEquals(listOf(Belt.TRANSPORT_BELT), config.availableBelts) },
            { assertEquals(listOf(Inserter.INSERTER), config.availableInserters) },
        )
    }

    @Test
    fun beltLanesDefaultToHalfAndCanBeFull() {
        val config = LogisticsConfig(testLogistics, spaceAge = true)

        assertAll(
            { assertEquals(1, config.beltLanes(null)) },
            { assertEquals(2, config.beltLanes("2")) },
            { assertEquals(2, config.beltLanes("9")) },
            { assertEquals(1, config.beltLanes("0")) },
        )
    }

    @Test
    fun beltStackingIsLimitedToSpaceAge() {
        assertAll(
            {
                assertEquals(
                    Belt.MAX_STACK,
                    LogisticsConfig(testLogistics, spaceAge = true).beltStackMax,
                )
            },
            // belt stacking needs the Space Age stack inserters
            { assertEquals(1, LogisticsConfig(testLogistics, spaceAge = false).beltStackMax) },
            { assertEquals(1, LogisticsConfig(testLogistics, spaceAge = false).beltStack("4")) },
        )
    }

    @Test
    fun fluidsAreDetectedFromTheDump() {
        val water = Material("water")
        val plate = Material("iron-plate")
        val calculator = calculator(logistics = Logistics(fluids = setOf(water)))

        assertAll(
            { assertTrue(calculator.isFluid(water)) },
            { assertFalse(calculator.isFluid(plate)) },
        )
    }
}
