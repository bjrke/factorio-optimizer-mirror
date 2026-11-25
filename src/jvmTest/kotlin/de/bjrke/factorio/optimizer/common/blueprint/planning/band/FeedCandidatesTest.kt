package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The guard that keeps a band from internalizing a product another plan row still needs. */
class FeedCandidatesTest : BaseGameCtxTest() {

    private val logistics = calculator()

    @Test
    fun aSharedProductCannotBeInternalizedWhileAnotherConsumerNeedsIt() {
        val processing = production(processingUnitRecipe)
        val advanced = production(advancedCircuitRecipe)
        val circuit = production(electronicCircuitRecipe)
        val cable = production(copperCableRecipe)
        val context =
            DirectFeedContext(
                optimizationResult(processing, advanced, circuit, cable),
                logistics = logistics,
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
