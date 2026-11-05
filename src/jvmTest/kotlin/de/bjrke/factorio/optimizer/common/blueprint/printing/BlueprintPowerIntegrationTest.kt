package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertPolesCoverAndConnect
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertPolesWired
import de.bjrke.test.assertTableOf
import kotlin.test.Test

/** Pole coverage, pole networks and the neighbour list a blueprint needs to rebuild wires. */
class BlueprintPowerIntegrationTest {

    @Test
    fun realBlueprintsPowerEveryMachine() =
        assertTableOf(
            "advanced-circuit" to 1.0,
            "electronic-circuit" to 1.0,
            "logistic-science-pack" to 1.0,
            "processing-unit" to 1.0,
            "utility-science-pack" to 1.0,
            "lubricant" to 1.0,
            "petroleum-gas" to 50.0,
        ) { (target, scale) ->
            val entities = Layout(target, scale).blueprint.entities
            assertPolesCoverAndConnect(entities)
            assertPolesWired(entities)
        }
}
