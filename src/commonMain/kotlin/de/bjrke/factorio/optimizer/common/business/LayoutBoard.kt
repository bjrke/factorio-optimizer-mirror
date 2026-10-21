package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material

internal const val MARKER = "constant-combinator"
internal const val INFINITY_CHEST = "infinity-chest"
internal const val INFINITY_PIPE = "infinity-pipe"
internal const val PIPE = "pipe"
internal const val PIPE_TO_GROUND = "pipe-to-ground"
internal const val POLE = "medium-electric-pole"

/** The name of an underground run cannot exceed the prototype's `max_underground_distance`. */
internal const val PIPE_UNDERGROUND_RANGE = 10

/** The prototype names derived from the selected belt, inserter and the fixed pipe entities. */
internal data class LayoutNames(
    val belt: String,
    val inserter: String,
    val longInserter: String,
    val undergroundBelt: String = belt.replace("transport-belt", "underground-belt"),
    val splitter: String = belt.replace("transport-belt", "splitter"),
    val loader: String = belt.replace("transport-belt", "loader"),
    val pipe: String = PIPE,
    val pipeToGround: String = PIPE_TO_GROUND,
    val marker: String = MARKER,
    val infinityChest: String = INFINITY_CHEST,
    val infinityPipe: String = INFINITY_PIPE,
    val pole: String = POLE,
)

/**
 * The tile grid and the entities placed on it: the single mutable kernel every layout stage shares.
 *
 * It owns only what a tile can answer (the entity covering it, whether a feed reserved it, which
 * underground runs pass under it) and the primitives that place or remove an entity. A stage asks
 * the board instead of keeping a second copy of the grid, so a new stage cannot forget a tunnel
 * index or a route bound. The entity-kind helpers live in `LayoutPlacement.kt`, the underground
 * queries in `LayoutUnderground.kt`.
 */
internal class LayoutBoard(
    val names: LayoutNames,
    /** Maximum length of the selected belt tier's underground run. */
    val undergroundRange: Int,
    private val fluidPredicate: (Material) -> Boolean,
    internal val temperatureOf: (Material) -> Int,
) {

    /** Whether [material] is a fluid, and therefore rides in pipes instead of on belts. */
    fun isFluid(material: Material) = fluidPredicate(material)

    /** One tile: the entity that covers it and the planner's flags for the tile. */
    private class Cell {
        var entity: LayoutEntity? = null

        /** A feed will need this tile; no route may run through it first. */
        var reserved = false

        /** The material the feed that reserved this tile carries. */
        var reservedFor: Material? = null

        /** The fluid a machine-edge pipe is reserved for here, so two fluids never touch edges. */
        var edgeFluid: Material? = null
    }

    private val grid = mutableMapOf<Coordinate, Cell>()

    /** The underground runs passing under the placed tiles. */
    internal val tunnels = Tunnels()

    /** Everything placed so far, in placement order. */
    val placed = mutableListOf<LayoutEntity>()

    /** The extent of everything placed, so a route search stays near the blueprint. */
    var minX = Int.MAX_VALUE
        private set

    var maxX = Int.MIN_VALUE
        private set

    var minY = Int.MAX_VALUE
        private set

    var maxY = Int.MIN_VALUE
        private set

    fun entityAt(coordinate: Coordinate) = grid[coordinate]?.entity

    fun isFree(coordinate: Coordinate) = grid[coordinate]?.entity == null

    fun isReserved(coordinate: Coordinate) = grid[coordinate]?.reserved == true

    /** Marks [coordinate] as needed by a feed or source of [material]. */
    fun reserve(coordinate: Coordinate, material: Material) {
        grid
            .getOrPut(coordinate) { Cell() }
            .apply {
                reserved = true
                reservedFor = material
            }
    }

    /** The material of the feed that reserved [coordinate], if any. */
    fun reservedMaterialAt(coordinate: Coordinate) = grid[coordinate]?.reservedFor

    /** The fluid a machine-edge pipe reserved for [coordinate], if any. */
    fun edgeFluidAt(coordinate: Coordinate) = grid[coordinate]?.edgeFluid

    fun setEdgeFluid(coordinate: Coordinate, material: Material) {
        grid.getOrPut(coordinate) { Cell() }.edgeFluid = material
    }

    /** Records [entity] and marks every tile it covers as occupied. */
    fun place(entity: LayoutEntity) {
        placed += entity
        entity.area.forEach { grid.getOrPut(it) { Cell() }.entity = entity }
        val area = entity.area
        minX = minOf(minX, area.x)
        maxX = maxOf(maxX, area.bottomRight.x)
        minY = minOf(minY, area.y)
        maxY = maxOf(maxY, area.bottomRight.y)
    }

    /** Removes the entity covering [tile], if any, from [placed] and the grid. */
    fun removeAt(tile: Coordinate) {
        val entity = grid[tile]?.entity ?: return
        placed.remove(entity)
        entity.area.forEach { if (grid[it]?.entity === entity) grid[it]?.entity = null }
    }

    /** Replaces the whole layout and rebuilds the tile grid and tunnel marks from the entities. */
    fun replaceAll(entities: List<LayoutEntity>) {
        placed.clear()
        grid.clear()
        minX = Int.MAX_VALUE
        maxX = Int.MIN_VALUE
        minY = Int.MAX_VALUE
        maxY = Int.MIN_VALUE
        entities.forEach { place(it) }
        rebuildTunnels()
    }
}
