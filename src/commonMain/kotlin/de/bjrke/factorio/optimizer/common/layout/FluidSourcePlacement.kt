package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.west

/** The tiles of a real fluid source block: input end, marker and output end. */
internal const val FLUID_SOURCE_WIDTH = 3

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
    val width = if (beltEnd == BeltEnd.INFINITY_CHEST) 1 else FLUID_SOURCE_WIDTH
    for (x in end.x - width + 1..end.x) removeAt(Coordinate(x, end.y))
}
