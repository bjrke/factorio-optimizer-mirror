package de.bjrke.factorio.optimizer.common.layout.entity

import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material

data class MachineEntity(
    override val area: Area,
    override val name: String,
    val recipe: String,
    /** The blueprint direction the machine is placed in; north is the prototype's own facing. */
    val direction: Direction = Direction.NORTH,
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
 * A cheat fluid marker: an infinity pipe carrying [material]. With [settings] it keeps that fluid
 * at 100% (a test source); without settings it has no filter and deletes whatever reaches it (a
 * test sink). It connects to pipes on any side just like a plain pipe, so it stands in for a fluid
 * marker.
 */
data class InfinityPipeEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    val settings: InfinityPipeSettings? = null,
) : Plumbing {
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

    /**
     * The side this end joins its surface pipe on. A pipe-to-ground connects only there: the other
     * sides are closed, which is why two different fluids may stand side by side.
     */
    val mouth: Direction
        get() = if (io == Io.INPUT) direction.opposite() else direction
}

data class PoleEntity(
    override val area: Area,
    override val name: String,
    /** The `entity_number`s this pole is wired to; a blueprint does not rebuild wires by itself. */
    val neighbours: List<Int> = emptyList(),
) : LayoutEntity {
    override fun withArea(area: Area) = copy(area = area)
}
