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

/**
 * Everything the layout moves material with: items on belts, inserters and loaders, fluids in
 * pipes. Each carries exactly the one [material] it handles, so a higher layer can tell what is on
 * a tile (and whether two feed materials would merge).
 */
sealed interface TransportEntity : LayoutEntity {
    val material: Material
}

/** A belt, underground belt or splitter: a transport entity that faces a [direction]. */
sealed interface ConveyorEntity : TransportEntity {
    val direction: Direction
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

data class BeltEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
) : BeltLikeEntity

data class UndergroundBeltEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
    override val io: Io,
) : BeltLikeEntity, UndergroundEntity

data class SplitterEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
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
}

data class LoaderEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    val io: Io,
    val direction: Direction,
) : TransportEntity

/** A chest the layout places as a belt end marker: its [area] is always one tile. */
sealed interface ChestLikeEntity : LayoutEntity

data class InfinityChestEntity(
    override val area: Area,
    override val name: String,
    val settings: InfinitySettings,
) : ChestLikeEntity

data class ChestEntity(
    override val area: Area,
    override val name: String,
    /** The logistic request it keeps in stock (a buffer/storage chest). */
    val requestFilter: RequestFilter? = null,
    /** The inventory bar limit (a wooden/steel chest). */
    val bar: Int? = null,
    /** Whether its own circuit condition is enabled, if it is set at all. */
    val circuitConditionEnabled: Boolean? = null,
) : ChestLikeEntity

data class MarkerEntity(
    override val area: Area,
    override val name: String,
    val signal: Signal,
) : LayoutEntity

data class PipeEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
) : Plumbing

data class PipeToGroundEntity(
    override val area: Area,
    override val name: String,
    override val material: Material,
    override val direction: Direction,
    /**
     * Input or output end of the run; a blueprint carries only the direction, so this is internal.
     */
    override val io: Io,
) : Plumbing, UndergroundEntity

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
        is InserterEntity ->
            common.copy(
                direction = direction,
                mirror = mirror,
                circuitCondition = circuitCondition,
            )
        is BeltEntity -> common.copy(direction = direction)
        is SplitterEntity -> common.copy(direction = direction)
        is UndergroundBeltEntity -> common.copy(direction = direction, type = io)
        is LoaderEntity -> common.copy(direction = direction, type = io)
        is InfinityChestEntity -> common.copy(infinity = settings)
        is ChestEntity ->
            common.copy(
                requestFilter = requestFilter,
                bar = bar,
                circuitConditionEnabled = circuitConditionEnabled,
            )
        is MarkerEntity -> common.copy(marker = signal.material, markerFluid = signal.fluid)
        is PipeEntity -> common
        is PipeToGroundEntity -> common.copy(direction = direction)
        is PoleEntity -> common.copy(neighbours = neighbours)
    }
}
