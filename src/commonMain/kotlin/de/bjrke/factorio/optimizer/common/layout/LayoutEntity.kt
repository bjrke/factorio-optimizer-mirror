package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material

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
 * DTO; [withArea] is how compaction moves one without knowing its concrete type.
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
