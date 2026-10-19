package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT

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
 * index or a route bound.
 */
internal class LayoutBoard(
    val names: LayoutNames,
    /** Maximum length of the selected belt tier's underground run. */
    val undergroundRange: Int,
    private val fluidPredicate: (Material) -> Boolean,
    private val temperatureOf: (Material) -> Int,
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

        /**
         * The underground runs tunneling over this tile, one key per run. Two runs may share the
         * tile when their axes differ, or when their types differ, so only a matching key clashes.
         */
        val tunnels = mutableSetOf<TunnelKey>()
    }

    /** The axis an underground run travels along; only a same-axis run can clash. */
    private enum class TunnelAxis {
        HORIZONTAL,
        VERTICAL,
    }

    /**
     * Identifies an underground run: its axis and its underground prototype (belt tier or pipe).
     */
    private data class TunnelKey(val axis: TunnelAxis, val type: String)

    private val grid = mutableMapOf<Coordinate, Cell>()

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

    private fun cell(coordinate: Coordinate) = grid.getOrPut(coordinate) { Cell() }

    fun entityAt(coordinate: Coordinate) = grid[coordinate]?.entity

    /** The fluid the pipe or pipe-to-ground at [coordinate] carries, if any. */
    fun fluidAt(coordinate: Coordinate) = (entityAt(coordinate) as? Plumbing)?.material

    fun isFree(coordinate: Coordinate) = grid[coordinate]?.entity == null

    fun isReserved(coordinate: Coordinate) = grid[coordinate]?.reserved == true

    /** Marks [coordinate] as needed by a feed or source of [material]. */
    fun reserve(coordinate: Coordinate, material: Material) {
        cell(coordinate).reserved = true
        cell(coordinate).reservedFor = material
    }

    /** The material of the feed that reserved [coordinate], if any. */
    fun reservedMaterialAt(coordinate: Coordinate) = grid[coordinate]?.reservedFor

    /** The fluid a machine-edge pipe reserved for [coordinate], if any. */
    fun edgeFluidAt(coordinate: Coordinate) = grid[coordinate]?.edgeFluid

    fun setEdgeFluid(coordinate: Coordinate, material: Material) {
        cell(coordinate).edgeFluid = material
    }

    /** Whether a run of [type] along [step] already passes under [coordinate]. */
    fun hasTunnel(coordinate: Coordinate, step: Coordinate, type: String): Boolean {
        val key = TunnelKey(axisOf(step), type)
        return key in (grid[coordinate]?.tunnels ?: emptySet())
    }

    /** Records [entity] and marks every tile it covers as occupied. */
    fun place(entity: LayoutEntity) {
        placed += entity
        entity.area.forEach { cell(it).entity = entity }
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

    /** Re-marks every underground run after the grid was rebuilt. */
    fun rebuildTunnels() {
        for (cell in grid.values) cell.tunnels.clear()
        for (input in placed.filterIsInstance<UndergroundEntity>().filter { it.io == INPUT }) {
            val output = undergroundOutputOf(input) ?: continue
            markTunnel(input.area.topLeft, output.area.topLeft, endName(input.material))
        }
    }

    /** Records every underground run of [nodes], so a later route sees the tiles under it. */
    fun markTunnels(nodes: List<Coordinate>, type: String) {
        nodes.zipWithNext().forEach { (from, to) ->
            if (!from.adjacent(to)) markTunnel(from, to, type)
        }
    }

    /** Records the underground run from [from] to [to] on every tile it passes under. */
    fun markTunnel(from: Coordinate, to: Coordinate, type: String) {
        val d = to - from
        val step = d.direction
        val key = TunnelKey(axisOf(step), type)
        for (k in 1 until d.chebyshevLen) {
            cell(from + step * k).tunnels += key
        }
    }

    /** The output end of the underground run [input] feeds, within the material's reach, if any. */
    fun undergroundOutputOf(input: UndergroundEntity): UndergroundEntity? {
        val step = input.direction.step
        for (reach in 2..range(input.material)) {
            val entity = grid[input.area.topLeft + step * reach]?.entity
            if (
                entity is UndergroundEntity &&
                    entity.io == OUTPUT &&
                    entity.direction == input.direction &&
                    entity.material == input.material
            ) {
                return entity
            }
        }
        return null
    }

    /** How far [material] may travel underground: a pipe's own reach or the belt tier's. */
    fun range(material: Material) =
        if (isFluid(material)) PIPE_UNDERGROUND_RANGE else undergroundRange

    /** The name of an underground end of [material]: a pipe-to-ground or the belt tier's. */
    fun endName(material: Material) =
        if (isFluid(material)) names.pipeToGround else names.undergroundBelt

    // The entity kinds that are placed often get their own helper, so the tile size and prototype
    // name live in one place rather than at every call site.

    /** Places a belt (always one tile) carrying [material] and facing [direction]. */
    fun placeBelt(coordinate: Coordinate, material: Material, direction: Direction) {
        place(BeltEntity(coordinate.area(), names.belt, material, direction))
    }

    /** Places a belt or a pipe, whichever [material] rides: a fluid goes through a pipe. */
    fun placeSurface(material: Material, coordinate: Coordinate, direction: Direction) {
        if (isFluid(material)) placePipe(coordinate, material)
        else placeBelt(coordinate, material, direction)
    }

    /** Places an underground belt or pipe-to-ground end (always one tile) for [material]. */
    fun placeEnd(
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
    fun placeInserter(
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
    fun placeLoader(
        coordinate: Coordinate,
        direction: Direction,
        io: Io,
        material: Material,
    ) {
        place(LoaderEntity(coordinate.area(2, 1), names.loader, material, io, direction))
    }

    /** Places a constant-combinator marker (always one tile) showing [signal]. */
    fun placeMarker(coordinate: Coordinate, signal: Signal) {
        place(MarkerEntity(coordinate.area(), names.marker, signal))
    }

    /** Places a pipe (always one tile) carrying [fluid]. */
    fun placePipe(coordinate: Coordinate, fluid: Material) {
        place(PipeEntity(coordinate.area(), names.pipe, fluid))
    }

    /** Places an infinity chest (always one tile) with its [settings]. */
    fun placeInfinityChest(coordinate: Coordinate, settings: InfinitySettings) {
        place(InfinityChestEntity(coordinate.area(), names.infinityChest, settings))
    }

    /**
     * Places an infinity pipe (always one tile) that supplies [fluid] at its default temperature.
     */
    fun placeInfinityPipe(coordinate: Coordinate, fluid: Material) {
        place(
            InfinityPipeEntity(
                coordinate.area(),
                names.infinityPipe,
                InfinityPipeSettings(fluid, temperatureOf(fluid)),
            ),
        )
    }

    /** Places a medium electric pole (always one tile). */
    fun placePole(coordinate: Coordinate) {
        place(PoleEntity(coordinate.area(), names.pole))
    }

    private fun axisOf(direction: Coordinate) =
        if (direction.x != 0) TunnelAxis.HORIZONTAL else TunnelAxis.VERTICAL
}
