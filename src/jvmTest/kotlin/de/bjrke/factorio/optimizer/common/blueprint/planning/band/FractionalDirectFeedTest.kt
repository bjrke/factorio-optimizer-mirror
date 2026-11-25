package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.splitterRecipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

private val DirectFeed.bands
    get() = rows.filter { it.links.isNotEmpty() }

/** Fractional machine needs, overfeed and the repeated windows they produce. */
class FractionalDirectFeedTest : BaseGameCtxTest() {

    private val logistics = calculator()

    @Test
    fun aFractionalChainUsesOneMachinePerRecipe() {
        val target = production(splitterRecipe, amount = 0.1)
        val belt = production(transportBeltRecipe, amount = 0.1)
        val gear = production(ironGearWheelRecipe, amount = 0.1)
        val circuit = production(electronicCircuitRecipe, amount = 0.1)
        val cable = production(copperCableRecipe, amount = 0.1)
        val feed =
            directFeed(
                optimizationResult(target, belt, gear, circuit, cable),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    mapOf(
                        "copper-cable" to 1,
                        "electronic-circuit" to 1,
                        "iron-gear-wheel" to 1,
                        "splitter" to 1,
                        "transport-belt" to 1,
                    ),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            {
                assertEquals(
                    setOf(gear.material, electronicCircuit, transportBelt, copperCable),
                    band.links.map { it.material }.toSet(),
                )
            },
        )
    }

    @Test
    fun anOverfedPairIsBuiltInsteadOfUnderfeeding() {
        val circuit = production(electronicCircuitRecipe, amount = 1.0)
        val cable = production(copperCableRecipe, amount = 2.0)
        val feed = directFeed(optimizationResult(circuit, cable), logistics = logistics)

        assertAll(
            {
                assertEquals(
                    mapOf("copper-cable" to 2, "electronic-circuit" to 1),
                    feed.bands.single().machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            // two cable machines feed one circuit machine; overfeed is allowed
            {
                assertEquals(List(2) { copperCable }, feed.bands.single().links.map { it.material })
            },
        )
    }

    @Test
    fun sixCableMachinesFeedFourCircuitsInARepeatedThreeToTwoWindow() {
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val cable = production(copperCableRecipe, amount = 6.0)
        val feed =
            directFeed(
                optimizationResult(circuit, cable),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    mapOf("copper-cable" to 6, "electronic-circuit" to 4),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            {
                // the 6:4 need reduces to 3:2, repeated as a single window
                assertEquals(
                    listOf(
                        copperCableRecipe,
                        electronicCircuitRecipe,
                        copperCableRecipe,
                        electronicCircuitRecipe,
                        copperCableRecipe,
                        copperCableRecipe,
                        electronicCircuitRecipe,
                        copperCableRecipe,
                        electronicCircuitRecipe,
                        copperCableRecipe,
                    ),
                    band.machines.map { it.recipe.recipe },
                )
            },
            { assertEquals(List(8) { copperCable }, band.links.map { it.material }) },
        )
    }
}
