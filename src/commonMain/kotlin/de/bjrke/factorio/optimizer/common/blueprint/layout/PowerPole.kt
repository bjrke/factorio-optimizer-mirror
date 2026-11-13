package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Surface

/**
 * The electric pole a blueprint uses. The prototype dump does not carry electric poles, so the
 * supply radius and wire reach are curated constants: a small pole supplies a 5x5 area (radius 2)
 * and reaches 7.5 tiles, a medium pole a 7x7 area (radius 3) and 9 tiles.
 *
 * [NONE] is a space platform, which powers its machines without poles.
 */
enum class PowerPole(val identifier: String, val supplyRadius: Int, val wireReach: Double) {
    NONE("", 0, 0.0),
    SMALL_ELECTRIC_POLE("small-electric-pole", 2, 7.5),
    MEDIUM_ELECTRIC_POLE("medium-electric-pole", 3, 9.0);

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
