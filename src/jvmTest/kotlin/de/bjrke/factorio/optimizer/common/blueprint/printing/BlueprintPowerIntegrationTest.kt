package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertPolesCoverAndConnect
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertPolesWired
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

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
            val layout = Layout(target, scale)
            val entities = layout.blueprint.entities
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertPolesCoverAndConnect(entities) },
                { assertPolesWired(entities) },
            )
        }

    @Test
    fun assemblingMachine1UsesSmallPoles() {
        val factories =
            FactoryConfig().defaults - Factory.ASSEMBLING_MACHINE_3 + Factory.ASSEMBLING_MACHINE_1
        val layout = Layout("advanced-circuit", factories = factories)
        val entities = layout.blueprint.entities

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertPolesCoverAndConnect(entities, PowerPole.SMALL_ELECTRIC_POLE) },
            { assertPolesWired(entities, PowerPole.SMALL_ELECTRIC_POLE) },
            {
                assertEquals(
                    emptyList(),
                    entities.filter { it.name == PowerPole.MEDIUM_ELECTRIC_POLE.identifier },
                )
            },
        )
    }

    /**
     * The reported early furnace URL: the merged fuel/ore belt leaves a column for the small pole.
     */
    @Test
    fun theReportedCopperPlateUrlPowersEveryInserter() {
        val layout = layoutFromUrl("http://localhost:8080/#/7.5/copper-plate/1CEFSXbfirt/1/1/10/-")
        val entities = layout.blueprint.entities

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertPolesCoverAndConnect(entities, PowerPole.SMALL_ELECTRIC_POLE) },
            { assertPolesWired(entities, PowerPole.SMALL_ELECTRIC_POLE) },
        )
    }

    @Test
    fun spacePlatformsHaveNoPoles() {
        val layout = Layout("space-science-pack", surface = Surface.SPACE)
        val entities = layout.blueprint.entities

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertEquals(
                    emptyList(),
                    entities.filter { it.name.endsWith("electric-pole") },
                )
            },
        )
    }
}
