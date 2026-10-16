package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.util.intersects
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt

private const val NORTH = 0
private const val EAST = 4
private const val SOUTH = 8
private const val WEST = 12

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe bands, so their belts can be routed between them. */
private const val BAND_GAP = 14

/** Upper bound on the copies of a recipe, so a huge rate cannot explode the blueprint. */
private const val MAX_COPIES = 9

/** Medium poles supply a 7x7 area (radius 3) and reach 9 tiles of wire. */
private const val SUPPLY_RADIUS = 3

private const val WIRE_REACH = 9

private const val MARKER = "constant-combinator"
private const val INFINITY_CHEST = "infinity-chest"
private const val PIPE = "pipe"
private const val PIPE_TO_GROUND = "pipe-to-ground"
private const val POLE = "medium-electric-pole"

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

/** The material each belt tile carries. Used by tests to check no materials merge. */
internal fun blueprintBeltMaterials(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    cheats: Boolean = false,
) =
    RowLayout(plan, logistics, cheats).run {
        build()
        beltMaterial.toMap()
    }

private class RowLayout(
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val cheats: Boolean,
) {
    private val belt = logistics.belt.identifier
    private val inserter = logistics.inserter.identifier
    private val longInserter = Inserter.LONG_HANDED_INSERTER.identifier
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

    private val entities = mutableListOf<BlueprintEntity>()
    private val occupied = mutableMapOf<Coordinate, BlueprintEntity>()

    /** Tiles a feed will need (lane feed tiles); no route may run through them first. */
    private val reserved = mutableSetOf<Coordinate>()

    /** The fluid each placed pipe tile carries, so two different fluids never touch. */
    private val fluidAt = mutableMapOf<Coordinate, Material>()

    /**
     * The direction of each placed belt, so a new belt never lands where one would side-load it.
     */
    private val beltAt = mutableMapOf<Coordinate, Int>()

    /** The material each belt carries, so only belts of the same material may merge. */
    val beltMaterial = mutableMapOf<Coordinate, Material>()

    /**
     * The fluid each machine-edge pipe is reserved for, so two fluids never pick touching edges.
     */
    private val fluidEdgeOwner = mutableMapOf<Coordinate, Material>()

    /** Tiles already tunneled over; two underground belts must never share a tile. */
    private val undergroundUsed = mutableSetOf<Coordinate>()

    private val markerRows = mutableSetOf<Int>()

    private val producerOf = mutableMapOf<Material, PlannedRecipe>()
    private val depth = mutableMapOf<Recipe, Int>()
    private val rows = mutableMapOf<Recipe, Row>()

    /** Inputs whose belt could not be routed; a non-empty list means the blueprint is broken. */
    val failures = mutableListOf<String>()

    /** Clockwise-first, so a shared feed goes around the right and the bottom. */
    private val steps =
        listOf(
            Coordinate(1, 0),
            Coordinate(0, 1),
            Coordinate(-1, 0),
            Coordinate(0, -1),
        )

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
        if (plan.recipes.isEmpty()) return entities
        computeDepth()
        placeRows()
        placeTargetOutput()
        val sinks = allSolidSinks()
        for ((_, _, sink) in sinks) {
            reserved += sink.routeTo
            reserved += sink.approach
        }
        feedSolids(sinks)
        feedFluids()
        placeTargetFluidOutput()
        placePoles()
        wirePoles()
        return entities
    }

    /**
     * Wires every pair of poles within reach. A blueprint does not rebuild pole wires on its own,
     * so they have to be part of it.
     */
    private fun wirePoles() {
        val poles = entities.withIndex().filter { it.value.name == POLE }
        for (i in poles.indices) {
            for (j in i + 1 until poles.size) {
                val a = poles[i]
                val b = poles[j]
                val distance = a.value.area.topLeft.distance(b.value.area.topLeft)
                if (distance <= WIRE_REACH + 1e-9) {
                    entities[a.index] =
                        entities[a.index].copy(
                            neighbours = entities[a.index].neighbours + (b.index + 1)
                        )
                    entities[b.index] =
                        entities[b.index].copy(
                            neighbours = entities[b.index].neighbours + (a.index + 1)
                        )
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
            add(
                BlueprintEntity(
                    recipe.factory.identifier,
                    Coordinate(row.left + copy * stride, factoryTop).area(size),
                    recipe = recipe.recipe.name,
                )
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
                addInserter(long, Coordinate(left + index, factoryTop - 1), NORTH)
            }
        }

        // an imported lane flows east (fed from the west); a produced lane flows west (fed from
        // the producer's fan on the east), so produced belts never cross the import lanes
        fun laneDirection(material: Material) = if (material in producerOf) WEST else EAST
        fun placeLane(y: Int, material: Material) {
            val direction = laneDirection(material)
            for (x in row.left..row.right) {
                add(BlueprintEntity(belt, Coordinate(x, y), direction))
                beltMaterial[Coordinate(x, y)] = material
            }
        }
        laneNearRow?.let { placeLane(it, solids[0]) }
        laneFarRow?.let { placeLane(it, solids[1]) }
        laneBelowRow?.let { placeLane(it, solids[2]) }

        val producesSolid = recipe.recipe.result.keys.any { !logistics.isFluid(it) }
        for (copy in 0 until copies) {
            val left = row.left + copy * stride
            // the product is inserted onto the belt below the row
            if (producesSolid) {
                addInserter(
                    false,
                    Coordinate(
                        left + size.width / 2,
                        row.factoryBottom + 1,
                    ),
                    NORTH,
                )
            }
            // a long inserter reaches over the output belt to a third input belt below the row
            if (laneBelowRow != null) {
                val column = if (size.width == 2) left else left + size.width - 1
                add(BlueprintEntity(longInserter, Coordinate(column, row.factoryBottom + 1), SOUTH))
            }
        }
        if (producesSolid) {
            val product = recipe.recipe.result.keys.first()
            for (x in row.left..row.right) {
                add(BlueprintEntity(belt, Coordinate(x, outputLaneRow), EAST))
                beltMaterial[Coordinate(x, outputLaneRow)] = product
            }
        }
        return row
    }

    private fun addInserter(long: Boolean, coordinate: Coordinate, direction: Int) {
        add(
            BlueprintEntity(
                if (long) longInserter else inserter,
                coordinate,
                direction,
            )
        )
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
            Sink(Coordinate(x, y), Coordinate((x + 1), y), y)
        } else {
            val x = row.left - 1
            Sink(Coordinate(x, y), Coordinate((x - 1), y), y)
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
                put(material, fanOut(producerRow, list.size))
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
                    add(
                        BlueprintEntity(
                            INFINITY_CHEST,
                            Coordinate(2, row),
                            infinity = InfinitySettings(item = material),
                        )
                    )
                    add(
                        BlueprintEntity(
                            loader,
                            Coordinate(3, row).area(2, 1),
                            EAST,
                            type = "output",
                        )
                    )
                    for (x in 5..sink.routeTo.x) {
                        add(BlueprintEntity(belt, Coordinate(x, row), EAST))
                    }
                } else {
                    for (x in 2..sink.routeTo.x) {
                        add(BlueprintEntity(belt, Coordinate(x, row), EAST))
                    }
                    val spot = markerSpot(row, 2)
                    add(
                        BlueprintEntity(
                            MARKER,
                            spot,
                            marker = material,
                        )
                    )
                }
            }
        }
        for ((material, list) in grouped.entries.sortedBy { it.key.name }) {
            val fan = branches[material]
            if (fan != null) {
                list.forEachIndexed { index, entry ->
                    val branch = fan.getOrNull(index) ?: return@forEachIndexed
                    deliver(material, Coordinate(branch.x, branch.y + 1), entry.third)
                }
            } else {
                for ((_, _, sink) in list) {
                    if (sink.approach.x != sink.routeTo.x) continue
                    val source = solidMarker(material, sink)
                    deliver(material, source, sink)
                }
            }
        }
    }

    /** True when a straight import belt can be laid in the margin on [row]. */
    private fun straightRowFree(row: Int, upto: Int) = (2..upto).all { isFree(Coordinate(it, row)) }

    /** A free tile beside the belt on [beltRow], so a marker never blocks the belt. */
    private fun markerSpot(beltRow: Int, anchorX: Int): Coordinate {
        for (dx in 0..12) {
            for (dy in listOf(-1, 1)) {
                val tile = Coordinate(anchorX + dx, beltRow + dy)
                if (isFree(tile)) return tile
            }
        }
        return Coordinate((anchorX - 1), beltRow)
    }

    /** True when a fan of [count] branches can be placed at [startX] on row [y]. */
    private fun fanFree(startX: Int, y: Int, count: Int): Boolean {
        var x = startX
        val tiles = mutableListOf<Coordinate>()
        repeat(count - 1) {
            tiles += Coordinate(x, y)
            tiles += Coordinate(x, (y + 1))
            tiles += Coordinate((x + 1), (y + 1))
            tiles += Coordinate((x + 1), y)
            // the branch's first route tile
            tiles += Coordinate((x + 1), (y + 2))
            x += 2
        }
        tiles += Coordinate(x, y)
        tiles += Coordinate(x, (y + 1))
        return tiles.all { isFree(it) && it !in reserved }
    }

    /**
     * Splits the east-flowing output belt of [row] into [count] belts, one per consumer. Returns
     * the belt (facing south) each branch starts from.
     */
    private fun fanOut(row: Row, count: Int): List<Coordinate> {
        val y = row.outputLaneRow
        // shift east until every fan tile is clear: a sink goal may sit right on the row's edge
        var startX = row.right + 1
        while (startX < row.right + 60 && !fanFree(startX, y, count)) startX++
        // connect the output lane to the fan when it had to shift east
        for (x in (row.right + 1) until startX) add(BlueprintEntity(belt, Coordinate(x, y), EAST))
        val branches = mutableListOf<Coordinate>()
        var x = startX
        repeat(count - 1) {
            // a splitter is two tiles across the flow; its input comes from the west, its outputs
            // run east, one of which turns south into a branch
            add(BlueprintEntity(splitter, Coordinate(x, y).area(1, 2), EAST))
            val branch = Coordinate((x + 1), (y + 1))
            add(BlueprintEntity(belt, branch, SOUTH))
            branches += branch
            add(BlueprintEntity(belt, Coordinate(x + 1, y), EAST))
            x += 2
        }
        val last = Coordinate(x, y)
        add(BlueprintEntity(belt, last, SOUTH))
        branches += last
        // keep each branch's first route tile free, so no earlier feed blocks it
        for ((x, y) in branches) reserved += Coordinate(x, (y + 1))
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
        add(BlueprintEntity(MARKER, Coordinate(1, row), marker = material))
        return Coordinate(2, row)
    }

    /**
     * Routes one belt from [start] to [sink]'s approach tile and then steps onto the feed tile, so
     * the feed belt always faces the lane whichever way the route arrived.
     */
    private fun deliver(material: Material, start: Coordinate, sink: Sink) {
        if (!isFree(start)) {
            failures += "${material.name} from $start (start blocked)"
            return
        }
        val segment =
            findRoute(start, sink.approach) { coordinate: Coordinate ->
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

        placeBeltPath(material, nodes)
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
                    reserved += tile
                    fluidEdgeOwner[tile] = material
                    targets.getOrPut(material) { mutableListOf() }.add(tile)
                }
            }
        }
        for ((material, sinks) in targets.entries.sortedBy { it.key.name }) {
            val source = fluidMarker(material, sinks.first())
            var current = source
            for (target in sinks) {
                val segment = fluidRoute(current, target, material) ?: continue
                for (tile in segment) {
                    if (isFree(tile)) addPipe(tile, material)
                }
                current = target
            }
        }
    }

    private fun fluidEdge(row: Row, copy: Int, material: Material): Coordinate? {
        val left = row.left + copy * row.stride
        for (x in listOf(left - 1, left + row.width)) {
            for (y in row.factoryTop until row.factoryTop + row.height) {
                val tile = Coordinate(x, y)
                if (!isFree(tile) || tile in reserved) continue
                val clash = steps.any { d ->
                    fluidEdgeOwner[tile + d].let { it != null && it != material }
                }
                if (!clash) return tile
            }
        }
        return null
    }

    private fun fluidMarker(material: Material, target: Coordinate): Coordinate {
        var row = target.y
        val coordinate = Coordinate(2, row)
        while (row in markerRows || !isFree(Coordinate(2, row - 1)) || !isFree(coordinate)) row++
        markerRows += row
        add(BlueprintEntity(MARKER, Coordinate(2, row - 1), marker = material, markerFluid = true))
        add(BlueprintEntity(PIPE_TO_GROUND, Coordinate(2, row), EAST))
        fluidAt[coordinate] = material
        return coordinate
    }

    /** Extends the target's output belt and ends it in a marker or a cheat infinity chest. */
    private fun placeTargetOutput() {
        val recipe = plan.recipes.firstOrNull { it.material == plan.target } ?: return
        val row = rows[recipe.recipe] ?: return
        if (!recipe.recipe.result.keys.any { !logistics.isFluid(it) }) return
        var coordinate = Coordinate(row.right + 1, row.outputLaneRow)

        while (coordinate.x <= row.right + 3 && isFree(coordinate)) {
            add(BlueprintEntity(belt, coordinate, EAST))
            coordinate = Coordinate(coordinate.x + 1, row.outputLaneRow)
        }
        val loaderFits =
            cheats &&
                isFree(coordinate) &&
                isFree(Coordinate(coordinate.x + 1, row.outputLaneRow)) &&
                isFree(Coordinate(coordinate.x + 2, row.outputLaneRow))
        if (loaderFits) {
            // the product belt runs into a loader that voids it in an infinity chest
            add(
                BlueprintEntity(
                    loader,
                    coordinate.area(2, 1),
                    EAST,
                    type = "input",
                )
            )
            add(
                BlueprintEntity(
                    INFINITY_CHEST,
                    Coordinate(
                        coordinate.x + 2,
                        row.outputLaneRow,
                    ),
                    infinity = InfinitySettings(removeUnfiltered = true),
                )
            )
        } else {
            val spot = markerSpot(row.outputLaneRow, coordinate.x - 1)
            add(
                BlueprintEntity(
                    MARKER,
                    spot,
                    marker = plan.target,
                )
            )
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
                    addPipe(coordinate, plan.target)
                    break
                }
            }
        }
    }

    private fun fluidRoute(from: Coordinate, target: Coordinate, fluid: Material) =
        route(from, target) { tile ->
            tile == target || pipeAllowed(tile, fluid)
        }

    private fun addPipe(coordinate: Coordinate, fluid: Material) {
        add(BlueprintEntity(PIPE, coordinate))
        fluidAt[coordinate] = fluid
    }

    /** A pipe tile is free only when it touches no pipe carrying a different fluid. */
    private fun pipeAllowed(coordinate: Coordinate, fluid: Material) =
        !(!isFree(coordinate) || coordinate in reserved) &&
            steps.none { d ->
                val cd = coordinate + d
                val placed = fluidAt[cd]
                val reservedFor = fluidEdgeOwner[cd]
                (placed != null && placed != fluid) || (reservedFor != null && reservedFor != fluid)
            }

    /** Draws the belts of [material] along [nodes]; a jump becomes an underground belt. */
    private fun placeBeltPath(material: Material, nodes: List<Coordinate>) {
        if (nodes.size < 2) return
        val emitted = mutableSetOf<Coordinate>()
        for (index in 0 until nodes.size - 1) {
            val from = nodes[index]
            val to = nodes[index + 1]
            val direction = directionBetween(from, to)
            val adjacent = abs(from.x - to.x) + abs(from.y - to.y) == 1
            if (from !in emitted && isFree(from)) {
                if (adjacent) {
                    add(BlueprintEntity(belt, from, direction))
                } else {
                    add(
                        BlueprintEntity(
                            undergroundBelt,
                            from,
                            direction,
                            type = "input",
                        )
                    )
                }
                emitted += from
            }
            if (!adjacent && isFree(to)) {
                val (dx, dy) = to - from
                val step = Coordinate(dx.compareTo(0), dy.compareTo(0))
                val reach = maxOf(abs(dx), abs(dy))
                for (k in 1 until reach) {
                    undergroundUsed += from + step * k
                }
                add(
                    BlueprintEntity(
                        undergroundBelt,
                        to,
                        direction,
                        type = "output",
                    )
                )
                emitted += to
            }
        }
        val last = nodes.last()
        if (last !in emitted && isFree(last)) {
            val before = nodes[nodes.size - 2]
            add(
                BlueprintEntity(
                    belt,
                    last,
                    directionBetween(before, last),
                )
            )
        }
        // remember which material each belt on this path carries
        for (node in nodes) {
            if (node in beltAt) beltMaterial[node] = material
        }
    }

    private fun directionBetween(from: Coordinate, to: Coordinate) =
        when {
            to.x > from.x -> EAST
            to.x < from.x -> WEST
            to.y > from.y -> SOUTH
            else -> NORTH
        }

    /** Surface routes first, an underground one otherwise, so belts can cross anything. */
    /**
     * A straight route that may tunnel under belts is preferred over a surface detour, so
     * intermediate belts are crossed with an underground belt instead of being routed around.
     */
    private fun findRoute(
        from: Coordinate,
        to: Coordinate,
        allowed: (Coordinate) -> Boolean,
    ) = routeWithTunnels(from, to, allowed) ?: route(from, to, allowed)

    private fun route(
        from: Coordinate,
        to: Coordinate,
        allowed: (Coordinate) -> Boolean,
    ): List<Coordinate>? {
        if (!allowed(to)) return null
        val previous = mutableMapOf<Coordinate, Coordinate>()
        val seen = mutableSetOf<Coordinate>()
        seen += from
        val queue = ArrayDeque<Coordinate>()
        queue += from
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 400_000) {
            val current = queue.removeFirst()
            for (d in steps) {
                val n = current + d
                if (n == to) {
                    previous[n] = current
                    return reconstruct(previous, to, from)
                }
                if (!allowed(n) || !seen.add(n)) continue
                previous[n] = current
                queue += n
            }
        }
        return null
    }

    /** Like [route] but a straight run of up to [undergroundRange] tiles may go underground. */
    private fun routeWithTunnels(
        from: Coordinate,
        to: Coordinate,
        allowed: (Coordinate) -> Boolean,
    ): List<Coordinate>? {
        if (!allowed(to)) return null
        val previous = mutableMapOf<Coordinate, Coordinate>()
        val seen = mutableSetOf<Coordinate>()
        seen += from
        /** The tunnel direction a tile was surfaced by, when it must go straight next. */
        val tunnelDir = mutableMapOf<Coordinate, Coordinate>()
        /** The direction of the move that reached a tile, so a tunnel is only entered straight. */
        val inDir = mutableMapOf<Coordinate, Coordinate>()
        val queue = ArrayDeque<Coordinate>()
        queue += from
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 400_000) {
            val current = queue.removeFirst()

            // after surfacing, a belt must run straight for one tile before it can turn
            val forced = tunnelDir[current]
            val incoming = inDir[current]
            val directions = if (forced != null) listOf(forced) else steps
            for (direction in directions) {
                val maxReach = if (forced != null) 1 else undergroundRange
                for (reach in 1..maxReach) {
                    val tunnel = reach > 1

                    if (tunnel) {
                        // a tunnel is only entered straight, so a belt feeds its back tile; the
                        // route always starts on the surface, so its first tile is a belt too
                        if (current in tunnelDir) continue
                        if (incoming != direction) continue
                        // and only to cross an obstacle: if the next tile is free, just step there
                        if (allowed(current + direction)) continue
                        if (!tunnelFree(current.x, current.y, direction.x, direction.y, reach))
                            continue
                    }
                    val n = current + direction * reach
                    val isGoal = n == to
                    // a feed must reach its sink on the surface, so its last belt faces the lane
                    if (isGoal && tunnel) continue
                    if (isGoal) {
                        previous[n] = current
                        return reconstruct(previous, to, from)
                    }
                    if (!allowed(n) || !seen.add(n)) continue
                    previous[n] = current
                    inDir[n] = direction
                    if (tunnel) tunnelDir[n] = direction
                    queue += n
                }
            }
        }
        return null
    }

    /** True when a tunnel of [reach] tiles in direction ([dx],[dy]) crosses no other tunnel. */
    private fun tunnelFree(x: Int, y: Int, dx: Int, dy: Int, reach: Int): Boolean {
        for (k in 1 until reach) {
            if (Coordinate(x + dx * k, y + dy * k) in undergroundUsed) return false
        }
        return true
    }

    private fun reconstruct(
        previous: Map<Coordinate, Coordinate>,
        goal: Coordinate,
        start: Coordinate,
    ) =
        buildList {
                var current: Coordinate? = goal
                while (current != null && current != start) {
                    add(current)
                    current = previous[current]
                }
            }
            .reversed()

    /** Covers every machine, inserter and loader with a pole and wires the poles together. */
    private fun placePoles() {
        val machineTiles =
            entities.filter { it.isPowered() }.sortedWith(compareBy({ it.area.y }, { it.area.x }))
        if (machineTiles.isEmpty()) return

        val poles = mutableSetOf<Coordinate>()
        val covered = mutableSetOf<Coordinate>()
        for (tile in machineTiles) {
            if (tile.area.intersects(covered)) continue
            val spot = bestPoleSpot(tile.area, covered, poles) ?: continue
            poles += spot
            covered.addAll(spot.area().grow(SUPPLY_RADIUS))
        }
        connectPoles(poles)
        for (poleTile in poles) add(BlueprintEntity(POLE, poleTile))
    }

    /** The free tile near [tile] that powers the most not-yet-[covered] machines, if any. */
    private fun bestPoleSpot(
        tile: Area,
        covered: Set<Coordinate>,
        poles: Set<Coordinate>,
    ): Coordinate? {
        var best: Coordinate? = null
        var bestCount = -1

        for (candidate in tile.grow(SUPPLY_RADIUS)) {
            if (!isFree(candidate) || candidate in poles) continue
            var count = 0
            for (other in candidate.area().grow(SUPPLY_RADIUS)) {
                if (other !in covered) {
                    count++

                    val otherMachine = occupied[other]
                    if (otherMachine?.isPowered() == true) {
                        count++
                    }
                }
            }
            if (count > bestCount) {
                bestCount = count
                best = candidate
            }
        }

        return best
    }

    /** Adds poles until every pole can reach another, so the whole set is one wire network. */
    private fun connectPoles(poles: MutableSet<Coordinate>) {
        var guard = 0
        while (guard++ < 400) {
            val components = poleComponents(poles)
            if (components.size <= 1) return
            var bestDistance = Double.MAX_VALUE
            var from: Coordinate? = null
            var to: Coordinate? = null
            for (i in components.indices) {
                for (j in i + 1 until components.size) {
                    for (a in components[i]) {
                        for (b in components[j]) {
                            val distance = a.distance(b)
                            if (distance < bestDistance) {
                                bestDistance = distance
                                from = a
                                to = b
                            }
                        }
                    }
                }
            }
            val start = from ?: return
            val end = to ?: return
            val steps = ceil(bestDistance / (WIRE_REACH - 1)).toInt().coerceAtLeast(1)
            var added = false
            for (step in 1 until steps) {
                val fraction = step.toDouble() / steps
                val x = round(start.x + (end.x - start.x) * fraction).toInt()
                val y = round(start.y + (end.y - start.y) * fraction).toInt()
                val spot = freeNear(Coordinate(x, y), poles.toSet()) ?: continue
                poles += spot
                added = true
            }
            if (!added) return
        }
    }

    /** The wire-connected groups of [poles], two poles being connected within the wire reach. */
    private fun poleComponents(poles: Set<Coordinate>): List<List<Coordinate>> {
        val seen = mutableSetOf<Coordinate>()
        val components = mutableListOf<List<Coordinate>>()
        for (pole in poles) {
            if (!seen.add(pole)) continue
            val component = mutableListOf<Coordinate>()
            val queue = ArrayDeque(listOf(pole))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component += current
                for (other in poles) {
                    if (other in seen) continue
                    val reach = current.distance(other)
                    if (reach <= WIRE_REACH) {
                        seen += other
                        queue += other
                    }
                }
            }
            components += component
        }
        return components
    }

    /** The nearest free tile within one tile of ([c]), if any. */
    private fun freeNear(c: Coordinate, used: Set<Coordinate>): Coordinate? {
        for (radius in 0..1) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val toTest = Coordinate(c.x + dx, c.y + dy)
                    if (isFree(toTest) && toTest !in used) return toTest
                }
            }
        }
        return null
    }

    private fun routable(coordinate: Coordinate) = isFree(coordinate) && coordinate !in reserved

    private fun add(entity: BlueprintEntity) {
        entities += entity
        val topLeft = entity.area.topLeft
        if (entity.name.contains("-belt") && entity.direction != null) {
            beltAt[topLeft] = entity.direction
        }
        entity.area.forEach { occupy(it, entity) }
    }

    /** True when a belt next to [coordinate] carries a different material into that tile. */
    private fun sideLoaded(coordinate: Coordinate, material: Material): Boolean = steps.any { d ->
        val neighbour = coordinate + d
        beltAt[neighbour] == directionBetween(neighbour, coordinate) &&
            beltMaterial[neighbour] != material
    }

    private fun isFree(coordinate: Coordinate) = coordinate !in occupied

    private fun occupy(coordinate: Coordinate, blueprint: BlueprintEntity) {
        occupied[coordinate] = blueprint
    }
}
