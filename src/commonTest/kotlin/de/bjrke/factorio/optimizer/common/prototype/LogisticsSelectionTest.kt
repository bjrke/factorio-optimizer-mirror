package de.bjrke.factorio.optimizer.common.prototype

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.prototype.selection.fullBeltIn
import de.bjrke.factorio.optimizer.common.service.fixtures.testLogisticsConfig
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the dump and the selection string make available: tiers, lanes, stacking and fluids. */
class LogisticsSelectionTest {

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
                                minStack = 1,
                                maxStack = 3,
                                stacksOnBelt = false,
                            ),
                    ),
            )
        val config = LogisticsConfig(partial)

        assertAll(
            { assertEquals(listOf(BeltTier.TRANSPORT_BELT), config.availableBelts) },
            { assertEquals(listOf(InserterTier.INSERTER), config.availableInserters) },
        )
    }

    @Test
    fun beltLanesDefaultToHalfAndTheFullBeltFlagSelectsBoth() {
        assertTableOf(
            null to 1,
            "tx" to 1,
            "L" to 2,
            "txL" to 2,
        ) { (selection, expected) ->
            assertEquals(expected, testLogisticsConfig.beltLanes(fullBeltIn(selection)))
        }
    }

    @Test
    fun beltAndInserterArePickedFromTheSelectionString() {
        assertAll(
            {
                // only the tier's own code matters, the surrounding letters are ignored
                assertTableOf(
                    "a3t" to BeltTier.TRANSPORT_BELT,
                    "a3e" to BeltTier.EXPRESS_TRANSPORT_BELT,
                ) { (selection, expected) ->
                    assertEquals(expected, testLogisticsConfig.belt(selection))
                }
            },
            {
                assertTableOf(
                    "a3i" to InserterTier.INSERTER,
                    "a3z" to InserterTier.STACK_INSERTER,
                ) { (selection, expected) ->
                    assertEquals(expected, testLogisticsConfig.inserter(selection))
                }
            },
            // the red belt and the fast inserter are the defaults and carry no code
            { assertEquals(BeltTier.FAST_TRANSPORT_BELT, testLogisticsConfig.defaultBelt) },
            { assertEquals(InserterTier.FAST_INSERTER, testLogisticsConfig.defaultInserter) },
            // absent falls back to the default tier
            { assertEquals(testLogisticsConfig.defaultBelt, testLogisticsConfig.belt(null)) },
            {
                assertEquals(
                    testLogisticsConfig.defaultInserter,
                    testLogisticsConfig.inserter(null),
                )
            },
        )
    }

    @Test
    fun fluidsAreDetectedFromTheDump() {
        val water = Material("water")
        val plate = Material("iron-plate")
        val calculator = LogisticsCalculator(logistics = Logistics(fluids = setOf(water)))

        assertTableOf(water to true, plate to false) { (material, expected) ->
            assertEquals(expected, calculator.isFluid(material))
        }
    }
}
