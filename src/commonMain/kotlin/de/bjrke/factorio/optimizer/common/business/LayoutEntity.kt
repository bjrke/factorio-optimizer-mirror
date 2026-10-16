package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/**
 * What a marker displays: a [material] and whether it is a [fluid]. Grouping the two keeps a
 * constant combinator's signal in one place (the wire format splits it into `marker` and
 * `markerFluid`).
 */
data class Signal(val material: Material, val fluid: Boolean = false)

/**
 * Something the layout places on the grid, with its own behaviour. This is the domain counterpart
 * of the [BlueprintEntity] wire DTO: it carries only the facts the layout reasons about (a belt's
 * direction and the material it moves, a pipe's fluid, a marker's signal), so a `when` over a
 * [LayoutEntity] is exhaustive. [toBlueprintEntity] is the single place that turns one into the
 * DTO.
 */
sealed interface LayoutEntity {
    /** The tiles the entity covers; its position is [Area.topLeft]. */
    val area: Area
    val name: String

    /** Whether a power pole must cover this entity. */
    val isPowered: Boolean
        get() = false
}

/** A belt, underground belt or splitter: everything that faces a [direction]. */
sealed interface ConveyorEntity : LayoutEntity {
    val direction: Direction
}

/** A conveyor that moves items, so it carries the [material] currently on it (held for routing). */
sealed interface BeltLikeEntity : ConveyorEntity {
    val material: Material?
}

data class BeltEntity(
    override val area: Area,
    override val name: String,
    override val direction: Direction,
    override val material: Material?,
) : BeltLikeEntity

data class UndergroundBeltEntity(
    override val area: Area,
    override val name: String,
    override val direction: Direction,
    override val material: Material?,
    val io: Io,
) : BeltLikeEntity

data class SplitterEntity(
    override val area: Area,
    override val name: String,
    override val direction: Direction,
) : ConveyorEntity

data class MachineEntity(
    override val area: Area,
    override val name: String,
    val recipe: String,
) : LayoutEntity {
    override val isPowered = true
}

data class InserterEntity(
    override val area: Area,
    override val name: String,
    val direction: Direction,
    /** Whether this is a long-handed inserter (reaches two tiles). */
    val long: Boolean,
) : LayoutEntity {
    override val isPowered = true
}

data class LoaderEntity(
    override val area: Area,
    override val name: String,
    val direction: Direction,
    val io: Io,
) : LayoutEntity

data class InfinityChestEntity(
    override val area: Area,
    override val name: String,
    val settings: InfinitySettings,
) : LayoutEntity

data class MarkerEntity(
    override val area: Area,
    override val name: String,
    val signal: Signal,
) : LayoutEntity

/** A pipe or pipe-to-ground; both carry the [fluid] the layout routed through them. */
sealed interface Plumbing : LayoutEntity {
    val fluid: Material?
}

data class PipeEntity(
    override val area: Area,
    override val name: String,
    override val fluid: Material?,
) : Plumbing

data class PipeToGroundEntity(
    override val area: Area,
    override val name: String,
    val direction: Direction,
    override val fluid: Material?,
) : Plumbing

data class PoleEntity(
    override val area: Area,
    override val name: String,
    /** The `entity_number`s this pole is wired to; a blueprint does not rebuild wires by itself. */
    val neighbours: List<Int> = emptyList(),
) : LayoutEntity

/**
 * The one place a placed entity becomes the serializable [BlueprintEntity] DTO. The common part is
 * built once and each branch only names the fields it adds, so a new base field is set here alone.
 */
fun LayoutEntity.toBlueprintEntity(): BlueprintEntity {
    val common = BlueprintEntity(name, area)
    return when (this) {
        is MachineEntity -> common.copy(recipe = recipe)
        is InserterEntity -> common.copy(direction = direction)
        is BeltEntity -> common.copy(direction = direction)
        is SplitterEntity -> common.copy(direction = direction)
        is UndergroundBeltEntity -> common.copy(direction = direction, type = io)
        is LoaderEntity -> common.copy(direction = direction, type = io)
        is InfinityChestEntity -> common.copy(infinity = settings)
        is MarkerEntity -> common.copy(marker = signal.material, markerFluid = signal.fluid)
        is PipeEntity -> common
        is PipeToGroundEntity -> common.copy(direction = direction)
        is PoleEntity -> common.copy(neighbours = neighbours)
    }
}
