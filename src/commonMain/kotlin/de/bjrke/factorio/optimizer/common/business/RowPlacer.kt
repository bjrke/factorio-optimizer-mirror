package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*
import kotlin.math.abs
import kotlin.math.ceil

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe bands, so their belts can be routed between them. */
private const val BAND_GAP = 14

/** One machine of a row: the allocation it builds and the column it starts on. */
internal class PlacedMachine(val allocation: Int, val left: Int)

/**
 * One row of machines, with its input lanes and output belt. A plain recipe is a row of copies of
 * it; a direct-feed band is a row of differently-built machines whose [interfaces] insert the
 * intermediate straight from one machine into the next.
 */
internal class Row(
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
 * Places the machine rows once: a plain allocation as a row of copies, a direct-feed band as one
 * row of interleaved machines. Owns the row index and the placement order the later delivery and
 * fluid stages read.
 */
internal class RowPlacer(
    private val board: LayoutBoard,
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val planner: RowPlanner,
) {

    /** The row that builds each allocation; a band row serves all of its allocations. */
    val rows = mutableMapOf<Int, Row>()

    /** Every row once, in placement order; a band row serves all of its allocations. */
    val placedRows = mutableListOf<Row>()

    fun placeAll() {
        var y = 0
        for (unit in planner.units) {
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
        val size = planner.footprint(recipe)
        val copies = planner.copiesOf(recipe)
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
            board.place(
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
        val size = planner.footprint(allocations.first())
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
            board.place(
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
                planner.inserterPattern(
                    near?.let { planner.insertersFor(allocation, listOf(it)) } ?: 0,
                    far?.let { planner.insertersFor(allocation, listOf(it)) } ?: 0,
                    row.width,
                )
            // the machine's last column is left free for a pole; a port there stays free for the
            // pipe
            val insertColumns =
                (0 until maxOf(1, row.width - 1)).filterNot(topPortColumns::contains)
            insertColumns.zip(pattern).forEach { (column, long) ->
                // a long inserter reaches the far lane, a short one the near lane
                board.placeInserter(
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
                board.placeInserter(
                    Coordinate(machine.left + productColumn, row.factoryBottom + 1),
                    NORTH,
                    it,
                    false,
                )
            }
            // a long inserter reaches over the output belt to a third input belt below the row
            if (row.laneBelowRow != null && row.solids.getOrNull(2) in recipe.ingredients) {
                board.placeInserter(
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
                board.placeBelt(Coordinate(x, y), material, direction)
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
                board.placeBelt(Coordinate(x, row.outputLaneRow), it, EAST)
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
                board.placeInserter(
                    Coordinate(x, y),
                    if (link.producerIsLeft) WEST else EAST,
                    link.material,
                    false,
                )
            }
        }
    }

    /**
     * Where a lane is fed: imports from the west. Produced materials split by their producer's
     * outlet, the left half feeding from the west and the right half from the east, so two trunks
     * into one row do not have to cross. A lone produced input picks the nearer end.
     */
    fun laneDirection(row: Row, material: Material): Direction {
        if (material !in planner.producerOf) return EAST
        val produced = row.solids.filter { it in planner.producerOf }.sortedBy { outletX(it) }
        val index = produced.indexOf(material)
        val middle = produced.size / 2
        if (index in 0 until middle) return EAST
        if (index >= (produced.size + 1) / 2) return WEST
        return if (outletX(material) <= (row.left + row.right) / 2) EAST else WEST
    }

    /** The tile east of a material's producer output lane, or Far East when it is not placed. */
    fun outletX(material: Material): Int {
        val producer = planner.producerOf[material] ?: return Int.MAX_VALUE
        val producerRow = rows[producer] ?: return Int.MAX_VALUE
        return producerRow.right + 1
    }
}
