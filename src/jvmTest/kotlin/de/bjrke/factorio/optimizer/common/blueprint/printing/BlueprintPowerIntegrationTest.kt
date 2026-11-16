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
import kotlin.test.assertTrue

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

    /**
     * The reported splitter URLs: excluding copper ore does not change the blueprint because the
     * plan imports it either way. A one-ULP machine count used to draw a third band inserter and
     * move the poles.
     */
    @Test
    fun theReportedSplitterUrlsDrawTheSamePoles() {
        val ironOnly =
            layoutFromUrl("http://localhost:8080/#/1.8/splitter/CFSXbmr/1/1/10/iron-plate")
        val withCopperOre =
            layoutFromUrl(
                "http://localhost:8080/#/1.8/splitter/CFSXbmr/1/1/10/copper-ore,iron-plate",
            )
        val entities = ironOnly.blueprint.entities

        assertAll(
            { assertEquals(emptyList(), ironOnly.failures) },
            { assertEquals(emptyList(), withCopperOre.failures) },
            { assertEquals(ironOnly.blueprint, withCopperOre.blueprint) },
            { assertPolesCoverAndConnect(entities) },
            { assertPolesWired(entities) },
        )
    }

    /**
     * A biochamber runs on nutrients instead of electricity, so its body needs no pole; the
     * inserters around it still do.
     */
    @Test
    fun glebaBiochambersDoNotNeedPower() {
        val layout = Layout("bioflux", surface = Surface.GLEBA)
        val entities = layout.blueprint.entities

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertPolesCoverAndConnect(entities) },
            { assertPolesWired(entities) },
            {
                assertTrue(
                    layout.machineEntities.none { it.isPowered },
                    "a biochamber body must not require a pole",
                )
            },
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
