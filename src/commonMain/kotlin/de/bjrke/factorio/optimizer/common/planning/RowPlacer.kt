package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.placeBelt
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRow

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe bands, so their belts can be routed between them. */
private const val BAND_GAP = 14

/**
 * Places the machine rows once: a plain planned recipe as a row of copies, a direct-feed band as
 * one row of interleaved machines. Owns the placement order the later delivery and fluid stages
 * read.
 */
internal class RowPlacer(
    private val board: LayoutBoard,
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val planner: RowPlanner,
) {

    /** Every row once, in placement order; a band row serves all of its planned recipes. */
    val placedRows = mutableListOf<Row>()

    private val machineInserters = MachineInserterPlacer(board, logistics, planner)
    private val bandInterfaces = BandInterfacePlacer(board, logistics)

    fun placeAll() {
        var y = 0
        for (unit in planner.units) {
            val row =
                if (unit.row.links.isEmpty()) placeRow(unit.row, y) else placeBand(unit.row, y)
            placedRows += row
            y = row.outputLaneRow + 3 + BAND_GAP
        }
    }

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

    /** Builds a plain row: repeated machines of its one planned recipe, touching each other. */
    private fun placeRow(plannedRow: PlannedRow, bandTop: Int): Row {
        val plannedRecipe = plannedRow.plannedRecipes.single()
        val size = planner.footprint(plannedRecipe)
        val solids =
            plannedRecipe.recipe.ingredients.keys
                .filter { !logistics.isFluid(it) }
                .sortedBy { it.name }
        val shape = rowShape(solids, size.height, bandTop)
        // a fluid row needs no side gap: its lane runs on a row of its own beyond the belts and
        // reaches every port through the squash of an underground shaft, so the copies touch
        val stride = size.width
        val machines =
            plannedRow.machines.mapIndexed { index, planned ->
                PlacedMachine(planned, LANE_MARGIN + index * stride)
            }
        val row =
            Row(
                plannedRow = plannedRow,
                machines = machines,
                box = box(shape, size, stride),
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
                    plannedRecipe.factory.identifier,
                    plannedRecipe.recipe.name,
                ),
            )
        }
        machineInserters.place(row, emptyList())
        placeLanes(row)
        placeProduct(row, emptyList())
        return row
    }

    /** The rectangle of a row: its corner, one machine footprint and the stride between copies. */
    private fun box(shape: RowShape, size: Dimension, stride: Int) =
        RowBox(LANE_MARGIN, shape.factoryTop, size.width, size.height, stride)

    /**
     * Builds a direct-feed band as one row of interleaved machines: every machine sits a tile apart
     * so the gap column between two of them can carry the inserters that move their intermediate,
     * and the whole band shares the lane rows and the output belt.
     */
    private fun placeBand(plannedRow: PlannedRow, bandTop: Int): Row {
        val internal = plannedRow.links.map { it.material }
        val plannedRecipes = plannedRow.plannedRecipes
        val size = planner.footprint(plannedRecipes.first())
        val solids =
            plannedRecipes
                .flatMap { it.recipe.ingredients.keys }
                .filter { !logistics.isFluid(it) && it !in internal }
                .distinct()
                .sortedBy { it.name }
        val shape = rowShape(solids, size.height, bandTop)
        val stride = size.width + 1
        val machines =
            plannedRow.machines.mapIndexed { index, plannedRecipe ->
                PlacedMachine(plannedRecipe, LANE_MARGIN + index * stride)
            }
        val row =
            Row(
                plannedRow = plannedRow,
                machines = machines,
                box = box(shape, size, stride),
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
                    machine.plannedRecipe.factory.identifier,
                    machine.plannedRecipe.recipe.name,
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
    private fun placeProduct(row: Row, internal: List<Material>) {
        val product =
            row.machines.firstNotNullOfOrNull {
                externalProduct(it.plannedRecipe, internal, logistics)
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
        if (material !in plan.beltMaterials) return EAST
        val produced = row.solids.filter { it in plan.beltMaterials }.sortedBy { outletX(it) }
        val index = produced.indexOf(material)
        val middle = produced.size / 2
        if (index in 0 until middle) return EAST
        if (index >= (produced.size + 1) / 2) return WEST
        return if (outletX(material) <= (row.left + row.right) / 2) EAST else WEST
    }

    /**
     * The row that puts [material] on a belt, or null while it is not placed or when the plan
     * imports it.
     */
    fun producerRow(material: Material): Row? {
        val producer = plan.producerOf(material) ?: return null
        return placedRows.firstOrNull { row -> row.plannedRow == producer }
    }

    /**
     * The tile east of the row that puts [material] on a belt, or Far East when it is not placed.
     */
    fun outletX(material: Material): Int = producerRow(material)?.right?.plus(1) ?: Int.MAX_VALUE
}
