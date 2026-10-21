package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.model.InfinitySettings
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.RequestFilter

data class MachineEntity(
    override val area: Area,
    override val name: String,
    val recipe: String,
) : LayoutEntity {
    override val isPowered = true

    override fun withArea(area: Area) = copy(area = area)
}

/** A chest the layout places as a belt end marker: its [area] is always one tile. */
sealed interface ChestLikeEntity : LayoutEntity

data class InfinityChestEntity(
    override val area: Area,
    override val name: String,
    val settings: InfinitySettings,
) : ChestLikeEntity {
    override fun withArea(area: Area) = copy(area = area)
}

data class ChestEntity(
    override val area: Area,
    override val name: String,
    /** The logistic request it keeps in stock (a buffer/storage chest). */
    val requestFilter: RequestFilter? = null,
    /** The inventory bar limit (a wooden/steel chest). */
    val bar: Int? = null,
    /** Whether its own circuit condition is enabled, if it is set at all. */
    val circuitConditionEnabled: Boolean? = null,
) : ChestLikeEntity {
    override fun withArea(area: Area) = copy(area = area)
}

data class MarkerEntity(
    override val area: Area,
    override val name: String,
    val signal: Signal,
) : LayoutEntity {
    override fun withArea(area: Area) = copy(area = area)
}

data class PipeEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
) : Plumbing {
    override fun withArea(area: Area) = copy(area = area)
}

/**
 * A cheat fluid source: an infinity pipe that keeps [settings]' fluid at 100%. It connects to pipes
 * on any side just like a plain pipe, so it stands in for a fluid import marker.
 */
data class InfinityPipeEntity(
    override val area: Area,
    override val name: String,
    val settings: InfinityPipeSettings,
) : Plumbing {
    override val material: Material
        get() = settings.fluid

    override fun withArea(area: Area) = copy(area = area)
}

data class PipeToGroundEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
    /**
     * Input or output end of the run; a blueprint carries only the direction, so this is internal.
     */
    override val io: Io,
) : Plumbing, UndergroundEntity {
    override fun withArea(area: Area) = copy(area = area)
}

data class PoleEntity(
    override val area: Area,
    override val name: String,
    /** The `entity_number`s this pole is wired to; a blueprint does not rebuild wires by itself. */
    val neighbours: List<Int> = emptyList(),
) : LayoutEntity {
    override fun withArea(area: Area) = copy(area = area)
}
