package de.bjrke.factorio.optimizer.common.blueprint.layout.entity

import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material

data class BeltEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
    /** The two lanes; [material] is one of them, the primary the entity is labelled with. */
    override val load: LaneLoad = LaneLoad.full(material),
) : BeltLikeEntity {

    init {
        require(load.has(material)) { "primary $material is not on the belt $load" }
    }

    constructor(
        area: Area,
        name: String,
        direction: Direction,
        load: LaneLoad,
    ) : this(area, name, load.material, direction, load)

    override fun withArea(area: Area) = copy(area = area)
}

/** Whether this conveyor carries only [material]; a mixed belt carries two materials. */
fun ConveyorEntity.isSingleMaterial(material: Material): Boolean =
    !load.isMixed && load.has(material)

data class UndergroundBeltEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
    override val io: Io,
    /** The two lanes the run carries; a tunnel keeps both. */
    override val load: LaneLoad = LaneLoad.full(material),
) : BeltLikeEntity, UndergroundEntity {

    init {
        require(load.has(material)) { "primary $material is not on the underground belt $load" }
    }

    override fun withArea(area: Area) = copy(area = area)
}

data class SplitterEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
    /** The two lanes the splitter carries; both outputs keep them. */
    override val load: LaneLoad = LaneLoad.full(material),
) : ConveyorEntity {

    init {
        require(load.has(material)) { "primary $material is not on the splitter $load" }
    }

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
