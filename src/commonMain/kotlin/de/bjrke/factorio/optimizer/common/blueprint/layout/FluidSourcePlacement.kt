package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.core.model.west

/** The tiles of a real fluid source block: input end, marker and output end. */
internal const val FLUID_SOURCE_WIDTH = 3

/** The surface width of the source block: one tile for the infinity pipe, three otherwise. */
internal fun fluidSourceWidth(beltEnd: BeltEnd) =
    if (beltEnd == BeltEnd.INFINITY_CHEST) 1 else FLUID_SOURCE_WIDTH

/** The surface tiles of the source block ending at [end], west to east. */
internal fun fluidSourceTiles(end: Coordinate, beltEnd: BeltEnd) =
    (end.x - fluidSourceWidth(beltEnd) + 1..end.x).map { Coordinate(it, end.y) }

/**
 * Places a fluid source block ending at [end]: a single infinity pipe for a cheat connector, or the
 * input/marker/output triple two tiles further west for a real one. An [io] of [Io.OUTPUT] drains
 * its fluid, so its cheat pipe is unfiltered; the marker triple always supplies.
 */
internal fun LayoutBoard.placeFluidSource(
    beltEnd: BeltEnd,
    end: Coordinate,
    fluid: Material,
    io: Io = Io.INPUT,
) {
    when {
        beltEnd != BeltEnd.INFINITY_CHEST -> {
            val input = end.west(FLUID_SOURCE_WIDTH - 1)
            placeMarker(end.west(), Signal(fluid, fluid = true))
            placeTunnel(fluid, input, end)
        }
        io == Io.OUTPUT -> placeInfinityPipeSink(end, fluid)
        else -> placeInfinityPipe(end, fluid)
    }
}

/** Removes the fluid source block ending at [end]. */
internal fun LayoutBoard.removeFluidSource(beltEnd: BeltEnd, end: Coordinate) {
    fluidSourceTiles(end, beltEnd).forEach(::removeAt)
}

/**
 * Connects the source block ending at [end] to the lane [head] with a pipe-to-ground run. The run
 * starts on the tile east of the source block and ends one tile west of the head, so it can pass
 * under a belt (the mixed merge's U-turn is the common case) without moving that belt.
 */
internal fun LayoutBoard.placeFluidSourceLink(
    material: Material,
    end: Coordinate,
    head: Coordinate,
) {
    val from = end.east()
    val to = head.west()
    if (from.x <= to.x) placeTunnels(material, from, to)
}

/** Removes the link [placeFluidSourceLink] placed. */
internal fun LayoutBoard.removeFluidSourceLink(
    material: Material,
    end: Coordinate,
    head: Coordinate,
) {
    val from = end.east()
    val to = head.west()
    if (from.x <= to.x) removeTunnelPlan(tunnelPlan(material, from, to))
}
