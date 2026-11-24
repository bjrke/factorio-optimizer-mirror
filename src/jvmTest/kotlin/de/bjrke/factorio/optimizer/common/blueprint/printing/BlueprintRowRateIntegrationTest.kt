package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.speedModule
import de.bjrke.factorio.optimizer.common.fixtures.steelPlate
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * The reported row-rate URLs: belts carry the plan's scaled demand, not every copy at full rate.
 */
class BlueprintRowRateIntegrationTest : BaseGameCtxTest() {

    /**
     * The target band repeats the machine, so its row rate must follow the plan's scaled demand. At
     * full per-machine rates the band needs five belt rows and loses the product; at the plan's
     * rates speed-module and steel-plate share one lane.
     */
    @Test
    fun theReportedAssemblingMachine3BandMergesSpeedAndSteel() {
        val layout =
            layoutFromUrl("http://localhost:8080/#/2.0/assembling-machine-3/CFSbmr/1/1/50/-")
        val mixed = layout.entities.filterIsInstance<BeltEntity>().map { it.load.materials.toSet() }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertContains(mixed, setOf(speedModule, steelPlate), mixed.toString()) },
        )
    }
}
