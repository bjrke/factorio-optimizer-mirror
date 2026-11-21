package de.bjrke.factorio.optimizer.common.blueprint.layout.entity

import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutEntityTest {

    /** A burner machine draws no power, so the pole pass must not cover its body. */
    @Test
    fun machinePowerNeedFollowsTheFactory() =
        assertTableOf(
            MachineEntity(
                Factory.STONE_FURNACE,
                Area(2, 3, 2, 2),
                "iron-plate",
            ) to false,
            MachineEntity(
                Factory.BIOCHAMBER,
                Area(2, 3, 3, 3),
                "bioflux",
            ) to false,
            MachineEntity(
                Factory.ASSEMBLING_MACHINE_3,
                Area(2, 3, 3, 3),
                "iron-gear-wheel",
            ) to true,
        ) { (machine, powered) ->
            assertEquals(powered, machine.isPowered, machine.name)
        }
}
