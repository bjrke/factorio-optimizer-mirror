package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidNetworkProblems
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plumbing validity and fluid network connectivity on the real prototype dump. */
class BlueprintFluidIntegrationTest {

    /**
     * One layout per target feeds both fluid checks: plumbing validity and network connectivity.
     * Only the fluid findings count here, because `utility-science-pack` has a pre-existing belt
     * side-load.
     */
    @Test
    fun realBlueprintsHaveValidPlumbingAndConnectedNetworks() =
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
            val plumbing =
                validateLayout(layout.entities).filter {
                    it.contains("fluid") || it.contains("unpaired")
                }
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(emptyList(), plumbing) },
                { assertEquals(emptyList(), fluidNetworkProblems(layout.entities)) },
            )
        }

    @Test
    fun aFluidOnlyPlanOmitsTheUnusedBeltAndInserterIcons() {
        val layout = Layout("lubricant")
        val icons = layout.blueprint.icons

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertEquals(
                    listOf(lubricant.name, Factory.CHEMICAL_PLANT.identifier),
                    icons.map { it.name },
                )
            },
            { assertEquals(listOf(true, false), icons.map { it.fluid }) },
        )
    }
}
