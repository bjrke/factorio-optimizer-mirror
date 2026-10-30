package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.fluidNetworkProblems
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
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
            assertEquals(emptyList(), plumbing)
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
            assertEquals(
                emptyList(),
                fluidNetworkProblems(Layout(target).entities),
            )
        }
}
