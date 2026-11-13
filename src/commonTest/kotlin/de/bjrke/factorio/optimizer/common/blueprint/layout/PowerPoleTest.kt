package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class PowerPoleTest {

    private val earlyGameFactories =
        FactoryConfig().defaults - Factory.ASSEMBLING_MACHINE_3 + Factory.ASSEMBLING_MACHINE_1

    @Test
    fun spacePlatformsNeedNoPoles() =
        assertTableOf(
            Surface.SPACE to FactoryConfig().defaults,
            Surface.SPACE to earlyGameFactories,
        ) { (surface, factories) ->
            assertEquals(PowerPole.NONE, PowerPole.forSelection(surface, factories))
        }

    @Test
    fun assemblingMachine1OnNauvisUsesSmallPoles() {
        assertEquals(
            PowerPole.SMALL_ELECTRIC_POLE,
            PowerPole.forSelection(Surface.NAUVIS, earlyGameFactories),
        )
    }

    @Test
    fun laterAssemblersKeepMediumPoles() =
        assertTableOf(
            Surface.NAUVIS to FactoryConfig().defaults,
            Surface.VULCANUS to earlyGameFactories,
        ) { (surface, factories) ->
            assertEquals(
                PowerPole.MEDIUM_ELECTRIC_POLE,
                PowerPole.forSelection(surface, factories),
            )
        }

    @Test
    fun smallPolesSupplyAndReachLessThanMediumPoles() =
        assertAll(
            { assertEquals(2, PowerPole.SMALL_ELECTRIC_POLE.supplyRadius) },
            { assertEquals(7.5, PowerPole.SMALL_ELECTRIC_POLE.wireReach) },
            { assertEquals(3, PowerPole.MEDIUM_ELECTRIC_POLE.supplyRadius) },
            { assertEquals(9.0, PowerPole.MEDIUM_ELECTRIC_POLE.wireReach) },
        )
}
