package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.InserterStats
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.Logistics
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
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

private data class BeltCase(
    val belt: BeltTier,
    val lanes: Int,
    val stack: Int,
    val expected: Double,
)

class LogisticsTest {

    private fun calculator(
        belt: BeltTier = BeltTier.TRANSPORT_BELT,
        inserter: InserterTier = InserterTier.INSERTER,
        lanes: Int = 1,
        beltStack: Int = 1,
        inserterStack: Int = 1,
        logistics: Logistics = testLogistics,
    ) = LogisticsCalculator(logistics, belt, inserter, lanes, beltStack, inserterStack)

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
                calculator(belt = belt, lanes = lanes, beltStack = stack).beltThroughput(),
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
                            belt = BeltTier.TURBO_TRANSPORT_BELT,
                            inserter = InserterTier.BULK_INSERTER,
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
                            belt = BeltTier.TURBO_TRANSPORT_BELT,
                            inserter = InserterTier.STACK_INSERTER,
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
                belt = BeltTier.EXPRESS_TRANSPORT_BELT,
                inserter = InserterTier.BULK_INSERTER,
                inserterStack = 8,
                beltStack = 4,
            )
        val stack =
            calculator(
                belt = BeltTier.EXPRESS_TRANSPORT_BELT,
                inserter = InserterTier.STACK_INSERTER,
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
                    calculator(
                            inserter = InserterTier.BULK_INSERTER,
                            inserterStack = 4,
                            beltStack = 1,
                        )
                        .placingThroughput(),
                    calculator(
                            inserter = InserterTier.BULK_INSERTER,
                            inserterStack = 4,
                            beltStack = 4,
                        )
                        .placingThroughput(),
                    TOLERANCE,
                )
            },
            // a slower belt makes the inserter wait longer between placements
            {
                assertTrue(
                    calculator(
                            belt = BeltTier.TURBO_TRANSPORT_BELT,
                            inserter = InserterTier.BULK_INSERTER,
                            inserterStack = 4,
                        )
                        .placingThroughput() >
                        calculator(
                                belt = BeltTier.TRANSPORT_BELT,
                                inserter = InserterTier.BULK_INSERTER,
                                inserterStack = 4,
                            )
                            .placingThroughput(),
                )
            },
            // placing onto a belt is never faster than moving between storages
            {
                val calculator =
                    calculator(inserter = InserterTier.BULK_INSERTER, inserterStack = 4)
                assertTrue(calculator.placingThroughput() <= calculator.inserterThroughput())
            },
        )

    @Test
    fun countsAreRatesDividedByThroughput() =
        assertAll(
            {
                assertTableOf(
                    Triple(1, 7.5, 1.0),
                    Triple(1, 15.0, 2.0),
                    Triple(2, 30.0, 2.0),
                ) { (lanes, rate, expected) ->
                    assertEquals(expected, calculator(lanes = lanes).beltCount(rate), TOLERANCE)
                }
            },
            {
                assertEquals(
                    5.0,
                    calculator(inserter = InserterTier.FAST_INSERTER).inserterCount(12.0),
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
                            ),
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
        assertTableOf(
            InserterTier.INSERTER to false,
            InserterTier.FAST_INSERTER to false,
            // the bulk inserter holds several items but cannot stack them on a belt
            InserterTier.BULK_INSERTER to false,
            InserterTier.STACK_INSERTER to true,
        ) { (inserter, expected) ->
            assertEquals(expected, calculator(inserter = inserter).stacksOnBelt())
        }

    @Test
    fun bulkAndStackInsertersAlwaysCarryTheirMinimum() =
        assertAll(
            {
                // a requested stack below the hand size is raised to the base hand size
                assertTableOf(
                    InserterTier.BULK_INSERTER to 2,
                    InserterTier.STACK_INSERTER to 6,
                    // a plain inserter keeps the requested stack
                    InserterTier.INSERTER to 1,
                ) { (inserter, handSize) ->
                    assertEquals(
                        handSize,
                        calculator(inserter = inserter, inserterStack = 1).handSize,
                    )
                }
            },
            {
                // the raised stack lowers the required inserter count
                assertEquals(
                    1.0,
                    calculator(inserter = InserterTier.BULK_INSERTER, inserterStack = 1)
                        .inserterCount(4.8),
                    TOLERANCE,
                )
            },
        )

    @Test
    fun inserterStackConfigKeepsWithinTheInsertersRange() {
        val config = LogisticsConfig(testLogistics, spaceAge = true)

        assertTableOf<Triple<String?, InserterTier, Int>>(
            Triple("1", InserterTier.BULK_INSERTER, 2),
            Triple(null, InserterTier.STACK_INSERTER, 6),
            Triple("99", InserterTier.BULK_INSERTER, 12),
            Triple(null, InserterTier.INSERTER, 1),
            Triple("99", InserterTier.STACK_INSERTER, 16),
        ) { (stack, inserter, expected) ->
            assertEquals(expected, config.inserterStack(stack, inserter))
        }
    }

    @Test
    fun beltCountCanForceAnUnstackedBelt() {
        val calculator = calculator(lanes = 1, beltStack = 4)

        assertTableOf(
            4 to 1.0,
            // a belt that cannot be stacked carries four times the count
            1 to 4.0,
        ) { (stack, expected) ->
            assertEquals(expected, calculator.beltCount(30.0, stack), TOLERANCE)
        }
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
                            ),
                    ),
            )
        val config = LogisticsConfig(partial, spaceAge = true)

        assertAll(
            { assertEquals(listOf(BeltTier.TRANSPORT_BELT), config.availableBelts) },
            { assertEquals(listOf(InserterTier.INSERTER), config.availableInserters) },
        )
    }

    @Test
    fun beltLanesDefaultToHalfAndTheFullBeltFlagSelectsBoth() {
        val config = LogisticsConfig(testLogistics, spaceAge = true)

        assertTableOf<Pair<String?, Int>>(
            null to 1,
            "tx" to 1,
            "L" to 2,
            "txL" to 2,
        ) { (selection, expected) ->
            assertEquals(expected, config.beltLanes(selection))
        }
    }

    @Test
    fun beltAndInserterArePickedFromTheSelectionString() {
        val config = LogisticsConfig(testLogistics, spaceAge = true)

        assertAll(
            {
                // only the tier's own code matters, the surrounding letters are ignored
                assertTableOf(
                    "a3t" to BeltTier.TRANSPORT_BELT,
                    "a3e" to BeltTier.EXPRESS_TRANSPORT_BELT,
                ) { (selection, expected) ->
                    assertEquals(expected, config.belt(selection))
                }
            },
            {
                assertTableOf(
                    "a3i" to InserterTier.INSERTER,
                    "a3z" to InserterTier.STACK_INSERTER,
                ) { (selection, expected) ->
                    assertEquals(expected, config.inserter(selection))
                }
            },
            // the red belt and the fast inserter are the defaults and carry no code
            { assertEquals(BeltTier.FAST_TRANSPORT_BELT, config.defaultBelt) },
            { assertEquals(InserterTier.FAST_INSERTER, config.defaultInserter) },
            // absent falls back to the default tier
            { assertEquals(config.defaultBelt, config.belt(null)) },
            { assertEquals(config.defaultInserter, config.inserter(null)) },
        )
    }

    @Test
    fun beltStackingIsLimitedToSpaceAge() {
        val spaceAge = LogisticsConfig(testLogistics, spaceAge = true)
        val base = LogisticsConfig(testLogistics, spaceAge = false)

        assertAll(
            { assertEquals(BeltTier.MAX_STACK, spaceAge.beltStackMax) },
            {
                // only the Space Age stack inserter builds whole stacks on a belt
                assertTableOf(
                    InserterTier.FAST_INSERTER to 1,
                    InserterTier.STACK_INSERTER to BeltTier.MIN_STACK_INSERTER_STACK,
                ) { (inserter, expected) ->
                    assertEquals(expected, spaceAge.beltStackMin(inserter))
                }
            },
            {
                // an unstacked belt is the default; selecting the stack inserter lifts the level
                assertTableOf<Triple<String?, InserterTier, Int>>(
                    Triple(null, InserterTier.FAST_INSERTER, 1),
                    Triple("1", InserterTier.FAST_INSERTER, 1),
                    Triple("1", InserterTier.STACK_INSERTER, BeltTier.MIN_STACK_INSERTER_STACK),
                    Triple(null, InserterTier.STACK_INSERTER, BeltTier.MIN_STACK_INSERTER_STACK),
                    Triple("3", InserterTier.STACK_INSERTER, 3),
                    Triple("9", InserterTier.STACK_INSERTER, BeltTier.MAX_STACK),
                ) { (stack, inserter, expected) ->
                    assertEquals(expected, spaceAge.beltStack(stack, inserter))
                }
            },
            // belt stacking needs the Space Age stack inserters
            { assertEquals(1, base.beltStackMax) },
            { assertEquals(1, base.beltStackMin(InserterTier.STACK_INSERTER)) },
            { assertEquals(1, base.beltStack("4", InserterTier.STACK_INSERTER)) },
        )
    }

    @Test
    fun fluidsAreDetectedFromTheDump() {
        val water = Material("water")
        val plate = Material("iron-plate")
        val calculator = calculator(logistics = Logistics(fluids = setOf(water)))

        assertTableOf(water to true, plate to false) { (material, expected) ->
            assertEquals(expected, calculator.isFluid(material))
        }
    }
}
