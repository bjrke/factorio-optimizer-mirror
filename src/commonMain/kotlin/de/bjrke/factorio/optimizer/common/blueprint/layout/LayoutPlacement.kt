package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinitySettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area

// The entity kinds that are placed often get their own helper, so the tile size and prototype name
// live in one place rather than at every call site. They are board extensions so the kernel keeps
// only the grid and reservation primitives.

/** Places a belt (always one tile) carrying [material] on both lanes and facing [direction]. */
internal fun LayoutBoard.placeBelt(
    coordinate: Coordinate,
    material: Material,
    direction: Direction,
) {
    placeBelt(coordinate, LaneLoad.full(material), direction)
}

/** Places a belt (always one tile) with the given [load] and facing [direction]. */
internal fun LayoutBoard.placeBelt(
    coordinate: Coordinate,
    load: LaneLoad,
    direction: Direction,
) {
    place(BeltEntity(coordinate.area(), names.belt, direction, load))
}

/** Places an inserter (always one tile) of the selected tier, long when [long]. */
internal fun LayoutBoard.placeInserter(
    coordinate: Coordinate,
    direction: Direction,
    material: Material,
    long: Boolean,
) {
    place(
        InserterEntity(
            coordinate.area(),
            if (long) names.longInserter else names.inserter,
            material,
            direction,
            long,
        ),
    )
}

/** Places a loader (two tiles across the belt it serves) with its input/output [io]. */
internal fun LayoutBoard.placeLoader(
    coordinate: Coordinate,
    direction: Direction,
    io: Io,
    material: Material,
) {
    place(LoaderEntity(coordinate.area(2, 1), names.loader, material, io, direction))
}

/** Places a constant-combinator marker (always one tile) showing [signal]. */
internal fun LayoutBoard.placeMarker(coordinate: Coordinate, signal: Signal) {
    place(MarkerEntity(coordinate.area(), names.marker, signal))
}

/** Places a pipe (always one tile) carrying [fluid]. */
internal fun LayoutBoard.placePipe(coordinate: Coordinate, fluid: Material) {
    place(PipeEntity(coordinate.area(), names.pipe, fluid))
}

/** Places an infinity chest (always one tile) with its [settings]. */
internal fun LayoutBoard.placeInfinityChest(coordinate: Coordinate, settings: InfinitySettings) {
    place(InfinityChestEntity(coordinate.area(), names.infinityChest, settings))
}

/** Places an infinity pipe (always one tile) that supplies [fluid] at its default temperature. */
internal fun LayoutBoard.placeInfinityPipe(coordinate: Coordinate, fluid: Material) {
    place(
        InfinityPipeEntity(
            coordinate.area(),
            names.infinityPipe,
            fluid,
            InfinityPipeSettings(fluid, temperatureOf(fluid)),
        ),
    )
}

/**
 * Places an infinity pipe (always one tile) with no filter, so it deletes whatever fluid reaches
 * it. The blueprint writes no `infinity_settings` for it, matching the game's unfiltered sink pipe.
 */
internal fun LayoutBoard.placeInfinityPipeSink(coordinate: Coordinate, fluid: Material) {
    place(InfinityPipeEntity(coordinate.area(), names.infinityPipe, fluid))
}

/** Places the configured electric pole (always one tile). */
internal fun LayoutBoard.placePole(coordinate: Coordinate) {
    place(PoleEntity(coordinate.area(), names.pole))
}
