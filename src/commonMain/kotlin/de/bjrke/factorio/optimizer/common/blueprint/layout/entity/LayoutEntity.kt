package de.bjrke.factorio.optimizer.common.blueprint.layout.entity

import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * What a marker displays: a [material] and whether it is a [fluid]. Grouping the two keeps a
 * constant combinator's signal in one place (the wire format splits it into `marker` and
 * `markerFluid`).
 */
data class Signal(val material: Material, val fluid: Boolean = false)

/**
 * Something the layout places on the grid, with its own behaviour. It carries only the facts the
 * layout reasons about (a belt's direction and the material it moves, a pipe's fluid, a marker's
 * signal), so a `when` over a [LayoutEntity] is exhaustive. The blueprint writer reads these
 * entities directly (`blueprint.printing.json`), and it is the single place that knows the wire
 * format; that is why this package never mentions the blueprint. [withArea] is how compaction moves
 * one without knowing its concrete type.
 */
sealed interface LayoutEntity {
    /** The tiles the entity covers; its position is [Area.topLeft]. */
    val area: Area
    val name: String

    /** The same entity moved to [area], keeping every other field. */
    fun withArea(area: Area): LayoutEntity

    /** Whether a power pole must cover this entity. */
    val isPowered: Boolean
        get() = false
}

/**
 * The direction the entity faces on the surface, or null when it faces no way. A pipe-to-ground
 * faces the side its surface pipe attaches on ([PipeToGroundEntity.mouth]); its stored
 * [UndergroundEntity.direction] is the run's travel direction instead.
 */
val LayoutEntity.facingDirection: Direction?
    get() =
        when (this) {
            is MachineEntity -> direction
            is BeltEntity -> direction
            is UndergroundBeltEntity -> direction
            is SplitterEntity -> direction
            is InserterEntity -> direction
            is LoaderEntity -> direction
            is PipeToGroundEntity -> mouth
            is PipeEntity,
            is InfinityPipeEntity,
            is InfinityChestEntity,
            is ChestEntity,
            is MarkerEntity,
            is PoleEntity -> null
        }

/**
 * Everything the layout moves material with: items on belts, inserters and loaders, fluids in
 * pipes. Each carries the [material] it handles; a belt may carry two, one per lane, so a higher
 * layer reads [materials] instead of assuming a single one.
 */
sealed interface TransportEntity : LayoutEntity {
    val material: Material

    /** Every material the entity can carry. A single-lane entity has exactly one. */
    val materials: Set<Material>
        get() = setOf(material)

    fun hasMaterial(material: Material) = this.material == material
}

/** A belt, underground belt or splitter: a transport entity that faces a [direction]. */
sealed interface ConveyorEntity : TransportEntity {
    val direction: Direction

    /** The two lanes; an underground end and a splitter keep both. */
    val load: LaneLoad

    override val materials: Set<Material>
        get() = load.materials

    override fun hasMaterial(material: Material) = load.has(material)
}

/** A belt or underground belt: the belt-like carriers the layout treats as item movers. */
sealed interface BeltLikeEntity : ConveyorEntity

/** A pipe or pipe-to-ground; both carry the fluid [material] the layout routed through them. */
sealed interface Plumbing : TransportEntity

/** One end of an underground run (an underground belt or a pipe-to-ground). */
sealed interface UndergroundEntity : TransportEntity {
    val direction: Direction
    val io: Io
}
