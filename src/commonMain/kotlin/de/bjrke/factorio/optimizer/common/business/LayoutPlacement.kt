package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

// The entity kinds that are placed often get their own helper, so the tile size and prototype name
// live in one place rather than at every call site. They are board extensions so the kernel keeps
// only the grid and reservation primitives.

/** Places a belt (always one tile) carrying [material] and facing [direction]. */
internal fun LayoutBoard.placeBelt(
    coordinate: Coordinate,
    material: Material,
    direction: Direction,
) {
    place(BeltEntity(coordinate.area(), names.belt, material, direction))
}

/** Places a belt or a pipe, whichever [material] rides: a fluid goes through a pipe. */
internal fun LayoutBoard.placeSurface(
    material: Material,
    coordinate: Coordinate,
    direction: Direction,
) {
    if (isFluid(material)) placePipe(coordinate, material)
    else placeBelt(coordinate, material, direction)
}

/** Places an underground belt or pipe-to-ground end (always one tile) for [material]. */
internal fun LayoutBoard.placeEnd(
    material: Material,
    coordinate: Coordinate,
    direction: Direction,
    io: Io,
) {
    place(
        if (isFluid(material))
            PipeToGroundEntity(coordinate.area(), names.pipeToGround, material, direction, io)
        else
            UndergroundBeltEntity(
                coordinate.area(),
                names.undergroundBelt,
                material,
                direction,
                io,
            ),
    )
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
            InfinityPipeSettings(fluid, temperatureOf(fluid)),
        ),
    )
}

/** Places a medium electric pole (always one tile). */
internal fun LayoutBoard.placePole(coordinate: Coordinate) {
    place(PoleEntity(coordinate.area(), names.pole))
}
