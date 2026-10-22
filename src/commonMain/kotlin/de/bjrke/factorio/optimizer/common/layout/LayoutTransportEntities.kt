package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.CircuitCondition
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material

data class BeltEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
) : BeltLikeEntity {
    override fun withArea(area: Area) = copy(area = area)
}

data class UndergroundBeltEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
    override val io: Io,
) : BeltLikeEntity, UndergroundEntity {
    override fun withArea(area: Area) = copy(area = area)
}

data class SplitterEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
) : ConveyorEntity {
    override fun withArea(area: Area) = copy(area = area)
}

data class InserterEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    val direction: Direction,
    /** Whether this is a long-handed inserter (reaches two tiles). */
    val long: Boolean,
    /** Whether this is the mirrored variant of the inserter facing [direction]. */
    val mirror: Boolean = false,
    /** The circuit condition that enables this inserter, if any. */
    val circuitCondition: CircuitCondition? = null,
) : TransportEntity {
    override val isPowered = true

    override fun withArea(area: Area) = copy(area = area)
}

data class LoaderEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    val io: Io,
    val direction: Direction,
) : TransportEntity {
    override fun withArea(area: Area) = copy(area = area)
}
