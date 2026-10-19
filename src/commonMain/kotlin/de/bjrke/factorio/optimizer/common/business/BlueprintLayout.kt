package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe bands, so their belts can be routed between them. */
private const val BAND_GAP = 14

/** Upper bound on compaction corridors, so a pathological layout cannot loop forever. */
private const val MAX_COMPACTION_PASSES = 256

/** How many split points are routed before the cheapest one is chosen. */
private const val SPLIT_CANDIDATES = 12

/** Extra cost of an underground run when comparing two split points, so surface wins when close. */
private const val SPLIT_TUNNEL_PENALTY = 12

/** Medium poles supply a 7x7 area (radius 3) and reach 9 tiles of wire. */
private const val SUPPLY_RADIUS = 3

private const val WIRE_REACH = 9
private const val WIRE_REACH_SQ = WIRE_REACH * WIRE_REACH

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
            wires = chestWires(),
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

    private val belt = board.names.belt
    private val inserter = board.names.inserter
    private val longInserter = board.names.longInserter
    private val splitter = board.names.splitter
    private val undergroundBelt = board.names.undergroundBelt

    /** The loader of the same tier as the belt, used as a test input/output. */
    private val loader = board.names.loader

    /**
     * How many tiles the solid belt connector occupies left of the feed: three for a label or a
     * cheat chest, five for a chest the inserters feed.
     */
    private val connectorWidth
        get() =
            when (beltEnd) {
                BeltEnd.CONSTANT_COMBINATOR,
                BeltEnd.INFINITY_CHEST -> 3
                else -> 5
            }

    /**
     * The column a solid connector starts on. A cheat chest sits two tiles in so its loader fits
     * before the feed; every other connector starts at the west edge.
     */
    private val connectorLeft
        get() = if (beltEnd == BeltEnd.INFINITY_CHEST) 2 else 0

    private val placed
        get() = board.placed

    private val beltProfile
        get() = router.beltProfile

    private val pipeProfile
        get() = router.pipeProfile

    private val markerRows = mutableSetOf<Int>()

    /**
     * The machine-port tiles a fluid must reach. An underground run never swallows one, so growing
     * a run cannot pull a pipe off the machine it feeds.
     */
    private val fluidPorts = mutableSetOf<Coordinate>()

    /**
     * One imported input: its belt connector (label, cheat chest or chest marker) and the tile its
     * belt starts on. The prefix between the two is trimmed after routing, so the import bus does
     * not have to cross the whole layout before the compactor sees it.
     */
    private class ImportFeed(
        val material: Material,
        val start: Coordinate,
        val row: Int,
        /** The lane tile the route feeds; the trim must never eat into the lane beyond it. */
        val feed: Coordinate,
    )

    private val importFeeds = mutableListOf<ImportFeed>()

    /**
     * The import marker tiles. A vertical corridor may not move them, so the markers stay in one
     * straight column even after the layout is compacted.
     */
    private val pinnedImportTiles = mutableSetOf<Coordinate>()

    /**
     * West of this column lies the import extension (markers and supply belts), not the factory.
     */
    private var poleWestBound = Int.MIN_VALUE

    /**
     * West of this column the imported rows may not be compacted: the input lane starts at the
     * factory edge, and moving the factory west of it would pull the lane over the marker.
     */
    private var compactionWestBound = Int.MIN_VALUE

    private val producerOf = mutableMapOf<Material, Int>()
    private val depth = mutableMapOf<Int, Int>()
    private val rows = mutableMapOf<Int, Row>()

    /** Every row once, in placement order; a band row serves all of its allocations. */
    private val placedRows = mutableListOf<Row>()

    /** The route tiles of each produced material, where a later consumer can split off. */
    private val networks = mutableMapOf<Material, MutableSet<Coordinate>>()

    /** Inputs whose belt could not be routed; a non-empty list means the blueprint is broken. */
    val failures = mutableListOf<String>()

    /** All the directions, Clock-wise EAST first, like most of the stuff has to be delivered. */
    private val steps = Direction.entries.map { it.step }

    /** One machine of a row: the allocation it builds and the column it starts on. */
    private class PlacedMachine(val allocation: Int, val left: Int)

    /**
     * One row of machines, with its input lanes and output belt. A plain recipe is a row of copies
     * of it; a direct-feed band is a row of differently-built machines whose [interfaces] insert
     * the intermediate straight from one machine into the next.
     */
    private class Row(
        /** The machine each allocation builds, left to right; one entry per machine. */
        val machines: List<PlacedMachine>,
        val left: Int,
        val factoryTop: Int,
        val width: Int,
        val height: Int,
        val stride: Int,
        val solids: List<Material>,
        val laneNearRow: Int?,
        val laneFarRow: Int?,
        val laneBelowRow: Int?,
        val outputLaneRow: Int,
        /** The direct insertions between the machines, by the gap they sit in. */
        val interfaces: List<PlannedLink> = emptyList(),
    ) {
        val copies
            get() = machines.size

        val factoryBottom
            get() = factoryTop + height - 1

        val right
            get() = left + (copies - 1) * stride + width - 1
    }

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

    init {
        // a band inserts its intermediate material machine to machine, so its producer allocation
        // must not act as a belt source: only a belted allocation may, which keeps the internal
        // material out of the lanes, sinks and deliveries
        val internal = mutableSetOf<Pair<Int, Material>>()
        for (band in plan.bands) {
            for (link in band.links) {
                val machine = if (link.producerIsLeft) link.gap else link.gap + 1
                internal += band.machines[machine] to link.material
            }
        }
        for ((index, recipe) in plan.recipes.withIndex()) {
            for (material in recipe.recipe.result.keys) {
                if ((index to material) in internal) continue
                producerOf.getOrPut(material) { index }
            }
        }
    }

    fun build(): List<BlueprintEntity> {
        if (plan.recipes.isNotEmpty()) {
            computeDepth()
            placeRows()
            placeTargetOutput()
            val sinks = allSolidSinks()
            for ((material, _, sink) in sinks) reserve(sink.feed, material)
            // prepare every source before routing anything, so a long early route cannot occupy a
            // marker or producer outlet that a later delivery needs
            val deliveries = prepareSolidDeliveries(sinks)
            // pipes go first: their underground runs are cheap and belts can tunnel under them
            feedFluids()
            routeDeliveries(deliveries)
            placeTargetFluidOutput()
            // the markers may move now that the routes are fixed; then the layout is compacted
            // before electricity, so poles and wires are placed on the final geometry
            trimImportPrefixes()
            stretchUndergroundRuns()
            compact()
            placePoles()
            wirePoles()
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

    /**
     * Moves every import marker to one common column and removes the straight belt prefix in front
     * of it. The routing already happened, so the router saw the old, wide west corridor and did
     * not have to route around the markers; only the unused prefix disappears. The common column is
     * the furthest east every feed has a straight eastward run, so the import bus stays straight.
     */
    private fun trimImportPrefixes() {
        if (importFeeds.isEmpty()) return
        val width = connectorWidth
        // the connector takes `width` tiles, so its column is the straight run's end - width
        val common = importFeeds.minOf { feed -> lastStraightEast(feed) - width }
        var trimmed = common > connectorLeft
        if (trimmed) {
            val saved = placed.toList()
            for (feed in importFeeds) {
                for (x in (feed.start.x - width) until feed.start.x) {
                    removeAt(Coordinate(x, feed.row))
                }
                for (x in feed.start.x until common + width) {
                    removeAt(Coordinate(x, feed.row))
                }
                placeSolidConnector(feed.material, common, feed.row, input = true)
                reserve(Coordinate(common + width, feed.row), feed.material)
            }
            if (validateLayout(placed).isNotEmpty()) {
                replaceAll(saved)
                trimmed = false
            }
        }
        // a chest marker is powered, so its poles may sit in the import extension
        if (trimmed && !beltEnd.isChest) poleWestBound = maxOf(poleWestBound, common + width)
        // keep the whole connector and the first belt together, so the markers keep one column
        for (feed in importFeeds) {
            val connectorStart = if (trimmed) common else feed.start.x - width
            for (x in connectorStart..(connectorStart + width)) {
                pinnedImportTiles += Coordinate(x, feed.row)
            }
        }
        // the old connector marks are gone; re-index the tunnels before stretching
        rebuildTunnels()
    }

    /**
     * The last column of the straight eastward belt run from [feed]'s source to its lane. The lane
     * itself starts behind [ImportFeed.feed] and is never part of the trim.
     */
    private fun lastStraightEast(feed: ImportFeed): Int {
        if (feed.feed.y != feed.row || feed.feed.x < feed.start.x) return feed.start.x - 1
        for (x in feed.start.x..feed.feed.x) {
            val belt = entityAt(Coordinate(x, feed.row)) as? BeltEntity
            if (belt == null || belt.material != feed.material || belt.direction != EAST) {
                return feed.start.x - 1
            }
        }
        return feed.feed.x
    }

    /**
     * Removes empty or spliceable corridors from the layout until none is left. Every corridor is
     * validated before it is kept; electricity is placed afterward, so poles follow the compacted
     * geometry.
     */
    private fun compact() {
        repeat(MAX_COMPACTION_PASSES) {
            val vertical =
                nextCompaction(
                    placed.toList(),
                    SeamOrientation.VERTICAL,
                    pinned = pinnedImportTiles,
                    westBound = compactionWestBound,
                )
            val horizontal = nextCompaction(placed.toList(), SeamOrientation.HORIZONTAL)
            val chosen =
                when {
                    vertical == null -> horizontal
                    horizontal == null -> vertical
                    vertical.width >= horizontal.width -> vertical
                    else -> horizontal
                } ?: return
            val saved = placed.toList()
            replaceAll(chosen.entities)
            // a corridor can expose a longer underground run; grow it again before the next one
            stretchUndergroundRuns()
            if (validateLayout(placed).isNotEmpty()) {
                replaceAll(saved)
                return
            }
            // the whole import group moved together, so its pins and the no-pole zone move with it
            if (chosen.pinnedShift != 0) {
                if (!beltEnd.isChest) poleWestBound += chosen.pinnedShift
                val moved =
                    pinnedImportTiles.mapTo(mutableSetOf()) { tile ->
                        if (chosen.orientation == SeamOrientation.VERTICAL) {
                            tile.east(chosen.pinnedShift)
                        } else {
                            tile.south(chosen.pinnedShift)
                        }
                    }
                pinnedImportTiles.clear()
                pinnedImportTiles += moved
            }
        }
    }

    /**
     * Wires every pair of poles within reach. A blueprint does not rebuild pole wires on its own,
     * so they have to be part of it.
     */
    private fun wirePoles() {
        val poles = placed.withIndex().filter { it.value is PoleEntity }
        for (i in poles.indices) {
            for (j in i + 1 until poles.size) {
                val a = poles[i]
                val b = poles[j]
                if (a.value.area.topLeft.distanceSq(b.value.area.topLeft) <= WIRE_REACH_SQ) {
                    val first = placed[a.index] as PoleEntity
                    val second = placed[b.index] as PoleEntity
                    placed[a.index] = first.copy(neighbours = first.neighbours + (b.index + 1))
                    placed[b.index] = second.copy(neighbours = second.neighbours + (a.index + 1))
                }
            }
        }
    }

    /**
     * The circuit wires a chest marker needs: the output marker's inserters read the chest through
     * their circuit condition, so each has to be wired to it. A blueprint does not rebuild a
     * circuit wire on its own (just like a pole wire), so the connection must be part of it.
     */
    fun chestWires(): List<BlueprintWire> {
        val wires = mutableListOf<BlueprintWire>()
        placed.forEachIndexed { index, entity ->
            // only the output marker reads the chest; the input marker has no circuit condition
            if (entity !is ChestEntity || entity.circuitConditionEnabled != false) {
                return@forEachIndexed
            }
            val chest = entity.area.topLeft
            for (dx in listOf(-1, 1)) {
                val inserter =
                    placed.withIndex().firstOrNull { (_, candidate) ->
                        candidate is InserterEntity &&
                            candidate.circuitCondition != null &&
                            candidate.area.topLeft == Coordinate(chest.x + dx, chest.y)
                    } ?: continue
                val chestNumber = index + 1
                val inserterNumber = inserter.index + 1
                wires +=
                    if (dx < 0) {
                        BlueprintWire(inserterNumber, 1, chestNumber, 1)
                    } else {
                        BlueprintWire(chestNumber, 1, inserterNumber, 1)
                    }
            }
        }
        return wires
    }

    private fun copiesOf(recipe: PlannedRecipe): Int =
        // a machine count that should be whole but carries floating point noise must not round up
        ceil(recipe.machines - 1e-6).toInt().coerceIn(1, MAX_COPIES)

    private fun footprint(recipe: PlannedRecipe) =
        blueprintMachineFootprint(recipe.factory.identifier)

    /** How many inserters one machine needs to pull [materials] out of its lane. */
    private fun insertersFor(recipe: PlannedRecipe, materials: List<Material>): Int {
        if (materials.isEmpty()) return 0
        val rate = materials.sumOf { inputPerMachine(recipe.production, it) }
        return maxOf(1, ceil(logistics.beltInserterCount(rate)).toInt())
    }

    /**
     * A list of inserters per machine edge: one entry per inserter, `true` for a long one (the far
     * lane). The two lanes are interleaved; when the edge is too short for both, the counts are
     * scaled down fairly.
     */
    private fun inserterPattern(near: Int, far: Int, width: Int): List<Boolean> {
        var short = near
        var long = far
        // leave the machine's last column free: a pole can sit there and reach the machine row
        val edge = maxOf(1, width - 1)
        if (short + long > edge) {
            short = (short.toDouble() * edge / (short + long)).roundToInt().coerceIn(0, edge)
            long = edge - short
            if (near == 0) {
                short = 0
                long = edge
            }
            if (far == 0) {
                short = edge
                long = 0
            }
        }
        val pattern = mutableListOf<Boolean>()
        while (short > 0 || long > 0) {
            if (short > 0) {
                pattern += false
                short--
            }
            if (long > 0) {
                pattern += true
                long--
            }
        }
        return pattern
    }

    /**
     * The depth of every allocation: how many production steps away from the sources it is, used to
     * order the rows. Band links count like any other input, so a band still sits below what feeds
     * it, and a cycle cannot recurse forever.
     */
    private fun computeDepth() {
        val producer = mutableMapOf<Material, Int>()
        for ((index, recipe) in plan.recipes.withIndex()) {
            for (material in recipe.recipe.result.keys) producer.getOrPut(material) { index }
        }
        val visiting = mutableSetOf<Recipe>()
        fun depthOf(index: Int): Int {
            depth[index]?.let {
                return it
            }
            val allocation = plan.recipes[index]
            if (!visiting.add(allocation.recipe)) return 0
            val inputs =
                allocation.recipe.ingredients.keys.filter {
                    it in producer && producer.getValue(it) != index
                }
            val value =
                if (inputs.isEmpty()) 0 else 1 + inputs.maxOf { depthOf(producer.getValue(it)) }
            visiting.remove(allocation.recipe)
            depth[index] = value
            return value
        }
        for (index in plan.recipes.indices) depthOf(index)
    }

    /** One row to place: a direct-feed band, or a single allocation built on its own. */
    private class RowUnit(
        /** What the row is built from, so two rows with the same depth keep a stable order. */
        val label: String,
        val depth: Int,
        val band: PlannedBand?,
        val allocation: Int,
    )

    /** The rows to place, top to bottom: the deepest consumers first, then by name. */
    private fun rowUnits(): List<RowUnit> {
        val inBand = mutableSetOf<Int>()
        for (band in plan.bands) inBand += band.machines
        val units = mutableListOf<RowUnit>()
        for (band in plan.bands) {
            val members = band.machines.distinct()
            units +=
                RowUnit(
                    label = members.map { plan.recipes[it].recipe.name }.sorted().joinToString("+"),
                    depth = members.maxOf { depth[it] ?: 0 },
                    band = band,
                    allocation = members.first(),
                )
        }
        for (index in plan.recipes.indices) {
            if (index in inBand) continue
            units +=
                RowUnit(
                    label = plan.recipes[index].recipe.name,
                    depth = depth[index] ?: 0,
                    band = null,
                    allocation = index,
                )
        }
        return units.sortedWith(compareBy({ it.depth }, { it.label }))
    }

    private fun placeRows() {
        var y = 0
        for (unit in rowUnits()) {
            val row =
                if (unit.band != null) placeBand(unit.band, y) else placeRow(unit.allocation, y)
            for (machine in row.machines.distinctBy { it.allocation }) rows[machine.allocation] =
                row
            placedRows += row
            y = row.outputLaneRow + 3 + BAND_GAP
        }
    }

    /** The input lanes of a row and the machine rows they need. */
    private class RowShape(
        val factoryTop: Int,
        val laneNearRow: Int?,
        val laneFarRow: Int?,
        val laneBelowRow: Int?,
        val outputLaneRow: Int,
    )

    private fun rowShape(solids: List<Material>, height: Int, bandTop: Int): RowShape {
        val lanes =
            when {
                solids.isEmpty() -> 0
                solids.size == 1 -> 1
                else -> 2
            }
        val factoryTop = bandTop + lanes + 1
        val factoryBottomIncl = factoryTop + height - 1
        return RowShape(
            factoryTop = factoryTop,
            laneNearRow = if (lanes >= 1) factoryTop - 2 else null,
            laneFarRow = if (lanes >= 2) factoryTop - 3 else null,
            // a third solid input goes on a second belt below the row
            laneBelowRow = if (solids.size >= 3) factoryBottomIncl + 3 else null,
            outputLaneRow = factoryBottomIncl + 2,
        )
    }

    /** Builds one allocation as a row of [copies] touching machines of the same recipe. */
    private fun placeRow(allocation: Int, bandTop: Int): Row {
        val recipe = plan.recipes[allocation]
        val size = footprint(recipe)
        val copies = copiesOf(recipe)
        val solids =
            recipe.recipe.ingredients.keys.filter { !logistics.isFluid(it) }.sortedBy { it.name }
        val shape = rowShape(solids, size.height, bandTop)
        // a machine that takes a fluid needs a free side for its pipe, so those copies get a gap
        val hasFluid = recipe.recipe.ingredients.keys.any { logistics.isFluid(it) }
        val stride = size.width + if (hasFluid) 1 else 0
        val machines = List(copies) { PlacedMachine(allocation, LANE_MARGIN + it * stride) }
        val row =
            Row(
                machines = machines,
                left = LANE_MARGIN,
                factoryTop = shape.factoryTop,
                width = size.width,
                height = size.height,
                stride = stride,
                solids = solids,
                laneNearRow = shape.laneNearRow,
                laneFarRow = shape.laneFarRow,
                laneBelowRow = shape.laneBelowRow,
                outputLaneRow = shape.outputLaneRow,
            )
        for (machine in machines) {
            place(
                MachineEntity(
                    Coordinate(machine.left, row.factoryTop).area(size),
                    recipe.factory.identifier,
                    recipe.recipe.name,
                ),
            )
        }
        placeMachines(row, emptySet())
        placeLanes(row)
        placeProduct(row, emptySet())
        return row
    }

    /**
     * Builds a direct-feed band as one row of interleaved machines: every machine sits a tile apart
     * so the gap column between two of them can carry the inserters that move their intermediate,
     * and the whole band shares the lane rows and the output belt.
     */
    private fun placeBand(band: PlannedBand, bandTop: Int): Row {
        val internal = band.links.mapTo(mutableSetOf()) { it.material }
        val allocations = band.machines.distinct().map { plan.recipes[it] }
        val size = footprint(allocations.first())
        val solids =
            allocations
                .flatMap { it.recipe.ingredients.keys }
                .filter { !logistics.isFluid(it) && it !in internal }
                .distinct()
                .sortedBy { it.name }
        val shape = rowShape(solids, size.height, bandTop)
        val stride = size.width + 1
        val machines =
            band.machines.mapIndexed { index, allocation ->
                PlacedMachine(allocation, LANE_MARGIN + index * stride)
            }
        val row =
            Row(
                machines = machines,
                left = LANE_MARGIN,
                factoryTop = shape.factoryTop,
                width = size.width,
                height = size.height,
                stride = stride,
                solids = solids,
                laneNearRow = shape.laneNearRow,
                laneFarRow = shape.laneFarRow,
                laneBelowRow = shape.laneBelowRow,
                outputLaneRow = shape.outputLaneRow,
                interfaces = band.links,
            )
        for (machine in machines) {
            val allocation = plan.recipes[machine.allocation]
            place(
                MachineEntity(
                    Coordinate(machine.left, row.factoryTop).area(size),
                    allocation.factory.identifier,
                    allocation.recipe.name,
                ),
            )
        }
        placeMachines(row, internal)
        placeLanes(row)
        placeInterfaces(row)
        placeProduct(row, internal)
        return row
    }

    /**
     * Places the inserters of every machine of [row]. A machine only takes the materials *it*
     * needs, from the lane that material sits on, and a machine that takes a fluid keeps the tiles
     * of its ports free for the pipe.
     */
    private fun placeMachines(row: Row, internal: Set<Material>) {
        for (machine in row.machines) {
            val allocation = plan.recipes[machine.allocation]
            val recipe = allocation.recipe
            // a fluid pipe sits on the machine edge just outside its port tile; keep those tiles
            // free of inserters so the fluid can actually connect. A recipe that uses no fluid
            // keeps
            // the original inserter spread, ports and all.
            val usesFluid =
                recipe.ingredients.keys.any { logistics.isFluid(it) } ||
                    recipe.result.keys.any { logistics.isFluid(it) }
            val ports = blueprintMachineFluidPorts(allocation.factory.identifier)
            val topPortColumns =
                if (usesFluid) {
                    ports.filter { it.tile.y == -1 }.mapTo(mutableSetOf()) { it.tile.x }
                } else {
                    emptySet()
                }
            val bottomPortColumns =
                if (usesFluid) {
                    ports.filter { it.tile.y == row.height }.mapTo(mutableSetOf()) { it.tile.x }
                } else {
                    emptySet()
                }

            // a short inserter reaches the near lane one tile up, a long one the far lane two tiles
            // up; a lane whose rate needs more than one inserter gets several, spread over the edge
            val near = row.solids.getOrNull(0)?.takeIf { it in recipe.ingredients }
            val far = row.solids.getOrNull(1)?.takeIf { it in recipe.ingredients }
            val pattern =
                inserterPattern(
                    near?.let { insertersFor(allocation, listOf(it)) } ?: 0,
                    far?.let { insertersFor(allocation, listOf(it)) } ?: 0,
                    row.width,
                )
            // the machine's last column is left free for a pole; a port there stays free for the
            // pipe
            val insertColumns =
                (0 until maxOf(1, row.width - 1)).filterNot(topPortColumns::contains)
            insertColumns.zip(pattern).forEach { (column, long) ->
                // a long inserter reaches the far lane, a short one the near lane
                placeInserter(
                    Coordinate(machine.left + column, row.factoryTop - 1),
                    NORTH,
                    if (long) row.solids[1] else row.solids[0],
                    long,
                )
            }

            // the product inserter and the extra lane's long inserter keep off the bottom fluid
            // ports
            val freeBottomColumns = (0 until row.width).filterNot(bottomPortColumns::contains)
            val productColumn =
                freeBottomColumns.minByOrNull { abs(it - row.width / 2) } ?: (row.width / 2)
            val belowPreferred = if (row.width == 2) 0 else row.width - 1
            val belowColumn =
                freeBottomColumns
                    .filter { it != productColumn }
                    .minByOrNull {
                        abs(it - belowPreferred)
                    } ?: belowPreferred
            // the product is inserted onto the belt below the row
            externalProduct(allocation, internal)?.let {
                placeInserter(
                    Coordinate(machine.left + productColumn, row.factoryBottom + 1),
                    NORTH,
                    it,
                    false,
                )
            }
            // a long inserter reaches over the output belt to a third input belt below the row
            if (row.laneBelowRow != null && row.solids.getOrNull(2) in recipe.ingredients) {
                placeInserter(
                    Coordinate(machine.left + belowColumn, row.factoryBottom + 1),
                    SOUTH,
                    row.solids[2],
                    true,
                )
            }
        }
    }

    /** Places the input lanes of [row] across the whole row, including the gaps of a band. */
    private fun placeLanes(row: Row) {
        fun placeLane(y: Int, material: Material) {
            val direction = laneDirection(row, material)
            for (x in row.left..row.right) {
                placeBelt(Coordinate(x, y), material, direction)
            }
        }
        row.laneNearRow?.let { placeLane(it, row.solids[0]) }
        row.laneFarRow?.let { placeLane(it, row.solids[1]) }
        row.laneBelowRow?.let { placeLane(it, row.solids[2]) }
    }

    /**
     * The material a machine's product leaves the row with: its solid result, unless it is
     * internal.
     */
    private fun externalProduct(allocation: PlannedRecipe, internal: Set<Material>): Material? =
        allocation.recipe.result.keys.firstOrNull { !logistics.isFluid(it) && it !in internal }

    /** Ends the row with an output belt when one of its machines makes something for outside. */
    private fun placeProduct(row: Row, internal: Set<Material>) {
        val product =
            row.machines.firstNotNullOfOrNull {
                externalProduct(plan.recipes[it.allocation], internal)
            }
        product?.let {
            for (x in row.left..row.right) {
                placeBelt(Coordinate(x, row.outputLaneRow), it, EAST)
            }
        }
    }

    /** The machine rows [machine] keeps free for a fluid port on the side facing the gap. */
    private fun portRows(row: Row, machine: PlacedMachine, east: Boolean): Set<Int> {
        val allocation = plan.recipes[machine.allocation]
        val recipe = allocation.recipe
        val usesFluid =
            recipe.ingredients.keys.any { logistics.isFluid(it) } ||
                recipe.result.keys.any { logistics.isFluid(it) }
        if (!usesFluid) return emptySet()
        val edge = if (east) row.width else -1
        return blueprintMachineFluidPorts(allocation.factory.identifier)
            .filter { it.tile.x == edge }
            .mapTo(mutableSetOf()) { row.factoryTop + it.tile.y }
    }

    /**
     * Places the inserters that move a band's internal material straight from one machine into the
     * next. They sit in the gap column between the two machines, on every machine row that neither
     * neighbor needs for a fluid port, as many as fit.
     */
    private fun placeInterfaces(row: Row) {
        for (link in row.interfaces) {
            val left = row.machines[link.gap]
            val right = row.machines[link.gap + 1]
            val blocked = portRows(row, left, east = true) + portRows(row, right, east = false)
            val free = (row.factoryTop..row.factoryBottom).filterNot(blocked::contains)
            val needed = maxOf(1, ceil(logistics.directInserterCount(link.flow)).toInt())
            val x = row.left + link.gap * row.stride + row.width
            for (y in free.take(needed)) {
                placeInserter(
                    Coordinate(x, y),
                    if (link.producerIsLeft) WEST else EAST,
                    link.material,
                    false,
                )
            }
        }
    }

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

    /**
     * Where a lane is fed: imports from the west. Produced materials split by their producer's
     * outlet, the left half feeding from the west and the right half from the east, so two trunks
     * into one row do not have to cross. A lone produced input picks the nearer end.
     */
    private fun laneDirection(row: Row, material: Material): Direction {
        if (material !in producerOf) return EAST
        val produced = row.solids.filter { it in producerOf }.sortedBy { outletX(it) }
        val index = produced.indexOf(material)
        val middle = produced.size / 2
        if (index in 0 until middle) return EAST
        if (index >= (produced.size + 1) / 2) return WEST
        return if (outletX(material) <= (row.left + row.right) / 2) EAST else WEST
    }

    /** The tile east of a material's producer output lane, or Far East when it is not placed. */
    private fun outletX(material: Material): Int {
        val producer = producerOf[material] ?: return Int.MAX_VALUE
        val producerRow = rows[producer] ?: return Int.MAX_VALUE
        return producerRow.right + 1
    }

    private fun laneSink(row: Row, y: Int, material: Material): Sink =
        if (laneDirection(row, material) == EAST) {
            Sink(Coordinate(row.left, y), Coordinate(row.left - 1, y), EAST)
        } else {
            Sink(Coordinate(row.right, y), Coordinate(row.right + 1, y), WEST)
        }

    private fun allSolidSinks() = buildList {
        for (row in placedRows) {
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
            val producer = producerOf[material]
            if (producer != null) {
                val producerRow = rows[producer] ?: continue
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
        val width = connectorWidth
        val columns = connectorLeft..(connectorLeft + width)
        var row = sink.feedRow
        var attempts = 0
        while (
            (row in markerRows || columns.any { !isFree(Coordinate(it, row)) }) && attempts++ < 50
        ) row++
        markerRows += row
        placeSolidConnector(material, connectorLeft, row, input = true)
        val start = Coordinate(connectorLeft + width, row)
        reserve(start, material)
        importFeeds += ImportFeed(material, start, row, sink.feed)
        // a chest marker is powered, so its poles may sit in the import extension
        if (!beltEnd.isChest) poleWestBound = maxOf(poleWestBound, start.x)
        compactionWestBound = maxOf(compactionWestBound, sink.target.x)
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
        placePath(material, nodes, sink.heading)
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
        if (material !in producerOf) return
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
        placePath(material, nodes, sink.heading)
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

    private fun feedFluids() {
        // reserve every fluid's machine edge first, so two fluids never pick touching edges
        val targets = mutableMapOf<Material, MutableList<Coordinate>>()
        for (row in placedRows) {
            for (machine in row.machines) {
                val allocation = plan.recipes[machine.allocation]
                val fluids =
                    allocation.recipe.ingredients.keys
                        .filter { logistics.isFluid(it) }
                        .sortedBy { it.name }
                for (material in fluids) {
                    val tile = fluidEdge(row, machine, material)
                    if (tile == null) {
                        failures +=
                            "${material.name} to ${allocation.factory.identifier} (no fluid port)"
                        continue
                    }
                    reserve(tile, material)
                    board.setEdgeFluid(tile, material)
                    fluidPorts += tile
                    targets.getOrPut(material) { mutableListOf() }.add(tile)
                }
            }
        }
        // prepare a source per fluid up front, then route the groups nearest-first
        val groups =
            targets.entries
                .sortedBy { it.key.name }
                .map { (material, sinks) ->
                    val source = fluidMarker(material, sinks.first())
                    reserve(source, material)
                    PipeGroup(material, source, sinks)
                }
                .sortedBy { it.nearestDistance }
        for (group in groups) routePipeGroup(group)
    }

    /** One fluid to lay: its marker tile, the pipe network grown from it and the taps to reach. */
    private class PipeGroup(
        val material: Material,
        val source: Coordinate,
        val sinks: List<Coordinate>,
    ) {
        val nearestDistance
            get() = sinks.minOf { it.manhattanDist(source) }
    }

    /**
     * Grows this fluid's network to every sink, each time tapping the nearest point that already
     * carries the fluid. A pipe connects on any side, so no splitter is needed.
     */
    private fun routePipeGroup(group: PipeGroup) {
        val network = mutableListOf(group.source)
        val pending = group.sinks.toMutableList()
        while (pending.isNotEmpty()) {
            val sink = pending.minBy { candidate -> network.minOf { it.manhattanDist(candidate) } }
            val candidates =
                network
                    .filter { it == group.source || entityAt(it) is PipeEntity }
                    .sortedBy { it.manhattanDist(sink) }
            var routed = false
            for (start in candidates.take(8)) {
                if (start == sink) {
                    pending.remove(sink)
                    routed = true
                    break
                }
                val segment = fluidRoute(start, EAST, sink, group.material) ?: continue
                placePath(group.material, listOf(start) + segment)
                network += listOf(start) + segment
                pending.remove(sink)
                routed = true
                break
            }
            if (!routed) {
                failures += "${group.material.name} to $sink (no pipe route)"
                pending.remove(sink)
            }
        }
    }

    /**
     * The tile a fluid pipe must occupy to feed one machine of [row]: one of the machine's input
     * ports (or, without a port table, the left then right edge). Null when all are taken.
     */
    private fun fluidEdge(row: Row, machine: PlacedMachine, material: Material): Coordinate? {
        val left = machine.left
        val ports =
            blueprintMachineFluidPorts(plan.recipes[machine.allocation].factory.identifier)
                .filter { it.flow != PortFlow.OUTPUT }
                .map { Coordinate(left + it.tile.x, row.factoryTop + it.tile.y) }
        val candidates = ports.ifEmpty {
            listOf(left - 1, left + row.width).flatMap { x ->
                (row.factoryTop until row.factoryTop + row.height).map { y -> Coordinate(x, y) }
            }
        }
        for (tile in candidates) {
            if (!isFree(tile) || isReserved(tile)) continue
            val clash = steps.any { d ->
                board.edgeFluidAt(tile + d).let { it != null && it != material }
            }
            if (!clash) return tile
        }
        return null
    }

    /** Places a fluid marker and returns the free tile its pipe lane starts on. */
    private fun fluidMarker(material: Material, target: Coordinate): Coordinate {
        // The connector sits just west of the sink, so the fluid only runs a short lane. A solid
        // import keeps a label at the far west and is trimmed after routing; a fluid route is not
        // straight, so its marker is placed near the machine from the start instead.
        val source = Coordinate(target.x - 1, target.y)
        // the connector tiles west of the source: a single infinity pipe or the input/label/output
        // triple that hides the fluid under the combinator (a chest cannot carry a fluid)
        val firstColumn = if (beltEnd == BeltEnd.INFINITY_CHEST) source.x - 1 else source.x - 3
        val columns = (firstColumn..source.x).toList()
        // keep the connector's buried run clear of any other fluid near the machine
        fun touchesForeignFluid(row: Int) = columns.any { x ->
            steps.any { step ->
                val neighbor = fluidAt(Coordinate(x, row) + step)
                neighbor != null && neighbor != material
            }
        }
        var row = target.y
        while (
            row in markerRows ||
                columns.any { !isFree(Coordinate(it, row)) } ||
                touchesForeignFluid(row)
        ) row++
        markerRows += row
        // the cheat variant replaces the label with a single infinity pipe that supplies the fluid
        if (beltEnd == BeltEnd.INFINITY_CHEST) {
            placeInfinityPipe(Coordinate(source.x - 1, row), material)
            compactionWestBound = maxOf(compactionWestBound, target.x)
            return Coordinate(source.x, row)
        }
        // a pipe-to-ground run under the label, so the label does not block the fluid
        placeEnd(material, Coordinate(source.x - 3, row), EAST, INPUT)
        placeMarker(Coordinate(source.x - 2, row), Signal(material, fluid = true))
        placeEnd(material, Coordinate(source.x - 1, row), EAST, OUTPUT)
        markTunnel(Coordinate(source.x - 3, row), Coordinate(source.x - 1, row), endName(material))
        compactionWestBound = maxOf(compactionWestBound, target.x)
        return Coordinate(source.x, row)
    }

    /** Extends the target's output belt and ends it in the selected solid connector. */
    private fun placeTargetOutput() {
        val targetMaterial = plan.target
        val producer = producerOf[targetMaterial] ?: return
        val recipe = plan.recipes[producer]
        val row = rows[producer] ?: return
        if (!recipe.recipe.result.keys.any { !logistics.isFluid(it) }) return
        var coordinate = Coordinate(row.right + 1, row.outputLaneRow)

        while (coordinate.x <= row.right + 3 && isFree(coordinate)) {
            placeBelt(coordinate, targetMaterial, EAST)
            coordinate = coordinate.east()
        }
        val fits = (0 until connectorWidth).all { isFree(coordinate.east(it)) }
        if (fits) {
            // the output label sits above the product line, just like an imported input
            placeSolidConnector(targetMaterial, coordinate.x, coordinate.y, input = false)
        } else {
            val spot = markerSpot(coordinate.west())
            placeMarker(spot, Signal(targetMaterial))
        }
    }

    /** Connects a fluid output pipe to each target machine, so its product has somewhere to go. */
    private fun placeTargetFluidOutput() {
        if (!logistics.isFluid(plan.target)) return
        val producer = producerOf[plan.target] ?: return
        val recipe = plan.recipes[producer]
        val row = rows[producer] ?: return
        val ports =
            blueprintMachineFluidPorts(recipe.factory.identifier).filter {
                it.flow != PortFlow.INPUT
            }
        for (machine in row.machines) {
            if (plan.recipes[machine.allocation].recipe !== recipe.recipe) continue
            val left = machine.left
            val candidates =
                ports
                    .map { Coordinate(left + it.tile.x, row.factoryTop + it.tile.y) }
                    .ifEmpty {
                        (row.factoryTop until row.factoryTop + row.height).map { y ->
                            Coordinate(left + row.width, y)
                        }
                    }
            for (coordinate in candidates) {
                if (isFree(coordinate)) {
                    placePipe(coordinate, plan.target)
                    fluidPorts += coordinate
                    break
                }
            }
        }
    }

    private fun fluidRoute(
        from: Coordinate,
        direction: Direction,
        target: Coordinate,
        fluid: Material,
    ) =
        route(
            from,
            direction,
            target,
            { tile, _ -> tile == target || pipeAllowed(tile, fluid) },
            pipeProfile,
            // a segment grows out of an existing plain pipe, which cannot become a tunnel end:
            // it has to leave the network on the surface for one tile before it may dive
            strictStart = true,
        )

    /** A pipe tile is free only when it touches no pipe carrying a different fluid. */
    private fun pipeAllowed(coordinate: Coordinate, fluid: Material) =
        isFree(coordinate) &&
            !isReserved(coordinate) &&
            steps.none { d ->
                val cd = coordinate + d
                val placedFluid = fluidAt(cd)
                val reservedFor = board.edgeFluidAt(cd)
                (placedFluid != null && placedFluid != fluid) ||
                    (reservedFor != null && reservedFor != fluid)
            }

    /** A tile's role in a route: a plain belt, a tunnel input or a tunnel output, all facing. */
    private sealed interface PathRole {
        val direction: Direction
    }

    private data class SurfaceRole(override val direction: Direction) : PathRole

    private data class TunnelInputRole(override val direction: Direction) : PathRole

    private data class TunnelOutputRole(override val direction: Direction) : PathRole

    /**
     * Draws the transport entities of [material] along [nodes]; a jump becomes an underground run.
     * A route may revisit a tile: to enter a tunnel a belt must already head straight, so the
     * search can step back onto a tile to turn first. A tile carries one entity, so each gets its
     * final role from [roles] before anything is placed. A fluid material routes as pipes.
     */
    private fun placePath(
        material: Material,
        nodes: List<Coordinate>,
        finalHeading: Direction? = null,
    ) {
        for ((tile, role) in roles(nodes, finalHeading)) {
            if (!isFree(tile)) continue
            when (role) {
                is SurfaceRole -> placeSurface(material, tile, role.direction)
                is TunnelInputRole -> placeEnd(material, tile, role.direction, INPUT)
                is TunnelOutputRole -> placeEnd(material, tile, role.direction, OUTPUT)
            }
        }
        markTunnels(nodes, endName(material))
    }

    /**
     * The role of every tile in [nodes], keyed by tile. A tunnel end beats a plain belt, so a
     * revisit never breaks a paired run; otherwise the last occurrence wins. [finalHeading] is the
     * direction the last tile must face, so a feed can turn onto its target regardless of the side
     * the route arrived from.
     */
    private fun roles(
        nodes: List<Coordinate>,
        finalHeading: Direction?,
    ): Map<Coordinate, PathRole> {
        if (nodes.size <= 1) return emptyMap()

        val roles = LinkedHashMap<Coordinate, PathRole>()

        fun canBeAssigned(tile: Coordinate) =
            when (roles[tile]) {
                is TunnelInputRole,
                is TunnelOutputRole -> false
                else -> true
            }

        fun assign(tile: Coordinate, role: SurfaceRole) {
            if (canBeAssigned(tile)) roles[tile] = role
        }

        for ((tile, next) in nodes.zipWithNext()) {
            val direction = directionBetween(tile, next)
            if (tile.adjacent(next)) {
                val surfaceRole = SurfaceRole(direction)
                assign(tile, surfaceRole)
                assign(next, surfaceRole)
            } else {
                roles[tile] = TunnelInputRole(direction)
                roles[next] = TunnelOutputRole(direction)
            }
        }
        if (finalHeading != null) roles[nodes.last()] = SurfaceRole(finalHeading)

        return roles
    }

    private fun directionBetween(from: Coordinate, to: Coordinate) =
        when {
            to.x > from.x -> EAST
            to.x < from.x -> WEST
            to.y > from.y -> SOUTH
            else -> NORTH
        }

    /** Covers every machine, inserter and loader with a pole and wires the poles together. */
    private fun placePoles() {
        val machineAreas =
            placed.filter { it.isPowered }.map { it.area }.sortedWith(compareBy({ it.y }, { it.x }))
        if (machineAreas.isEmpty()) return

        val covered = mutableSetOf<Coordinate>()
        val poles = mutableSetOf<Coordinate>()
        for (area in machineAreas) {
            if (!area.any(covered::contains)) {
                val spot = bestPoleSpot(area, covered) ?: continue
                poles += spot
                placePole(spot)
                covered.addAll(spot.area().grow(SUPPLY_RADIUS))
            }
        }
        connectPoles(poles)
    }

    /** The free tile near [tile] that powers the most not-yet-[covered] machines, if any. */
    private fun bestPoleSpot(tile: Area, covered: Set<Coordinate>) =
        tile
            .grow(SUPPLY_RADIUS)
            .filter { isFree(it) && it.x >= poleWestBound }
            .maxByOrNull {
                it.area().grow(SUPPLY_RADIUS).sumOf { other ->
                    when {
                        other in covered -> 0
                        entityAt(other)?.isPowered == true -> 2
                        else -> 1
                    }
                }
            }

    /** Adds poles until every pole can reach another, so the whole set is one wire network. */
    private fun connectPoles(poles: MutableSet<Coordinate>) {
        val components = poleComponents(poles)
        repeat(40) {
            if (components.size <= 1) return

            var bestDistanceSq = Long.MAX_VALUE
            var from: Coordinate? = null
            var to: Coordinate? = null
            var fromIndex = -1
            var toIndex = -1

            for (i in components.indices) {
                for (j in i + 1 until components.size) {
                    for (a in components[i]) {
                        for (b in components[j]) {
                            val distanceSq = a.distanceSq(b)
                            if (distanceSq < bestDistanceSq) {
                                bestDistanceSq = distanceSq
                                from = a
                                to = b
                                fromIndex = i
                                toIndex = j
                            }
                        }
                    }
                }
            }
            if (from == null || to == null) return

            val steps =
                ceil(sqrt(bestDistanceSq.toDouble()) / (WIRE_REACH - 1)).toInt().coerceAtLeast(1)
            var added = false

            for (step in 1 until steps) {
                val fraction = step.toDouble() / steps
                val x = round(from.x + (to.x - from.x) * fraction).toInt()
                val y = round(from.y + (to.y - from.y) * fraction).toInt()
                val spot = freeNear(Coordinate(x, y)) ?: continue
                poles += spot
                placePole(spot)
                components[fromIndex] += spot
                added = true
            }
            if (!added) return

            components[fromIndex] += components[toIndex]
            components.removeAt(toIndex)
        }
    }

    /** The wire-connected groups of [poles], two poles being connected within the wire reach. */
    private fun poleComponents(poles: Set<Coordinate>): MutableList<MutableList<Coordinate>> {
        val seen = mutableSetOf<Coordinate>()
        val components = mutableListOf<MutableList<Coordinate>>()
        for (pole in poles) {
            if (!seen.add(pole)) continue
            val component = mutableListOf<Coordinate>()
            val queue = ArrayDeque<Coordinate>()
            var current: Coordinate? = pole
            while (current != null) {
                component += current
                // this is a job for a quadtree!
                for (other in poles) {
                    if (other !in seen) {
                        val reach = current.distanceSq(other)
                        if (reach <= WIRE_REACH_SQ) {
                            seen += other
                            queue += other
                        }
                    }
                }
                current = queue.removeFirstOrNull()
            }
            components += component
        }
        return components
    }

    /** The nearest free tile within one tile of ([c]), if any. */
    private fun freeNear(c: Coordinate): Coordinate? {
        for (radius in 0..1) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val toTest = Coordinate(c.x + dx, c.y + dy)
                    if (isFree(toTest) && toTest.x >= poleWestBound) return toTest
                }
            }
        }
        return null
    }

    private fun routable(coordinate: Coordinate) = isFree(coordinate) && !isReserved(coordinate)

    /**
     * Places the selected solid belt connector for [material] on [row], starting at column [left]:
     * a constant-combinator label, an infinity chest or a chest the inserters feed. [input] marks a
     * connector that supplies an imported belt, whose first tile is the tunnel input; the target
     * output passes `false` so a cheat chest loads from the belt instead.
     */
    private fun placeSolidConnector(material: Material, left: Int, row: Int, input: Boolean) {
        when (beltEnd) {
            BeltEnd.CONSTANT_COMBINATOR -> {
                // the label sits above an underground run, so the belt can pass underneath
                placeEnd(material, Coordinate(left, row), EAST, INPUT)
                placeMarker(Coordinate(left + 1, row), Signal(material))
                placeEnd(material, Coordinate(left + 2, row), EAST, OUTPUT)
                markTunnel(Coordinate(left, row), Coordinate(left + 2, row), endName(material))
            }
            BeltEnd.INFINITY_CHEST -> {
                if (input) {
                    placeInfinityChest(Coordinate(left, row), InfinitySettings(item = material))
                    placeLoader(Coordinate(left + 1, row), EAST, OUTPUT, material)
                } else {
                    placeLoader(Coordinate(left, row), EAST, INPUT, material)
                    placeInfinityChest(
                        Coordinate(left + 2, row),
                        InfinitySettings(removeUnfiltered = true),
                    )
                }
            }
            else -> placeChestConnector(material, left, row, input)
        }
    }

    /**
     * Places a chest marker: a five-tile connector whose belt tunnels under the chest, with an
     * inserter on each side. An input marker feeds the belt out of the chest, an output marker
     * fills the chest from the belt, and the plain wooden and steel chests do both at once.
     */
    private fun placeChestConnector(material: Material, left: Int, row: Int, input: Boolean) {
        placeEnd(material, Coordinate(left, row), EAST, INPUT)
        placeEnd(material, Coordinate(left + 4, row), EAST, OUTPUT)
        markTunnel(Coordinate(left, row), Coordinate(left + 4, row), endName(material))
        val buffer = beltEnd == BeltEnd.BUFFER_CHEST
        val count = if (buffer) if (input) 100 else 200 else 1
        when {
            beltEnd == BeltEnd.WOODEN_CHEST || beltEnd == BeltEnd.STEEL_CHEST -> {
                // both inserters face west, so the chest buffers a pass-through belt
                placeConnectorInserter(material, Coordinate(left + 1, row), WEST)
                placeConnectorInserter(material, Coordinate(left + 3, row), WEST)
                placeChest(Coordinate(left + 2, row), bar = 4)
            }
            input -> {
                placeConnectorInserter(material, Coordinate(left + 1, row), EAST)
                placeConnectorInserter(material, Coordinate(left + 3, row), WEST, mirror = true)
                placeChest(
                    Coordinate(left + 2, row),
                    requestFilter = RequestFilter(material.name, count, buffer),
                )
            }
            else -> {
                val condition = CircuitCondition(material.name, "<", 200)
                placeConnectorInserter(
                    material,
                    Coordinate(left + 1, row),
                    WEST,
                    circuitCondition = condition,
                )
                placeConnectorInserter(
                    material,
                    Coordinate(left + 3, row),
                    EAST,
                    circuitCondition = condition,
                )
                placeChest(
                    Coordinate(left + 2, row),
                    requestFilter = RequestFilter(material.name, count, buffer),
                    circuitConditionEnabled = false,
                )
            }
        }
    }

    /** Places a chest marker's inserter (always one tile) of the selected tier. */
    private fun placeConnectorInserter(
        material: Material,
        coordinate: Coordinate,
        direction: Direction,
        mirror: Boolean = false,
        circuitCondition: CircuitCondition? = null,
    ) {
        place(
            InserterEntity(
                coordinate.area(),
                inserter,
                material,
                direction,
                long = false,
                mirror = mirror,
                circuitCondition = circuitCondition,
            ),
        )
    }

    /** Places a chest marker (always one tile) of the selected type. */
    private fun placeChest(
        coordinate: Coordinate,
        requestFilter: RequestFilter? = null,
        bar: Int? = null,
        circuitConditionEnabled: Boolean? = null,
    ) {
        place(
            ChestEntity(
                coordinate.area(),
                beltEnd.identifier,
                requestFilter = requestFilter,
                bar = bar,
                circuitConditionEnabled = circuitConditionEnabled,
            ),
        )
    }

    /** True when a belt next to [coordinate] pours a different material into that tile. */
    private fun sideLoaded(coordinate: Coordinate, material: Material): Boolean = steps.any { d ->
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

    private fun placeEnd(
        material: Material,
        coordinate: Coordinate,
        direction: Direction,
        io: Io,
    ) = board.placeEnd(material, coordinate, direction, io)

    private fun placeInserter(
        coordinate: Coordinate,
        direction: Direction,
        material: Material,
        long: Boolean,
    ) = board.placeInserter(coordinate, direction, material, long)

    private fun placeLoader(
        coordinate: Coordinate,
        direction: Direction,
        io: Io,
        material: Material,
    ) = board.placeLoader(coordinate, direction, io, material)

    private fun placeMarker(coordinate: Coordinate, signal: Signal) =
        board.placeMarker(coordinate, signal)

    private fun placePipe(coordinate: Coordinate, fluid: Material) =
        board.placePipe(coordinate, fluid)

    private fun placeInfinityChest(coordinate: Coordinate, settings: InfinitySettings) =
        board.placeInfinityChest(coordinate, settings)

    private fun placeInfinityPipe(coordinate: Coordinate, fluid: Material) =
        board.placeInfinityPipe(coordinate, fluid)

    private fun placePole(coordinate: Coordinate) = board.placePole(coordinate)

    private fun removeAt(tile: Coordinate) = board.removeAt(tile)

    private fun replaceAll(entities: List<LayoutEntity>) = board.replaceAll(entities)

    private fun rebuildTunnels() = board.rebuildTunnels()

    private fun markTunnel(from: Coordinate, to: Coordinate, type: String) =
        board.markTunnel(from, to, type)

    private fun markTunnels(nodes: List<Coordinate>, type: String) = board.markTunnels(nodes, type)

    private fun endName(material: Material) = board.endName(material)

    private fun stretchUndergroundRuns() = UndergroundStretcher(board, fluidPorts).stretchAll()

    private fun findRoute(
        from: Coordinate,
        fromDirection: Direction,
        to: Coordinate,
        allowed: (Coordinate, Direction) -> Boolean,
        strictStart: Boolean = false,
    ) = router.findRoute(from, fromDirection, to, allowed, strictStart)

    private fun route(
        from: Coordinate,
        heading: Direction,
        to: Coordinate,
        allowed: (Coordinate, Direction) -> Boolean,
        profile: RouteProfile,
        strictStart: Boolean = false,
    ) = router.route(from, heading, to, allowed, profile, strictStart)

    private fun entityAt(coordinate: Coordinate) = board.entityAt(coordinate)

    /** The fluid the pipe or pipe-to-ground at [coordinate] carries, if any. */
    private fun fluidAt(coordinate: Coordinate) = board.fluidAt(coordinate)

    private fun isFree(coordinate: Coordinate) = board.isFree(coordinate)

    private fun isReserved(coordinate: Coordinate) = board.isReserved(coordinate)

    private fun reserve(coordinate: Coordinate, material: Material) =
        board.reserve(coordinate, material)
}
