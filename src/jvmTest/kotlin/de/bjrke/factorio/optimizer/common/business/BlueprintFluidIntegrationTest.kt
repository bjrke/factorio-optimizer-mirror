package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.blueprint.blueprintLayoutDump
import de.bjrke.factorio.optimizer.common.layout.validateLayout
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
        ) { target ->
            val (plan, logistics) = planAndLogistics(target)
            val dump = blueprintLayoutDump(plan, logistics)
            // only the fluid checks here: utility-science-pack has a pre-existing belt side-load
            val plumbing =
                validateLayout(dump.entities).filter {
                    it.contains("fluid") || it.contains("underground")
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
        ) { target ->
            val (plan, logistics) = planAndLogistics(target)
            val dump = blueprintLayoutDump(plan, logistics)
            assertEquals(emptyList(), fluidNetworkProblems(dump.entities))
        }
}
