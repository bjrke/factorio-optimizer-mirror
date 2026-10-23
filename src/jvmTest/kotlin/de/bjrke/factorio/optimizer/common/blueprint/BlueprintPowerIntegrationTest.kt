package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertPolesCoverAndConnect
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertPolesWired
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.entitiesFor
import de.bjrke.test.assertTableOf
import kotlin.test.Test

/** Pole coverage, pole networks and the neighbour list a blueprint needs to rebuild wires. */
class BlueprintPowerIntegrationTest {

    @Test
    fun realBlueprintsPowerEveryMachine() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
        ) {
            assertPolesCoverAndConnect(entitiesFor(it))
        }

    @Test
    fun realBlueprintsWireTheirPoles() =
        assertTableOf(
            "advanced-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
        ) {
            assertPolesWired(entitiesFor(it))
        }
}
