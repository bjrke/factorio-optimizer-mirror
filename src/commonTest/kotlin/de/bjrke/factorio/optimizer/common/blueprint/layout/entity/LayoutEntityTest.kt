package de.bjrke.factorio.optimizer.common.blueprint.layout.entity

import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.area
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
                Coordinate(2, 3).area(2, 2),
                "iron-plate",
            ) to false,
            MachineEntity(
                Factory.BIOCHAMBER,
                Coordinate(2, 3).area(3, 3),
                "bioflux",
            ) to false,
            MachineEntity(
                Factory.ASSEMBLING_MACHINE_3,
                Coordinate(2, 3).area(3, 3),
                "iron-gear-wheel",
            ) to true,
        ) { (machine, powered) ->
            assertEquals(powered, machine.isPowered, machine.name)
        }
}
