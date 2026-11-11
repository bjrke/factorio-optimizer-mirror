package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The guard that keeps a band from internalizing a product another plan row still needs. */
class FeedCandidatesTest {

    private val logistics = calculator()

    private fun production(recipe: Recipe, material: Material) =
        FactoryProduction(material, recipe, Factory.ASSEMBLING_MACHINE_3, 1.0, 0)

    private fun factories(vararg productions: FactoryProduction) = productions.associateBy {
        it.recipe
    }

    @Test
    fun aSharedProductCannotBeInternalizedWhileAnotherConsumerNeedsIt() {
        val processing = production(processingUnitRecipe, processingUnit)
        val advanced = production(advancedCircuitRecipe, advancedCircuit)
        val circuit = production(electronicCircuitRecipe, electronicCircuit)
        val cable = production(copperCableRecipe, copperCable)
        val context =
            DirectFeedContext(
                processing,
                factories(processing, advanced, circuit, cable),
                logistics,
            )
        val intoCircuit = context.feedLink(cable, circuit)
        val intoAdvanced = context.feedLink(circuit, advanced)

        assertAll(
            // the processing row also takes circuits, so internalizing the circuit producer leaves
            // its other consumer without the belt source it expects
            { assertTrue(context.internalizesSharedOutput(listOf(intoCircuit), intoAdvanced)) },
            // a group that already contains the candidate hides that consumer, so the swap path
            // has to test the group without the candidate
            {
                assertFalse(
                    context.internalizesSharedOutput(
                        listOf(intoCircuit, intoAdvanced),
                        intoAdvanced,
                    ),
                )
            },
        )
    }
}
