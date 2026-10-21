package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe bands, so their belts can be routed between them. */
private const val BAND_GAP = 14

/** One machine of a row: the allocation it builds and the column it starts on. */
internal class PlacedMachine(val allocation: Int, val left: Int)

/**
 * The rectangle a row occupies: its corner, one machine footprint and the stride between copies.
 */
internal data class RowBox(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val stride: Int,
)

/** The input and output belt rows of a row and the solid materials those lanes carry. */
internal data class RowLanes(
    val solids: List<Material>,
    val nearRow: Int?,
    val farRow: Int?,
    val belowRow: Int?,
    val outputRow: Int,
)

/**
 * One row of machines, with its input lanes and output belt. A plain recipe is a row of copies of
 * it; a direct-feed band is a row of differently-built machines whose [interfaces] insert the
 * intermediate straight from one machine into the next.
 */
internal class Row(
    /** The machine each allocation builds, left to right; one entry per machine. */
    val machines: List<PlacedMachine>,
    val box: RowBox,
    val lanes: RowLanes,
    /** The direct insertions between the machines, by the gap they sit in. */
    val interfaces: List<PlannedLink> = emptyList(),
) {
    val copies
        get() = machines.size

    val left
        get() = box.left

    val factoryTop
        get() = box.top

    val width
        get() = box.width

    val height
        get() = box.height

    val stride
        get() = box.stride

    val solids
        get() = lanes.solids

    val laneNearRow
        get() = lanes.nearRow

    val laneFarRow
        get() = lanes.farRow

    val laneBelowRow
        get() = lanes.belowRow

    val outputLaneRow
        get() = lanes.outputRow

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

    private val machineInserters = MachineInserterPlacer(board, plan, logistics, planner)
    private val bandInterfaces = BandInterfacePlacer(board, plan, logistics)

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
                box = RowBox(LANE_MARGIN, shape.factoryTop, size.width, size.height, stride),
                lanes =
                    RowLanes(
                        solids,
                        shape.laneNearRow,
                        shape.laneFarRow,
                        shape.laneBelowRow,
                        shape.outputLaneRow,
                    ),
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
        machineInserters.place(row, emptySet())
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
                box = RowBox(LANE_MARGIN, shape.factoryTop, size.width, size.height, stride),
                lanes =
                    RowLanes(
                        solids,
                        shape.laneNearRow,
                        shape.laneFarRow,
                        shape.laneBelowRow,
                        shape.outputLaneRow,
                    ),
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
        machineInserters.place(row, internal)
        placeLanes(row)
        bandInterfaces.place(row)
        placeProduct(row, internal)
        return row
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

    /** Ends the row with an output belt when one of its machines makes something for outside. */
    private fun placeProduct(row: Row, internal: Set<Material>) {
        val product =
            row.machines.firstNotNullOfOrNull {
                externalProduct(plan.recipes[it.allocation], internal, logistics)
            }
        product?.let {
            for (x in row.left..row.right) {
                board.placeBelt(Coordinate(x, row.outputLaneRow), it, EAST)
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
