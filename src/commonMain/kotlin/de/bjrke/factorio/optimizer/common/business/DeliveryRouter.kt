package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*

/** How many split points are routed before the cheapest one is chosen. */
private const val SPLIT_CANDIDATES = 12

/** Extra cost of an underground run when comparing two split points, so surface wins when close. */
private const val SPLIT_TUNNEL_PENALTY = 12

/**
 * Routes the solid belts of the layout: the target's output belt, the sink of every row input, the
 * producer trunk a material runs on and the splitters that branch off it, and the import
 * connectors. A produced material's trunk reaches its farthest consumer and the nearer consumers
 * split off it; an import gets one belt connector per sink, so unrelated consumers are not tied to
 * one long belt. The pipe routes belong to [FluidFeeder], which runs after the sources are prepared
 * and before they are routed.
 */
internal class DeliveryRouter(
    private val board: LayoutBoard,
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val planner: RowPlanner,
    private val rowPlacer: RowPlacer,
    private val router: LayoutRouter,
    private val connector: BeltEndConnector,
    imports: ImportExtension,
    beltEnd: BeltEnd,
    private val failures: MutableList<String>,
) {
    private val splitter = board.names.splitter

    /** The route tiles of each produced material, where a later consumer can split off. */
    private val networks = mutableMapOf<Material, MutableSet<Coordinate>>()

    /** Places the sinks and sources; [prepareDeliveries] delegates to it. */
    private val preparation =
        DeliveryPreparation(board, planner, rowPlacer, connector, imports, beltEnd)

    /**
     * Places every solid source up front and returns the deliveries to route. The sinks' feeds are
     * reserved first, then the sources are placed, so a long early route cannot occupy a marker or
     * producer outlet that a later delivery needs.
     */
    fun prepareDeliveries(): List<Delivery> = preparation.prepareDeliveries()

    /**
     * Routes every delivery, shortest first so a long belt can go around a confined one. A produced
     * material's split-offs only enter the queue once its trunk exists, but then compete with the
     * remaining deliveries.
     */
    fun routeDeliveries(deliveries: List<Delivery>) {
        val pending = deliveries.toMutableList()
        while (pending.isNotEmpty()) {
            val next = pending.minBy { it.distance }
            pending.remove(next)
            val routed =
                if (next.split) deliverSplit(next.material, next.sink)
                else deliver(next.material, next.start, next.direction, next.sink)
            if (routed) {
                for (sink in next.followUps) {
                    pending += Delivery(next.material, next.start, EAST, sink, split = true)
                }
            }
        }
    }

    /**
     * Routes one belt or pipe from [start] to [sink]'s feed tile, arriving on the surface and, for
     * a belt, heading into the target. The target itself is never touched.
     */
    private fun deliver(
        material: Material,
        start: Coordinate,
        direction: Direction,
        sink: Sink,
    ): Boolean {
        if (!isFree(start)) {
            failures += "${material.name} from $start (start blocked)"
            return false
        }
        if (start == sink.feed) {
            placeSurface(material, sink.feed, sink.heading ?: direction)
            return true
        }
        val segment = findRoute(start, direction, sink.feed, beltAllowed(material, sink))
        if (segment == null) {
            failures += "${material.name} to ${sink.feed} (no route)"
            return false
        }

        val nodes = buildList {
            add(start)
            addAll(segment)
        }
        placePath(board, material, nodes, sink.heading)
        recordNetwork(material, nodes, sink)
        return true
    }

    /**
     * What a belt route may do: stay off foreign belts, leave every other sink's feed clear, and
     * avoid [blocked] tiles (the splitter a branch starts from must not be routed over).
     */
    private fun beltAllowed(
        material: Material,
        sink: Sink,
        blocked: Set<Coordinate> = emptySet(),
    ): (Coordinate, Direction) -> Boolean = { coordinate, heading ->
        val goal = coordinate == sink.feed
        val facing = if (goal) sink.heading ?: heading else heading
        // the feed may side-load its target (a fourth input does), but no earlier tile may
        (routable(coordinate) || goal) &&
            coordinate !in blocked &&
            !sideLoaded(coordinate, material) &&
            (goal || !poursIntoSide(coordinate, facing, material)) &&
            !facesReservedFeed(coordinate, facing, sink.feed, material)
    }

    /** Remembers the tiles of a produced material's belt, so a nearer consumer can split off it. */
    private fun recordNetwork(material: Material, nodes: List<Coordinate>, sink: Sink) {
        if (material !in planner.producerOf) return
        networks.getOrPut(material) { mutableSetOf() } += nodes.filter { it != sink.feed }
    }

    /** A place where a splitter can be inserted into a produced material's belt. */
    private class SplitCandidate(
        val belt: Coordinate,
        val side: Coordinate,
        /** The splitter's free output, where the branch belt starts. */
        val exit: Coordinate,
        val direction: Direction,
    )

    /**
     * Feeds one more consumer by inserting a splitter into the material's existing belt. Both sides
     * of the belt are tried, and the routable split with the best surface-biased score wins.
     */
    private fun deliverSplit(material: Material, sink: Sink): Boolean {
        val candidates = splitCandidates(material).sortedBy { it.exit.manhattanDist(sink.feed) }
        var best: Pair<SplitCandidate, List<Coordinate>>? = null
        var bestScore = Int.MAX_VALUE
        for (candidate in candidates.take(SPLIT_CANDIDATES)) {
            val splitterTiles = setOf(candidate.belt, candidate.side)
            val segment =
                findRoute(
                    candidate.exit,
                    candidate.direction,
                    sink.feed,
                    beltAllowed(material, sink, splitterTiles),
                    strictStart = true,
                ) ?: continue
            val score = routeScore(listOf(candidate.exit) + segment)
            if (score < bestScore) {
                bestScore = score
                best = candidate to segment
            }
        }
        val (candidate, segment) =
            best
                ?: run {
                    failures += "${material.name} to ${sink.feed} (no split)"
                    return false
                }

        split(candidate, material)
        val nodes = listOf(candidate.exit) + segment
        placePath(board, material, nodes, sink.heading)
        recordNetwork(material, nodes, sink)
        return true
    }

    /**
     * How good a split route is: its traveled length plus a penalty for every underground run. A
     * slightly longer surface route beats a shorter one that dives under the trunk.
     */
    private fun routeScore(nodes: List<Coordinate>): Int =
        nodes.zipWithNext().sumOf { (from, to) ->
            if (from.adjacent(to)) 1 else from.manhattanDist(to) + SPLIT_TUNNEL_PENALTY
        }

    /** Every belt tile where a splitter fits: straight trunk, a free side and a free exit. */
    private fun splitCandidates(material: Material): List<SplitCandidate> {
        val tiles = networks[material] ?: return emptyList()
        return tiles.flatMap { tile ->
            val belt = entityAt(tile) as? BeltEntity ?: return@flatMap emptyList()
            if (belt.material != material) return@flatMap emptyList()
            val direction = belt.direction
            val next = entityAt(tile + direction.step) as? BeltEntity
            if (next == null || next.material != material || next.direction != direction) {
                return@flatMap emptyList()
            }
            sides(direction).mapNotNull { sideDirection ->
                val side = tile + sideDirection.step
                if (!isFree(side) || isReserved(side)) return@mapNotNull null
                val exit = side + direction.step
                if (!isFree(exit) || isReserved(exit)) return@mapNotNull null
                val area = splitterArea(tile, side, direction)
                if (!area.all { it == tile || isFree(it) }) return@mapNotNull null
                SplitCandidate(tile, side, exit, direction)
            }
        }
    }

    /** The two tiles across [direction] from tile that a splitter can use. */
    private fun sides(direction: Direction) =
        when (direction) {
            NORTH,
            SOUTH -> listOf(EAST, WEST)
            EAST,
            WEST -> listOf(NORTH, SOUTH)
        }

    /** The two-by-one area a splitter covers when [belt] is its input side. */
    private fun splitterArea(belt: Coordinate, side: Coordinate, direction: Direction) =
        when (direction) {
            EAST,
            WEST -> Coordinate(belt.x, minOf(belt.y, side.y)).area(1, 2)
            NORTH,
            SOUTH -> Coordinate(minOf(belt.x, side.x), belt.y).area(2, 1)
        }

    /** Replaces the trunk belt at [candidate] with a splitter; its continuations stay in place. */
    private fun split(candidate: SplitCandidate, material: Material) {
        removeAt(candidate.belt)
        place(
            SplitterEntity(
                splitterArea(candidate.belt, candidate.side, candidate.direction),
                splitter,
                material,
                candidate.direction,
            ),
        )
    }

    /** Extends the target's output belt and ends it in the selected solid connector. */
    fun placeTargetOutput() {
        val targetMaterial = plan.target
        val producer = planner.producerOf[targetMaterial] ?: return
        val recipe = plan.recipes[producer]
        val row = rowPlacer.rows[producer] ?: return
        if (!recipe.recipe.result.keys.any { !logistics.isFluid(it) }) return
        var coordinate = Coordinate(row.right + 1, row.outputLaneRow)

        while (coordinate.x <= row.right + 3 && isFree(coordinate)) {
            placeBelt(coordinate, targetMaterial, EAST)
            coordinate = coordinate.east()
        }
        val fits = (0 until connector.width).all { isFree(coordinate.east(it)) }
        if (fits) {
            // the output label sits above the product line, just like an imported input
            connector.placeSolidConnector(targetMaterial, coordinate.x, coordinate.y, input = false)
        } else {
            val spot = markerSpot(coordinate.west())
            placeMarker(spot, Signal(targetMaterial))
        }
    }

    /** A free tile beside the belt at [where], so a marker never blocks the belt. */
    private fun markerSpot(where: Coordinate): Coordinate {
        val (anchorX, beltRow) = where

        for (dx in 0..12) {
            for (dy in listOf(-1, 1)) {
                val tile = Coordinate(anchorX + dx, beltRow + dy)
                if (isFree(tile)) return tile
            }
        }
        return where.west()
    }

    /** True when a belt next to [coordinate] pours a different material into that tile. */
    private fun sideLoaded(coordinate: Coordinate, material: Material): Boolean =
        DIRECTION_STEPS.any { d ->
            val neighbor = coordinate + d
            val belt = entityAt(neighbor) as? ConveyorEntity
            belt != null &&
                belt.direction == directionBetween(neighbor, coordinate) &&
                belt.material != material
        }

    /** True when a belt at [coordinate] leaving in [direction] would side-load a foreign belt. */
    private fun poursIntoSide(
        coordinate: Coordinate,
        direction: Direction,
        material: Material,
    ): Boolean {
        val belt = entityAt(coordinate + direction.step) as? ConveyorEntity ?: return false
        return belt.material != material &&
            belt.direction != direction &&
            belt.direction != opposite(direction)
    }

    /**
     * True when a belt at [coordinate] leaving in [direction] would run straight at a reserved feed
     * tile. That feed would later be entered from another side, so the belt would either block it
     * or side-load a foreign material into the lane.
     */
    private fun facesReservedFeed(
        coordinate: Coordinate,
        direction: Direction,
        ownFeed: Coordinate,
        material: Material,
    ): Boolean {
        val ahead = coordinate + direction.step
        if (ahead == ownFeed) return false
        val reserved = board.reservedMaterialAt(ahead) ?: return false
        // the same material may join its own feed; a foreign one would merge or block it
        return reserved != material
    }

    private fun opposite(direction: Direction) = direction.opposite()

    // The board owns the grid and the tile primitives; these delegates keep the layout code
    // reading as if it placed entities directly.

    private fun place(entity: LayoutEntity) = board.place(entity)

    private fun placeBelt(coordinate: Coordinate, material: Material, direction: Direction) =
        board.placeBelt(coordinate, material, direction)

    private fun placeSurface(material: Material, coordinate: Coordinate, direction: Direction) =
        board.placeSurface(material, coordinate, direction)

    private fun placeMarker(coordinate: Coordinate, signal: Signal) =
        board.placeMarker(coordinate, signal)

    private fun removeAt(tile: Coordinate) = board.removeAt(tile)

    private fun findRoute(
        from: Coordinate,
        fromDirection: Direction,
        to: Coordinate,
        allowed: (Coordinate, Direction) -> Boolean,
        strictStart: Boolean = false,
    ) = router.findRoute(from, fromDirection, to, allowed, strictStart)

    private fun entityAt(coordinate: Coordinate) = board.entityAt(coordinate)

    private fun isFree(coordinate: Coordinate) = board.isFree(coordinate)

    private fun isReserved(coordinate: Coordinate) = board.isReserved(coordinate)

    private fun routable(coordinate: Coordinate) = isFree(coordinate) && !isReserved(coordinate)
}
