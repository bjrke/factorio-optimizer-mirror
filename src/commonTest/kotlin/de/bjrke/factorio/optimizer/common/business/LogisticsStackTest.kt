package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** How the inserter hand and the belt stack size behave, per tier and Space Age. */
class LogisticsStackTest {

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
}
