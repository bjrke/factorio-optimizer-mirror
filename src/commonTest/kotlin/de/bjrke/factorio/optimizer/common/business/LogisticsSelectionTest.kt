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
    fun fluidsAreDetectedFromTheDump() {
        val water = Material("water")
        val plate = Material("iron-plate")
        val calculator = calculator(logistics = Logistics(fluids = setOf(water)))

        assertTableOf(water to true, plate to false) { (material, expected) ->
            assertEquals(expected, calculator.isFluid(material))
        }
    }
}
