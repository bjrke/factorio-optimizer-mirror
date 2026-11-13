package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Surface

/**
 * The electric pole a blueprint uses. Its supply area and wire reach come from the `electric-pole`
 * prototype (`Logistics.poles`): the selection here only names the prototype.
 *
 * [NONE] is a space platform, which powers its machines without poles.
 */
enum class PowerPole(val identifier: String) {
    NONE(""),
    SMALL_ELECTRIC_POLE("small-electric-pole"),
    MEDIUM_ELECTRIC_POLE("medium-electric-pole");

    companion object {
        /**
         * The pole a blueprint uses for [surface] and the selected [factories]: none in space, the
         * small pole for an early-game assembling machine 1 on Nauvis, the medium pole otherwise.
         */
        fun forSelection(surface: Surface, factories: Set<Factory>) =
            when {
                surface == Surface.SPACE -> NONE
                surface == Surface.NAUVIS && Factory.ASSEMBLING_MACHINE_1 in factories ->
                    SMALL_ELECTRIC_POLE
                else -> MEDIUM_ELECTRIC_POLE
            }
    }
}
