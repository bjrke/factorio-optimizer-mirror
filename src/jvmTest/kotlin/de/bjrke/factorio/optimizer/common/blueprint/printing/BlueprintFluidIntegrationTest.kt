package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidNetworkProblems
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plumbing validity and fluid network connectivity on the real prototype dump. */
class BlueprintFluidIntegrationTest {

    @Test
    fun realBlueprintsHaveValidPlumbing() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
            "plastic-bar",
            "petroleum-gas",
        ) { target ->
            val layout = Layout(target)
            // only the fluid checks here: utility-science-pack has a pre-existing belt side-load
            val plumbing =
                validateLayout(layout.entities).filter {
                    it.contains("fluid") || it.contains("unpaired")
                }
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(emptyList(), plumbing) },
            )
        }

    @Test
    fun realFluidNetworksAreConnected() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
            "plastic-bar",
            "petroleum-gas",
        ) { target ->
            val layout = Layout(target)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(emptyList(), fluidNetworkProblems(layout.entities)) },
            )
        }
}
