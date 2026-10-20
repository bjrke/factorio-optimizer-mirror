package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*

/** How many split points are routed before the cheapest one is chosen. */
private const val SPLIT_CANDIDATES = 12

/** Extra cost of an underground run when comparing two split points, so surface wins when close. */
private const val SPLIT_TUNNEL_PENALTY = 12

/**
 * Lays out [plan] as rows of connected factories. Every recipe is one row of touching machines, fed
 * by belt lanes just above (a short inserter for the near lane, a long inserter for the far one)
 * and drained by an output belt just below. Rows flow top to bottom; imports enter from the left,
 * and a material built inside the plan runs on one trunk from its producer's output belt, with a
 * splitter on that belt for every further recipe that takes it. Fluids use pipe clusters next to
 * the machines instead of belts.
 *
 * The [beltEnd] chooses how a solid belt ends: the constant-combinator label (the default), an
 * infinity chest, or a chest the belt's inserters feed from or to. A fluid belt always keeps the
 * constant-combinator/pipe label, since a chest cannot carry a fluid.
 */
fun buildBlueprint(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        val entities = build()
        Blueprint(
            label = plan.label,
            icon = plan.target,
            entities = entities,
            wires = blueprintWires(),
        )
    }

/** Inputs whose belt could not be routed; empty for a valid blueprint. Used by tests. */
internal fun blueprintFeedFailures(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        failures
    }

/**
 * The material each transport entity carries, keyed by tile. Used by tests to check nothing merges.
 */
internal fun blueprintTransportMaterials(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        transportMaterials()
    }

/** The fluid each pipe or pipe-to-ground tile carries. Used by tests to check no fluids merge. */
internal fun blueprintPipeFluids(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        pipeFluids()
    }

/** The placed entities and the feed routes that failed, for the CLI's text layout. */
internal fun blueprintLayoutDump(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    RowLayout(plan, logistics, beltEnd).run {
        build()
        LayoutDump(placedEntities(), failures.toList())
    }

private class RowLayout(
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val beltEnd: BeltEnd,
) {
    private val board =
        LayoutBoard(
            LayoutNames(
                belt = logistics.belt.identifier,
                inserter = logistics.inserter.identifier,
                longInserter = InserterTier.LONG_HANDED_INSERTER.identifier,
            ),
            undergroundRange =
                when {
                    logistics.belt.identifier.contains("turbo") -> 11
                    logistics.belt.identifier.contains("express") -> 9
                    logistics.belt.identifier.contains("fast") -> 7
                    else -> 5
                },
            logistics::isFluid,
            logistics::fluidTemperature,
        )

    private val router = LayoutRouter(board)

    private val splitter = board.names.splitter

    private val connector = BeltEndConnector(board, beltEnd)

    private val placed
        get() = board.placed

    /**
     * The machine-port tiles a fluid must reach. An underground run never swallows one, so growing
     * a run cannot pull a pipe off the machine it feeds.
     */
    private val fluidPorts = mutableSetOf<Coordinate>()

    /** Inputs whose belt could not be routed; a non-empty list means the blueprint is broken. */
    val failures = mutableListOf<String>()

    /** The import markers and west bounds the trim, compaction and power stages share. */
    private val imports = ImportExtension()

    private val planner = RowPlanner(plan, logistics)

    private val rowPlacer = RowPlacer(board, plan, logistics, planner)

    private val fluidFeeder =
        FluidFeeder(
            board,
            plan,
            logistics,
            planner,
            rowPlacer,
            router,
            beltEnd,
            imports,
            fluidPorts,
            failures,
        )

    private val importTrimmer = ImportTrimmer(board, connector, beltEnd, imports)

    private val compactionPass = CompactionPass(board, beltEnd, imports, fluidPorts)

    /** The route tiles of each produced material, where a later consumer can split off. */
    private val networks = mutableMapOf<Material, MutableSet<Coordinate>>()

    /**
     * Where a material enters a row's lane: the route ends on [feed], the one tile next to
     * [target], and faces [heading] so its belt or pipe points at the lane. [target] is already
     * placed and is never overwritten.
     */
    private class Sink(
        val target: Coordinate,
        val feed: Coordinate,
        /** The direction the feed faces, or null for a pipe, which has no facing. */
        val heading: Direction?,
    ) {
        val feedRow
            get() = feed.y
    }

    /**
     * One belt or pipe route: fill [sink] from [start], leaving in [direction]. A produced
     * material's first delivery carries the other sinks as [followUps]; they split off its belt
     * once it is laid. A [split] delivery starts on an existing belt of its material.
     */
    private class Delivery(
        val material: Material,
        val start: Coordinate,
        val direction: Direction,
        val sink: Sink,
        val followUps: List<Sink> = emptyList(),
        val split: Boolean = false,
    ) {
        val distance
            get() = start.manhattanDist(sink.feed)
    }

    fun build(): List<BlueprintEntity> {
        if (plan.recipes.isNotEmpty()) {
            rowPlacer.placeAll()
            placeTargetOutput()
            val sinks = allSolidSinks()
            for ((material, _, sink) in sinks) reserve(sink.feed, material)
            // prepare every source before routing anything, so a long early route cannot occupy a
            // marker or producer outlet that a later delivery needs
            val deliveries = prepareSolidDeliveries(sinks)
            // pipes go first: their underground runs are cheap and belts can tunnel under them
            fluidFeeder.feed()
            routeDeliveries(deliveries)
            fluidFeeder.placeTargetOutput()
            // the markers may move now that the routes are fixed; then the layout is compacted
            // before electricity, so poles and wires are placed on the final geometry
            importTrimmer.trim()
            stretchUndergroundRuns()
            compactionPass.compact()
            PowerPlacer(board, imports.poleWestBound).place()
        }
        return placed.map { it.toBlueprintEntity() }
    }

    /** The entities placed so far, in placement order. Used by [blueprintLayoutDump]. */
    fun placedEntities(): List<LayoutEntity> = placed.toList()

    /** The material each transport entity carries, keyed by the tile. Used by tests. */
    fun transportMaterials() =
        placed.filterIsInstance<TransportEntity>().associate {
            it.area.topLeft to it.material
        }

    /** The fluid each pipe or pipe-to-ground tile carries, keyed by the tile. Used by tests. */
    fun pipeFluids() =
        placed.filterIsInstance<Plumbing>().associate {
            it.area.topLeft to it.material
        }

    /** The circuit wires a chest marker needs, relative to the final entity order. */
    fun blueprintWires(): List<BlueprintWire> = chestWires(placed)

    private fun sinkOf(row: Row, material: Material): Sink? {
        val index = row.solids.indexOf(material)
        if (index < 0) return null
        return when (index) {
            0 -> laneSink(row, row.laneNearRow ?: return null, material)
            1 -> laneSink(row, row.laneFarRow ?: return null, material)
            2 -> laneSink(row, row.laneBelowRow ?: return null, material)
            else -> {
                // any further input side-loads the lower belt from below
                val y = row.laneBelowRow ?: return null
                val x = row.left + (index - 3)
                Sink(Coordinate(x, y), Coordinate(x, y + 1), NORTH)
            }
        }
    }

    private fun laneSink(row: Row, y: Int, material: Material): Sink =
        if (rowPlacer.laneDirection(row, material) == EAST) {
            Sink(Coordinate(row.left, y), Coordinate(row.left - 1, y), EAST)
        } else {
            Sink(Coordinate(row.right, y), Coordinate(row.right + 1, y), WEST)
        }

    private fun allSolidSinks() = buildList {
        for (row in rowPlacer.placedRows) {
            for (material in row.solids) {
                sinkOf(row, material)?.let { add(Triple(material, row, it)) }
            }
        }
    }

    /**
     * Turns the solid sinks into deliveries and places every source up front. An intermediate gets
     * one trunk from its producer to the farthest consumer; the nearer consumers split off that
     * belt later. An import gets one belt connector per sink, so unrelated consumers are not tied
     * to one long shared belt.
     */
    private fun prepareSolidDeliveries(sinks: List<Triple<Material, Row, Sink>>): List<Delivery> {
        val grouped = sinks.groupBy { it.first }
        val deliveries = mutableListOf<Delivery>()
        for ((material, list) in grouped.entries.sortedBy { it.key.name }) {
            val producer = planner.producerOf[material]
            if (producer != null) {
                val producerRow = rowPlacer.rows[producer] ?: continue
                val start = outputStart(producerRow, material)
                val distinctSinks = list.map { it.third }.distinctBy { it.feed }
                // the trunk reaches the farthest consumer, so it passes the nearer ones on the way
                val sorted = distinctSinks.sortedByDescending { start.manhattanDist(it.feed) }
                deliveries +=
                    Delivery(
                        material = material,
                        start = start,
                        direction = EAST,
                        sink = sorted.first(),
                        followUps = sorted.drop(1),
                    )
            } else {
                for ((_, _, sink) in list) {
                    deliveries += Delivery(material, importSource(material, sink), EAST, sink)
                }
            }
        }
        return deliveries
    }

    /**
     * Routes every delivery, shortest first so a long belt can go around a confined one. A produced
     * material's split-offs only enter the queue once its trunk exists, but then compete with the
     * remaining deliveries.
     */
    private fun routeDeliveries(deliveries: List<Delivery>) {
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

    /** The first free belt tile east of [row]'s output lane, reserved as a source. */
    private fun outputStart(row: Row, material: Material): Coordinate {
        var x = row.right + 1
        while (x < row.right + 60 && !isFree(Coordinate(x, row.outputLaneRow))) x++
        val start = Coordinate(x, row.outputLaneRow)
        reserve(start, material)
        return start
    }

    /**
     * A solid belt connector feeding one imported sink: a constant-combinator label, an infinity
     * chest or a chest marker. The first free tile is reserved, so a route placed earlier cannot
     * block this source.
     */
    private fun importSource(material: Material, sink: Sink): Coordinate {
        val width = connector.width
        val columns = connector.left..(connector.left + width)
        var row = sink.feedRow
        var attempts = 0
        while (
            (row in imports.markerRows || columns.any { !isFree(Coordinate(it, row)) }) &&
                attempts++ < 50
        ) row++
        imports.markerRows += row
        connector.placeSolidConnector(material, connector.left, row, input = true)
        val start = Coordinate(connector.left + width, row)
        reserve(start, material)
        imports.importFeeds += ImportFeed(material, start, row, sink.feed)
        // a chest marker is powered, so its poles may sit in the import extension
        if (!beltEnd.isChest) imports.poleWestBound = maxOf(imports.poleWestBound, start.x)
        imports.compactionWestBound = maxOf(imports.compactionWestBound, sink.target.x)
        return start
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
    private fun placeTargetOutput() {
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

    private fun stretchUndergroundRuns() = UndergroundStretcher(board, fluidPorts).stretchAll()

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

    private fun reserve(coordinate: Coordinate, material: Material) =
        board.reserve(coordinate, material)
}
