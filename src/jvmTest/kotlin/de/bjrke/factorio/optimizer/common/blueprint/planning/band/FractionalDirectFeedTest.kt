package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.splitter
import de.bjrke.factorio.optimizer.common.fixtures.splitterRecipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

private val DirectFeed.bands
    get() = rows.filter { it.links.isNotEmpty() }

private fun production(
    recipe: Recipe,
    material: Material,
    amount: Double = 1.0,
) =
    FactoryProduction(
        material,
        recipe,
        Factory.ASSEMBLING_MACHINE_3,
        amount,
        0,
        1.0,
        0.0,
    )

private fun factories(vararg productions: FactoryProduction) = productions.associateBy {
    it.recipe
}

/** Fractional machine needs, overfeed and the repeated windows they produce. */
class FractionalDirectFeedTest {

    private val logistics = calculator()

    @Test
    fun aFractionalChainUsesOneMachinePerRecipe() {
        val target = production(splitterRecipe, splitter, amount = 0.1)
        val belt = production(transportBeltRecipe, transportBelt, amount = 0.1)
        val gear = production(ironGearWheelRecipe, gear, amount = 0.1)
        val circuit = production(electronicCircuitRecipe, electronicCircuit, amount = 0.1)
        val cable = production(copperCableRecipe, copperCable, amount = 0.1)
        val feed =
            directFeed(
                target,
                factories(target, belt, gear, circuit, cable),
                scale = 1.0,
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
        val circuit = production(electronicCircuitRecipe, electronicCircuit, amount = 1.0)
        val cable = production(copperCableRecipe, copperCable, amount = 2.0)
        val feed =
            directFeed(
                circuit,
                factories(circuit, cable),
                scale = 1.0,
                logistics = logistics,
            )

        assertAll(
            {
                assertEquals(
                    mapOf("copper-cable" to 2, "electronic-circuit" to 1),
                    feed.bands.single().machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            // two cable machines feed one circuit machine; overfeed is allowed
            {
                assertEquals(
                    List(2) { copperCable },
                    feed.bands.single().links.map { it.material },
                )
            },
        )
    }

    @Test
    fun sixCableMachinesFeedFourCircuitsInARepeatedThreeToTwoWindow() {
        val circuit = production(electronicCircuitRecipe, electronicCircuit, amount = 4.0)
        val cable = production(copperCableRecipe, copperCable, amount = 6.0)
        val feed =
            directFeed(
                circuit,
                factories(circuit, cable),
                scale = 1.0,
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
                    band.machines.map { it.recipe },
                )
            },
            { assertEquals(List(8) { copperCable }, band.links.map { it.material }) },
        )
    }
}
