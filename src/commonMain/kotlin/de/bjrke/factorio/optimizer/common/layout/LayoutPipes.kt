package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.neighbors

/**
 * Whether a pipe of another fluid already touches [tile] from a side. Only pipes that are placed
 * count: a tile merely reserved for another fluid is not a pipe yet and is the caller's check
 * (`FluidFeeder.pipeAllowed` reads it from `edgeFluidAt`).
 */
internal fun LayoutBoard.touchesForeign(tile: Coordinate, material: Material): Boolean =
    tile.neighbors.any { neighbor ->
        val neighborMaterial = fluidAt(neighbor)
        neighborMaterial != null && neighborMaterial != material
    }

/**
 * Whether a pipe of [fluid] may sit at [coordinate]: the tile is free and no pipe beside it carries
 * a different fluid. Factorio joins touching pipes, so a pipe next to a foreign fluid would merge
 * two networks into one that can carry neither fluid; the lane placer and the fluid feed therefore
 * share this one rule instead of each testing `isFree` and the neighborhood itself.
 */
internal fun LayoutBoard.canPlacePipes(coordinate: Coordinate, fluid: Material) =
    isFree(coordinate) && !touchesForeign(coordinate, fluid)
