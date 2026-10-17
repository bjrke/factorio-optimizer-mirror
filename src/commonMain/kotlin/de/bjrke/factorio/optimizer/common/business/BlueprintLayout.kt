package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.util.MinHeap
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe bands, so their belts can be routed between them. */
private const val BAND_GAP = 14

/** Upper bound on the copies of a recipe, so a huge rate cannot explode the blueprint. */
private const val MAX_COPIES = 9

/** Medium poles supply a 7x7 area (radius 3) and reach 9 tiles of wire. */
private const val SUPPLY_RADIUS = 3

private const val WIRE_REACH = 9
private const val WIRE_REACH_SQ = WIRE_REACH * WIRE_REACH

private const val MARKER = "constant-combinator"
private const val INFINITY_CHEST = "infinity-chest"
private const val PIPE = "pipe"
private const val PIPE_TO_GROUND = "pipe-to-ground"
private const val POLE = "medium-electric-pole"

/** West edge no feed route may cross, so a route cannot run around the import lanes. */
private const val ROUTE_WEST_BOUND = 2

/** How far past everything placed a route may still search, so A* stays near the blueprint. */
private const val ROUTE_MARGIN = 24

/** The maximum length of a pipe-to-ground run, matching the prototype's `max_distance`. */
private const val PIPE_UNDERGROUND_RANGE = 9

/** A search node: a tile, the direction index the route entered it from, and a forced flag. */
private data class RouteNode(val tile: Coordinate, val heading: Direction, val forced: Boolean)

/**
 * What makes belts and pipes route differently: the tile costs, the tunnel range and whether a
 * tunnel may only be started over an obstacle (belts) or anywhere (pipes).
 */
private class RouteProfile(
    /** Cost of one straight surface tile. */
    val surfaceCost: Int = 4,
    /** Added when a surface route changes direction, so belts prefer to run straight. */
    val turnCost: Int = 1,
    /** Per-tile cost of an underground tile; it differs between belts and pipes. */
    val undergroundCost: Int,
    /** Fixed cost of entering an underground run; it differs between belts and pipes. */
    val tunnelEntry: Int,
    /**
     * Added to a surface tile outside the start-to-goal corridor, to prefer a tunnel over a detour.
     */
    val detourCost: Int = 2,
    /** Added when a route is forced onto its sink's own tile, so the feed still faces the lane. */
    val sinkCost: Int = 12,
    /** Added to a surface tile next to a reserved sink tile, so a feed cannot box its lane in. */
    val keepClearCost: Int = 6,
    /** Maximum length of a run, matching the prototype's `max_distance`. */
    val range: Int,
    /** The underground entity this profile places. */
    val undergroundType: String,
    /** Whether a tunnel may only be started over an obstacle (belts) or anywhere (pipes). */
    val tunnelOnlyWhenBlocked: Boolean,
)

/** One queued A* entry; ties break on `g` then insertion order, so routes are reproducible. */
private class RouteEntry(
    val f: Int,
    val g: Int,
    val seq: Int,
    val node: RouteNode,
) : Comparable<RouteEntry> {
    override fun compareTo(other: RouteEntry): Int {
        if (f != other.f) return f.compareTo(other.f)
        if (g != other.g) return g.compareTo(other.g)
        return seq.compareTo(other.seq)
    }
}

/**
 * Lays out [plan] as rows of connected factories. Every recipe is one row of touching machines, fed
 * by belt lanes just above (a short inserter for the near lane, a long inserter for the far one)
 * and drained by an output belt just below. Rows flow top to bottom; imports enter from the left
 * and a material built inside the plan is fanned out of its producer's output belt to every recipe
 * that takes it. Fluids use pipe clusters next to the machines instead of belts.
 *
 * When [cheats] is set, each imported input gets an infinity chest feeding the belt and the target
 * output ends in an infinity chest, so a blueprint can be tested without building the item supply.
 * (Both are cheat items and off by default.)
 */
fun buildBlueprint(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    cheats: Boolean = false,
) =
    Blueprint(
        label = plan.label,
        icon = plan.target,
        entities = RowLayout(plan, logistics, cheats).build(),
    )

/** Inputs whose belt could not be routed; empty for a valid blueprint. Used by tests. */
internal fun blueprintFeedFailures(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    cheats: Boolean = false,
) =
    RowLayout(plan, logistics, cheats).run {
        build()
        failures
    }

/**
 * The material each transport entity carries, keyed by tile. Used by tests to check nothing merges.
 */
internal fun blueprintTransportMaterials(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    cheats: Boolean = false,
) =
    RowLayout(plan, logistics, cheats).run {
        build()
        transportMaterials()
    }

/** The fluid each pipe or pipe-to-ground tile carries. Used by tests to check no fluids merge. */
internal fun blueprintPipeFluids(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    cheats: Boolean = false,
) =
    RowLayout(plan, logistics, cheats).run {
        build()
        pipeFluids()
    }

/** The placed entities and the feed routes that failed, for the CLI's text layout. */
internal fun blueprintLayoutDump(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    cheats: Boolean = false,
) =
    RowLayout(plan, logistics, cheats).run {
        build()
        LayoutDump(placedEntities(), failures.toList())
    }

private class RowLayout(
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val cheats: Boolean,
) {
    private val belt = logistics.belt.identifier
    private val inserter = logistics.inserter.identifier
    private val longInserter = InserterTier.LONG_HANDED_INSERTER.identifier
    private val splitter = belt.replace("transport-belt", "splitter")
    private val undergroundBelt = belt.replace("transport-belt", "underground-belt")

    /** The loader of the same tier as the belt, used as a test input/output. */
    private val loader = belt.replace("transport-belt", "loader")

    /** Maximum length of an underground run, matching the prototype's `max_distance`. */
    private val undergroundRange =
        when {
            belt.contains("turbo") -> 11
            belt.contains("express") -> 9
            belt.contains("fast") -> 7
            else -> 5
        }

    private val placed = mutableListOf<LayoutEntity>()

    /**
     * Belts tunnel only over an obstacle, so surface stays their first choice; a slight per-tile
     * discount keeps a blocked tile tunnelled rather than detoured, while [stretchUndergroundRuns]
     * decides how long the run becomes.
     */
    private val beltProfile =
        RouteProfile(
            undergroundCost = 3,
            tunnelEntry = 1,
            range = undergroundRange,
            undergroundType = undergroundBelt,
            tunnelOnlyWhenBlocked = true,
        )

    /** Pipes may tunnel anywhere and prefer to: the player walks over a buried pipe, not a pipe. */
    private val pipeProfile =
        RouteProfile(
            undergroundCost = 2,
            tunnelEntry = 2,
            range = PIPE_UNDERGROUND_RANGE,
            undergroundType = PIPE_TO_GROUND,
            tunnelOnlyWhenBlocked = false,
        )

    /**
     * One tile of the layout: the entity that covers it and the planner's flags for the tile. A
     * tile with no [entity] is free; the flags only matter while the layout is being built.
     */
    private class Cell {
        var entity: LayoutEntity? = null

        /** A feed will need this tile; no route may run through it first. */
        var reserved = false

        /** The fluid a machine-edge pipe is reserved for here, so two fluids never touch edges. */
        var edgeFluid: Material? = null

        /**
         * The underground runs tunnelling over this tile, one key per run. Two runs may share the
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

    /** The extent of everything placed, so a route search stays near the blueprint. */
    private var routeMinX = Int.MAX_VALUE
    private var routeMaxX = Int.MIN_VALUE
    private var routeMinY = Int.MAX_VALUE
    private var routeMaxY = Int.MIN_VALUE

    private val markerRows = mutableSetOf<Int>()

    private val producerOf = mutableMapOf<Material, PlannedRecipe>()
    private val depth = mutableMapOf<Recipe, Int>()
    private val rows = mutableMapOf<Recipe, Row>()

    /** Inputs whose belt could not be routed; a non-empty list means the blueprint is broken. */
    val failures = mutableListOf<String>()

    /** All the directions, Clock-wise EAST first, like most of the stuff has to be delivered. */
    private val steps = Direction.entries.map { it.step }

    /** One recipe built as a row of touching machines, with its input lanes and output belt. */
    private class Row(
        val recipe: PlannedRecipe,
        val left: Int,
        val factoryTop: Int,
        val copies: Int,
        val width: Int,
        val height: Int,
        val stride: Int,
        val solids: List<Material>,
        val laneNearRow: Int?,
        val laneFarRow: Int?,
        val laneBelowRow: Int?,
        val outputLaneRow: Int,
    ) {
        val factoryBottom
            get() = factoryTop + height - 1

        val right
            get() = left + (copies - 1) * stride + width - 1
    }

    /**
     * Where a material enters a row's lane: the feed ends at [routeTo], having come from
     * [approach], so its belt ends up facing the lane. [feedRow] is the row its marker sits on.
     */
    private class Sink(
        val routeTo: Coordinate,
        /** The one tile the feed must come from, so its belt ends facing the lane. */
        val approach: Coordinate,
        val feedRow: Int,
    )

    init {
        for (recipe in plan.recipes) {
            for (material in recipe.recipe.result.keys) producerOf.getOrPut(material) { recipe }
        }
    }

    fun build(): List<BlueprintEntity> {
        if (plan.recipes.isNotEmpty()) {
            computeDepth()
            placeRows()
            placeTargetOutput()
            val sinks = allSolidSinks()
            for ((_, _, sink) in sinks) {
                reserve(sink.routeTo)
                reserve(sink.approach)
            }
            feedSolids(sinks)
            feedFluids()
            placeTargetFluidOutput()
            stretchUndergroundRuns()
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

    private fun copiesOf(recipe: PlannedRecipe): Int =
        // a machine count that should be whole but carries floating point noise must not round up
        ceil(recipe.machines - 1e-6).toInt().coerceIn(1, MAX_COPIES)

    private fun footprint(recipe: PlannedRecipe) =
        blueprintMachineFootprint(recipe.factory.identifier)

    /** How many inserters one machine needs to pull [materials] out of its lane. */
    private fun insertersFor(recipe: PlannedRecipe, materials: List<Material>): Int {
        if (materials.isEmpty()) return 0
        val rate = materials.sumOf { perMachineRate(recipe, it) }
        return maxOf(1, ceil(logistics.inserterCount(rate)).toInt())
    }

    /** Items of [material] one machine of [recipe] consumes per second. */
    private fun perMachineRate(recipe: PlannedRecipe, material: Material): Double {
        val time = recipe.recipe.time
        val items = recipe.recipe.ingredients[material] ?: return 0.0
        return if (time <= 0.0) 0.0 else recipe.production.speed * items / time
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

    private fun computeDepth() {
        val visiting = mutableSetOf<Recipe>()
        fun depthOf(recipe: PlannedRecipe): Int {
            depth[recipe.recipe]?.let {
                return it
            }
            if (!visiting.add(recipe.recipe)) return 0
            val inputs =
                recipe.recipe.ingredients.keys.filter {
                    it in producerOf && producerOf.getValue(it).recipe !== recipe.recipe
                }
            val value =
                if (inputs.isEmpty()) 0 else 1 + inputs.maxOf { depthOf(producerOf.getValue(it)) }
            visiting.remove(recipe.recipe)
            depth[recipe.recipe] = value
            return value
        }
        for (recipe in plan.recipes) depthOf(recipe)
    }

    private fun placeRows() {
        var y = 0
        for (recipe in
            plan.recipes.sortedWith(compareBy({ depth[it.recipe] ?: 0 }, { it.recipe.name }))) {
            val row = placeRow(recipe, y)
            rows[recipe.recipe] = row
            y = row.outputLaneRow + 3 + BAND_GAP
        }
    }

    private fun placeRow(recipe: PlannedRecipe, bandTop: Int): Row {
        val size = footprint(recipe)
        val copies = copiesOf(recipe)
        val solids =
            recipe.recipe.ingredients.keys.filter { !logistics.isFluid(it) }.sortedBy { it.name }
        val lanes =
            when {
                solids.isEmpty() -> 0
                solids.size == 1 -> 1
                else -> 2
            }
        val factoryTop = bandTop + lanes + 1
        val factoryBottomIncl = factoryTop + size.height - 1

        val laneNearRow = if (lanes >= 1) factoryTop - 2 else null
        val laneFarRow = if (lanes >= 2) factoryTop - 3 else null
        val outputLaneRow = factoryBottomIncl + 2
        // a third solid input goes on a second belt below the row
        val laneBelowRow = if (solids.size >= 3) factoryBottomIncl + 3 else null
        // a machine that takes a fluid needs a free side for its pipe, so those copies get a gap
        val hasFluid = recipe.recipe.ingredients.keys.any { logistics.isFluid(it) }
        val stride = size.width + if (hasFluid) 1 else 0
        val row =
            Row(
                recipe = recipe,
                left = LANE_MARGIN,
                factoryTop = factoryTop,
                copies = copies,
                width = size.width,
                height = size.height,
                stride = stride,
                solids = solids,
                laneNearRow = laneNearRow,
                laneFarRow = laneFarRow,
                laneBelowRow = laneBelowRow,
                outputLaneRow = outputLaneRow,
            )

        for (copy in 0 until copies) {
            place(
                MachineEntity(
                    Coordinate(row.left + copy * stride, factoryTop).area(size),
                    recipe.factory.identifier,
                    recipe.recipe.name,
                ),
            )
        }

        // a short inserter reaches the near lane one tile up, a long one the far lane two tiles up;
        // a lane whose rate needs more than one inserter gets several, spread over the edge
        val nearCount = if (lanes >= 1) insertersFor(recipe, listOf(solids[0])) else 0
        val farCount = if (lanes >= 2) insertersFor(recipe, listOf(solids[1])) else 0
        val pattern = inserterPattern(nearCount, farCount, size.width)
        for (copy in 0 until copies) {
            val left = row.left + copy * stride
            pattern.forEachIndexed { index, long ->
                // a long inserter reaches the far lane, a short one the near lane
                placeInserter(
                    Coordinate(left + index, factoryTop - 1),
                    NORTH,
                    if (long) solids[1] else solids[0],
                    long,
                )
            }
        }

        // an imported lane flows east (fed from the west); a produced lane flows west (fed from
        // the producer's fan on the east), so produced belts never cross the import lanes
        fun laneDirection(material: Material) = if (material in producerOf) WEST else EAST
        fun placeLane(y: Int, material: Material) {
            val direction = laneDirection(material)
            for (x in row.left..row.right) {
                placeBelt(Coordinate(x, y), material, direction)
            }
        }
        laneNearRow?.let { placeLane(it, solids[0]) }
        laneFarRow?.let { placeLane(it, solids[1]) }
        laneBelowRow?.let { placeLane(it, solids[2]) }

        val product = recipe.recipe.result.keys.firstOrNull { !logistics.isFluid(it) }
        for (copy in 0 until copies) {
            val left = row.left + copy * stride
            // the product is inserted onto the belt below the row
            product?.let {
                placeInserter(
                    Coordinate(left + size.width / 2, row.factoryBottom + 1),
                    NORTH,
                    it,
                    false,
                )
            }
            // a long inserter reaches over the output belt to a third input belt below the row
            if (laneBelowRow != null) {
                val column = if (size.width == 2) left else left + size.width - 1
                placeInserter(
                    Coordinate(column, row.factoryBottom + 1),
                    SOUTH,
                    solids[2],
                    true,
                )
            }
        }
        product?.let {
            for (x in row.left..row.right) {
                placeBelt(Coordinate(x, outputLaneRow), it, EAST)
            }
        }
        return row
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
                Sink(Coordinate(x, (y + 1)), Coordinate(x, (y + 2)), y + 2 + (index - 3))
            }
        }
    }

    /** Where a lane is fed: an imported one from the west, a produced one from the east. */
    private fun laneSink(row: Row, y: Int, material: Material): Sink =
        if (material in producerOf) {
            val x = row.right + 1
            val routeTo = Coordinate(x, y)
            Sink(routeTo, routeTo.east(), y)
        } else {
            val x = row.left - 1
            val routeTo = Coordinate(x, y)
            Sink(routeTo, routeTo.west(), y)
        }

    private fun allSolidSinks() = buildList {
        for (row in rows.values) {
            for (material in row.solids) {
                sinkOf(row, material)?.let { add(Triple(material, row, it)) }
            }
        }
    }

    private fun feedSolids(sinks: List<Triple<Material, Row, Sink>>) {
        val grouped = sinks.groupBy { it.first }
        // place every producer's fan before routing anything, so a route never crosses a fan that
        // is placed later
        val branches = buildMap {
            for ((material, list) in grouped) {
                val producer = producerOf[material] ?: continue
                val producerRow = rows[producer.recipe] ?: continue
                put(material, fanOut(producerRow, material, list.size))
            }
        }
        // an imported material that feeds a lane from the west gets a straight belt laid down now,
        // so its row stays clear and nothing has to route across it
        for ((material, list) in grouped.entries.sortedBy { it.key.name }) {
            if (material in branches) continue
            for ((_, _, sink) in list) {
                if (sink.approach.x == sink.routeTo.x) continue
                val row = sink.feedRow
                if (!straightRowFree(row, sink.routeTo.x)) {
                    failures += "${material.name} to ${sink.routeTo} (row blocked)"
                    continue
                }
                if (cheats) {
                    // an infinity chest feeds the belt through a loader, so no supply is needed
                    placeInfinityChest(Coordinate(2, row), InfinitySettings(item = material))
                    placeLoader(Coordinate(3, row), EAST, OUTPUT, material)
                    for (x in 5..sink.routeTo.x) {
                        placeBelt(Coordinate(x, row), material, EAST)
                    }
                } else {
                    for (x in 2..sink.routeTo.x) {
                        placeBelt(Coordinate(x, row), material, EAST)
                    }
                    val spot = markerSpot(Coordinate(2, row))
                    placeMarker(spot, Signal(material))
                }
            }
        }
        for ((material, list) in grouped.entries.sortedBy { it.key.name }) {
            val fan = branches[material]
            if (fan != null) {
                list.forEachIndexed { index, entry ->
                    val branch = fan.getOrNull(index) ?: return@forEachIndexed
                    deliver(material, branch.south(), SOUTH, entry.third)
                }
            } else {
                for ((_, _, sink) in list) {
                    if (sink.approach.x != sink.routeTo.x) continue
                    val source = solidMarker(material, sink)
                    deliver(material, source, EAST, sink)
                }
            }
        }
    }

    /** True when a straight import belt can be laid in the margin on [row]. */
    private fun straightRowFree(row: Int, upto: Int) = (2..upto).all { isFree(Coordinate(it, row)) }

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

    /** True when a fan of [count] branches can be placed at [start]. */
    private fun fanFree(start: Coordinate, count: Int): Boolean {
        // the body is two tiles tall: a splitter per branch plus the final belt
        val body = start.area(2 * count - 1, 2)
        if (!body.all { isFree(it) && !isReserved(it) }) return false

        // each splitter's branch turns south; its first route tile must stay clear
        var coordinate = start.south(2).east()
        repeat(count - 1) {
            if (!isFree(coordinate) || isReserved(coordinate)) return false
            coordinate = coordinate.east(2)
        }
        return true
    }

    /**
     * Splits the east-flowing output belt of [row] into [count] belts, one per consumer. Returns
     * the belt (facing south) each branch starts from.
     */
    private fun fanOut(row: Row, material: Material, count: Int): List<Coordinate> {
        val y = row.outputLaneRow
        // shift east until every fan tile is clear: a sink goal may sit right on the row's edge
        var startX = row.right + 1
        while (startX < row.right + 60 && !fanFree(Coordinate(startX, y), count)) startX++
        // connect the output lane to the fan when it had to shift east
        for (x in (row.right + 1) until startX) {
            placeBelt(Coordinate(x, y), material, EAST)
        }
        val branches = mutableListOf<Coordinate>()
        var start = Coordinate(startX, y)
        repeat(count - 1) {
            // a splitter is two tiles across the flow; its input comes from the west, its outputs
            // run east, one of which turns south into a branch
            place(SplitterEntity(start.area(1, 2), splitter, material, EAST))
            val branchEast = start.east()
            val branchSouth = branchEast.south()
            placeBelt(branchSouth, material, SOUTH)
            branches += branchSouth
            placeBelt(branchEast, material, EAST)
            start = start.east(2)
        }
        placeBelt(start, material, SOUTH)
        branches += start
        // keep each branch's first route tile free, so no earlier feed blocks it
        for (c in branches) reserve(c.south())
        return branches
    }

    private fun solidMarker(material: Material, sink: Sink): Coordinate {
        // the marker sits at the lane's own row, so its belt runs straight east into the lane
        var row = sink.feedRow
        var attempts = 0
        while (
            (row in markerRows || !isFree(Coordinate(1, row)) || !isFree(Coordinate(2, row))) &&
                attempts++ < 50
        ) row++
        markerRows += row
        placeMarker(Coordinate(1, row), Signal(material))
        return Coordinate(2, row)
    }

    /**
     * Routes one belt from [start] to [sink]'s approach tile and then steps onto the feed tile, so
     * the feed belt always faces the lane whichever way the route arrived.
     */
    private fun deliver(material: Material, start: Coordinate, direction: Direction, sink: Sink) {
        if (!isFree(start)) {
            failures += "${material.name} from $start (start blocked)"
            return
        }
        // the search may only use the sink's own tile when it has to: normally it stops one tile
        // short (at the approach) so the feed belt lands facing the lane, but a boxed-in approach
        // can only be reached by surfacing on the sink tile first
        val segment =
            findRoute(start, direction, sink.approach, sink.routeTo) { coordinate: Coordinate ->
                (routable(coordinate) ||
                    coordinate == sink.routeTo ||
                    coordinate == sink.approach) && !sideLoaded(coordinate, material)
            }

        if (segment == null) {
            failures += "${material.name} to ${sink.routeTo} (no route)"
            return
        }

        val nodes = buildList {
            add(start)
            addAll(segment)
            add(sink.routeTo)
        }

        placePath(material, nodes)
    }

    private fun feedFluids() {
        // reserve every fluid's machine edge first, so two fluids never pick touching edges
        val targets = mutableMapOf<Material, MutableList<Coordinate>>()
        for (row in rows.values) {
            val fluids =
                row.recipe.recipe.ingredients.keys
                    .filter { logistics.isFluid(it) }
                    .sortedBy { it.name }
            for (material in fluids) {
                for (copy in 0 until row.copies) {
                    val tile = fluidEdge(row, copy, material) ?: continue
                    reserve(tile)
                    cell(tile).edgeFluid = material
                    targets.getOrPut(material) { mutableListOf() }.add(tile)
                }
            }
        }
        for ((material, sinks) in targets.entries.sortedBy { it.key.name }) {
            val source = fluidMarker(material, sinks.first())
            var current = source
            for (target in sinks) {
                val segment = fluidRoute(current, EAST, target, material) ?: continue
                placePath(material, listOf(current) + segment)
                current = target
            }
        }
    }

    private fun fluidEdge(row: Row, copy: Int, material: Material): Coordinate? {
        val left = row.left + copy * row.stride
        for (x in listOf(left - 1, left + row.width)) {
            for (y in row.factoryTop until row.factoryTop + row.height) {
                val tile = Coordinate(x, y)
                if (!isFree(tile) || isReserved(tile)) continue
                val clash = steps.any { d ->
                    grid[tile + d]?.edgeFluid.let { it != null && it != material }
                }
                if (!clash) return tile
            }
        }
        return null
    }

    /** Places a fluid marker and returns the free tile its pipe lane starts on. */
    private fun fluidMarker(material: Material, target: Coordinate): Coordinate {
        var row = target.y
        while (
            row in markerRows || !isFree(Coordinate(2, row)) || !isFree(Coordinate(2, row - 1))
        ) {
            row++
        }
        markerRows += row
        placeMarker(Coordinate(2, row - 1), Signal(material, fluid = true))
        return Coordinate(2, row)
    }

    /** Extends the target's output belt and ends it in a marker or a cheat infinity chest. */
    private fun placeTargetOutput() {
        val targetMaterial = plan.target
        val recipe = plan.recipes.firstOrNull { it.material == targetMaterial } ?: return
        val row = rows[recipe.recipe] ?: return
        if (!recipe.recipe.result.keys.any { !logistics.isFluid(it) }) return
        var coordinate = Coordinate(row.right + 1, row.outputLaneRow)

        while (coordinate.x <= row.right + 3 && isFree(coordinate)) {
            placeBelt(coordinate, targetMaterial, EAST)
            coordinate = coordinate.east()
        }
        val loaderFits =
            cheats && isFree(coordinate) && isFree(coordinate.east()) && isFree(coordinate.east(2))
        if (loaderFits) {
            // the product belt runs into a loader that voids it in an infinity chest
            placeLoader(coordinate, EAST, INPUT, targetMaterial)
            placeInfinityChest(
                coordinate.east(2),
                InfinitySettings(removeUnfiltered = true),
            )
        } else {
            val spot = markerSpot(coordinate.west())
            placeMarker(spot, Signal(targetMaterial))
        }
    }

    /** Connects a fluid output pipe to each target machine, so its product has somewhere to go. */
    private fun placeTargetFluidOutput() {
        if (!logistics.isFluid(plan.target)) return
        val recipe = plan.recipes.firstOrNull { it.material == plan.target } ?: return
        val row = rows[recipe.recipe] ?: return
        for (copy in 0 until row.copies) {
            val x = row.left + copy * row.stride + row.width
            for (y in row.factoryTop until row.factoryTop + row.height) {
                val coordinate = Coordinate(x, y)
                if (isFree(coordinate)) {
                    placePipe(coordinate, plan.target)
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
            { tile -> tile == target || pipeAllowed(tile, fluid) },
            pipeProfile,
        )

    /** A pipe tile is free only when it touches no pipe carrying a different fluid. */
    private fun pipeAllowed(coordinate: Coordinate, fluid: Material) =
        isFree(coordinate) &&
            !isReserved(coordinate) &&
            steps.none { d ->
                val cd = coordinate + d
                val placedFluid = fluidAt(cd)
                val reservedFor = grid[cd]?.edgeFluid
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
    private fun placePath(material: Material, nodes: List<Coordinate>) {
        for ((tile, role) in roles(nodes)) {
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
     * revisit never breaks a paired run; otherwise the last occurrence wins.
     */
    private fun roles(nodes: List<Coordinate>): Map<Coordinate, PathRole> {
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

        return roles
    }

    /** Records every underground run of [nodes], so a later route sees the tiles under it. */
    private fun markTunnels(nodes: List<Coordinate>, type: String) {
        nodes.zipWithNext().forEach { (from, to) ->
            if (!from.adjacent(to)) markTunnel(from, to, type)
        }
    }

    /** Records the underground run from [from] to [to] on every tile it passes under. */
    private fun markTunnel(from: Coordinate, to: Coordinate, type: String) {
        val d = to - from
        val step = d.direction
        val key = TunnelKey(axisOf(step), type)
        for (k in 1 until d.chebyshevLen) {
            cell(from + step * k).tunnels += key
        }
    }

    /**
     * Lengthens every underground run along its own straight surface run, up to the tier's reach.
     * It runs after all feeding and before electricity, so it may rewrite belts and pipes; a run
     * only grows by swallowing the plain belts/pipes of its own material that already continue it.
     */
    private fun stretchUndergroundRuns() {
        for (input in placed.filterIsInstance<UndergroundEntity>().filter { it.io == INPUT }) {
            stretchRun(input)
        }
    }

    /** The output end of the underground run [input] feeds, within the material's reach, if any. */
    private fun undergroundOutputOf(input: UndergroundEntity): UndergroundEntity? {
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
    private fun range(material: Material) =
        if (logistics.isFluid(material)) PIPE_UNDERGROUND_RANGE else undergroundRange

    /** The name of an underground end of [material]: a pipe-to-ground or the belt tier's. */
    private fun endName(material: Material) =
        if (logistics.isFluid(material)) PIPE_TO_GROUND else undergroundBelt

    /**
     * Grows one underground run, at most [range] tiles long, by spreading the extra tiles evenly
     * between its two ends; when one end runs out the other takes the remainder, so the run grows
     * toward the end that still has plain material to swallow (fewer free tiles). The end entities
     * are re-placed and the swallowed tiles become part of the run.
     */
    private fun stretchRun(input: UndergroundEntity) {
        if (input !in placed) return
        val output = undergroundOutputOf(input) ?: return

        val inputTile = input.area.topLeft
        val outputTile = output.area.topLeft

        val direction = input.direction
        val material = input.material
        val range = range(material)
        val type = endName(material)

        val step = direction.step
        val backward = step * -1
        val budget = range - inputTile.manhattanDist(outputTile)
        if (budget <= 0) return
        val fluid = logistics.isFluid(material)

        fun swallowable(tile: Coordinate) =
            when (val entity = grid[tile]?.entity) {
                is BeltEntity ->
                    !fluid && entity.material == material && entity.direction == direction
                is PipeEntity -> fluid && entity.material == material
                else -> false
            }

        val maxBack = extension(inputTile, backward, budget, type, ::swallowable)
        val maxForward = extension(outputTile, step, budget, type, ::swallowable)

        val (back, forward) = share(budget, maxBack, maxForward)
        if (back == 0 && forward == 0) return

        for (i in 1..back) removeAt(inputTile + backward * i)
        for (i in 1..forward) removeAt(outputTile + step * i)
        removeAt(inputTile)
        removeAt(outputTile)
        val newInput = inputTile + backward * back
        val newOutput = outputTile + step * forward

        placeEnd(material, newInput, input.direction, INPUT)
        placeEnd(material, newOutput, input.direction, OUTPUT)

        markTunnel(newInput, newOutput, type)
    }

    /**
     * How far the end at [start] can grow in [step], at most [budget] tiles: every swallowed tile
     * must be [swallowable] and leave a belt/pipe beyond the new end, and the tile that becomes
     * part of the run must not already carry a tunnel of the same [type] on the same axis.
     */
    private fun extension(
        start: Coordinate,
        step: Coordinate,
        budget: Int,
        type: String,
        swallowable: (Coordinate) -> Boolean,
    ): Int {
        val key = TunnelKey(axisOf(step), type)
        var count = 0
        while (count < budget) {
            val next = start + step * (count + 1)
            if (!swallowable(next) || isFree(next + step)) break
            if (key in (grid[start + step * count]?.tunnels ?: emptySet())) break
            count++
        }
        return count
    }

    /**
     * Splits [budget] extra tiles as evenly as possible, the remainder to the end with more room.
     */
    private fun share(budget: Int, maxBack: Int, maxForward: Int): Pair<Int, Int> {
        var back = minOf(maxBack, budget / 2)
        val forward = minOf(maxForward, budget - back)
        val left = budget - back - forward
        if (left > 0) back = minOf(maxBack, back + left)
        return back to forward
    }

    /** Removes the entity covering [tile], if any, from [placed] and the grid. */
    private fun removeAt(tile: Coordinate) {
        val entity = grid[tile]?.entity ?: return
        placed.remove(entity)
        entity.area.forEach { if (grid[it]?.entity === entity) grid[it]?.entity = null }
    }

    private fun directionBetween(from: Coordinate, to: Coordinate) =
        when {
            to.x > from.x -> EAST
            to.x < from.x -> WEST
            to.y > from.y -> SOUTH
            else -> NORTH
        }

    /**
     * A weighted A* route for belts: a straight surface run is cheapest, a turn costs a little, and
     * a straight underground run under an obstacle is preferred over routing around it. The route
     * never steps west of [ROUTE_WEST_BOUND], and surface tiles outside the start-to-goal corridor
     * pay [RouteProfile.detourCost].
     */
    private fun findRoute(
        from: Coordinate,
        fromDirection: Direction,
        to: Coordinate,
        avoid: Coordinate? = null,
        allowed: (Coordinate) -> Boolean,
    ) = route(from, fromDirection, to, allowed, beltProfile, avoid)

    /**
     * A weighted A* route. A tile costs [RouteProfile.surfaceCost], a turn adds
     * [RouteProfile.turnCost], and a straight underground run costs [RouteProfile.tunnelEntry] plus
     * [RouteProfile.undergroundCost] per tile, so a started tunnel runs long. A tunnel is entered
     * only straight, and (for belts) only over an obstacle so surface stays the first choice. The
     * last move into the goal is always a surface step, so a feed reaches its sink facing it.
     * Manhattan distance times the cheapest tile is the (admissible) heuristic.
     */
    private fun route(
        from: Coordinate,
        heading: Direction,
        to: Coordinate,
        allowed: (Coordinate) -> Boolean,
        profile: RouteProfile,
        avoid: Coordinate? = null,
    ): List<Coordinate>? {
        if (from == to) return emptyList()
        if (!inRegion(to) || !allowed(to)) return null

        val start = RouteNode(from, heading, forced = false)
        val gScore = mutableMapOf(start to 0)
        val previous = mutableMapOf<RouteNode, RouteNode>()
        val heap = MinHeap<RouteEntry>()
        var seq = 0
        heap.add(RouteEntry(heuristic(from, to, profile), 0, seq++, start))

        fun relax(current: RouteNode, next: RouteNode, cost: Int, g: Int) {
            val tentative = g + cost
            if (tentative >= (gScore[next] ?: Int.MAX_VALUE)) return
            gScore[next] = tentative
            previous[next] = current
            heap.add(
                RouteEntry(tentative + heuristic(next.tile, to, profile), tentative, seq++, next),
            )
        }

        var popped = 0
        while (!heap.isEmpty() && popped++ < 1_000_000) {
            val entry = heap.poll()
            val current = entry.node
            if (entry.g > (gScore[current] ?: Int.MAX_VALUE)) continue
            if (current.tile == to) return reconstructRoute(previous, current, start)

            // after surfacing, a belt must run straight for one tile before it can turn
            val directions = if (current.forced) listOf(current.heading) else Direction.entries
            for (direction in directions) {
                val directionStep = direction.step
                val surface = current.tile + directionStep
                if (inRegion(surface) && allowed(surface)) {
                    val turn = if (current.heading != direction) profile.turnCost else 0
                    val detour = if (inCorridor(surface, from, to)) 0 else profile.detourCost
                    val sink = if (surface == avoid) profile.sinkCost else 0
                    val clear =
                        if (steps.any { isReserved(surface + it) }) profile.keepClearCost else 0
                    relax(
                        current,
                        RouteNode(surface, direction, forced = false),
                        profile.surfaceCost + turn + detour + sink + clear,
                        entry.g,
                    )
                }
                // a tunnel is only entered straight, never as the route's first move, and never
                // right after surfacing
                if (current.forced || current.heading != direction) continue
                // a belt tunnels only to cross an obstacle: if the tile ahead is free, step there
                if (profile.tunnelOnlyWhenBlocked && allowed(current.tile + directionStep)) continue
                for (reach in 2..profile.range) {
                    val target = current.tile + directionStep * reach
                    // a feed must reach its sink on the surface, so its last belt faces the lane
                    if (target == to) continue
                    if (!inRegion(target) || !allowed(target)) continue
                    if (!tunnelFree(current.tile, directionStep, reach, profile.undergroundType)) {
                        continue
                    }
                    val sink = if (target == avoid) profile.sinkCost else 0
                    relax(
                        current,
                        RouteNode(target, direction, forced = true),
                        profile.tunnelEntry + reach * profile.undergroundCost + sink,
                        entry.g,
                    )
                }
            }
        }
        return null
    }

    private fun reconstructRoute(
        previous: Map<RouteNode, RouteNode>,
        goal: RouteNode,
        start: RouteNode,
    ) = buildList {
        var current: RouteNode? = goal
        while (current != null && current != start) {
            add(current.tile)
            current = previous[current]
        }
    }
        .asReversed()

    private fun heuristic(tile: Coordinate, to: Coordinate, profile: RouteProfile) =
        minOf(profile.surfaceCost, profile.undergroundCost) * tile.manhattanDist(to)

    /** Within the start-to-goal corridor, where a surface tile costs no extra detour penalty. */
    private fun inCorridor(tile: Coordinate, from: Coordinate, to: Coordinate) =
        tile.x in minOf(from.x, to.x)..maxOf(from.x, to.x) &&
            tile.y in minOf(from.y, to.y)..maxOf(from.y, to.y)

    /**
     * Whether a route may use [coordinate]: east of [ROUTE_WEST_BOUND], and within [ROUTE_MARGIN]
     * of everything placed, so the search stays near the blueprint instead of roaming forever.
     */
    private fun inRegion(coordinate: Coordinate) =
        coordinate.x >= ROUTE_WEST_BOUND &&
            coordinate.x <= routeMaxX + ROUTE_MARGIN &&
            coordinate.y >= routeMinY - ROUTE_MARGIN &&
            coordinate.y <= routeMaxY + ROUTE_MARGIN

    /**
     * True when a tunnel of [reach] tiles in [direction] clashes with a tunnel already there. Two
     * runs may share a tile on different axes, or with a different underground type.
     */
    private fun tunnelFree(
        from: Coordinate,
        direction: Coordinate,
        reach: Int,
        type: String,
    ): Boolean {
        val key = TunnelKey(axisOf(direction), type)
        for (k in 1 until reach) {
            if (key in (grid[from + direction * k]?.tunnels ?: emptySet())) return false
        }
        return true
    }

    private fun axisOf(direction: Coordinate) =
        if (direction.x != 0) TunnelAxis.HORIZONTAL else TunnelAxis.VERTICAL

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
            .filter { isFree(it) }
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
                    if (isFree(toTest)) return toTest
                }
            }
        }
        return null
    }

    private fun routable(coordinate: Coordinate) = isFree(coordinate) && !isReserved(coordinate)

    /** Records [entity] and marks every tile it covers as occupied. */
    private fun place(entity: LayoutEntity) {
        placed += entity
        entity.area.forEach { cell(it).entity = entity }
        val area = entity.area
        routeMinX = minOf(routeMinX, area.x)
        routeMaxX = maxOf(routeMaxX, area.bottomRight.x)
        routeMinY = minOf(routeMinY, area.y)
        routeMaxY = maxOf(routeMaxY, area.bottomRight.y)
    }

    // The entity kinds that are placed often get their own helper, so the tile size and prototype
    // name live in one place rather than at every call site.

    /** Places a belt (always one tile) carrying [material] and facing [direction]. */
    private fun placeBelt(
        coordinate: Coordinate,
        material: Material,
        direction: Direction,
    ) {
        place(BeltEntity(coordinate.area(), belt, material, direction))
    }

    /** Places a belt or a pipe, whichever [material] rides: a fluid goes through a pipe. */
    private fun placeSurface(material: Material, coordinate: Coordinate, direction: Direction) {
        if (logistics.isFluid(material)) placePipe(coordinate, material)
        else placeBelt(coordinate, material, direction)
    }

    /** Places an underground belt or pipe-to-ground end (always one tile) for [material]. */
    private fun placeEnd(
        material: Material,
        coordinate: Coordinate,
        direction: Direction,
        io: Io,
    ) {
        place(
            if (logistics.isFluid(material))
                PipeToGroundEntity(coordinate.area(), PIPE_TO_GROUND, material, direction, io)
            else UndergroundBeltEntity(coordinate.area(), undergroundBelt, material, direction, io),
        )
    }

    /** Places an inserter (always one tile) of the selected tier, long when [long]. */
    private fun placeInserter(
        coordinate: Coordinate,
        direction: Direction,
        material: Material,
        long: Boolean,
    ) {
        place(
            InserterEntity(
                coordinate.area(),
                if (long) longInserter else inserter,
                material,
                direction,
                long,
            ),
        )
    }

    /** Places a loader (two tiles across the belt it serves) with its input/output [io]. */
    private fun placeLoader(
        coordinate: Coordinate,
        direction: Direction,
        io: Io,
        material: Material,
    ) {
        place(LoaderEntity(coordinate.area(2, 1), loader, material, io, direction))
    }

    /** Places a constant-combinator marker (always one tile) showing [signal]. */
    private fun placeMarker(coordinate: Coordinate, signal: Signal) {
        place(MarkerEntity(coordinate.area(), MARKER, signal))
    }

    /** Places a pipe (always one tile) carrying [fluid]. */
    private fun placePipe(coordinate: Coordinate, fluid: Material) {
        place(PipeEntity(coordinate.area(), PIPE, fluid))
    }

    /** Places an infinity chest (always one tile) with its [settings]. */
    private fun placeInfinityChest(coordinate: Coordinate, settings: InfinitySettings) {
        place(InfinityChestEntity(coordinate.area(), INFINITY_CHEST, settings))
    }

    /** Places a medium electric pole (always one tile). */
    private fun placePole(coordinate: Coordinate) {
        place(PoleEntity(coordinate.area(), POLE))
    }

    /** True when a belt next to [coordinate] carries a different material into that tile. */
    private fun sideLoaded(coordinate: Coordinate, material: Material): Boolean = steps.any { d ->
        val neighbour = coordinate + d
        val belt = entityAt(neighbour) as? BeltLikeEntity
        belt != null &&
            belt.direction == directionBetween(neighbour, coordinate) &&
            belt.material != material
    }

    private fun cell(coordinate: Coordinate) = grid.getOrPut(coordinate) { Cell() }

    private fun entityAt(coordinate: Coordinate) = grid[coordinate]?.entity

    /** The fluid the pipe or pipe-to-ground at [coordinate] carries, if any. */
    private fun fluidAt(coordinate: Coordinate) = (entityAt(coordinate) as? Plumbing)?.material

    private fun isFree(coordinate: Coordinate) = grid[coordinate]?.entity == null

    private fun isReserved(coordinate: Coordinate) = grid[coordinate]?.reserved == true

    private fun reserve(coordinate: Coordinate) {
        cell(coordinate).reserved = true
    }
}
