package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BeltGroup
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_ROW_BELTS
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineDirection
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.oneLaneCapacity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.packRowBelts
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowInputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowOutputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.FAR_BOTTOM
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.FAR_TOP
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.NEAR_BOTTOM
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.NEAR_TOP
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Dimension
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe rows, so their belts can be routed between them. */
private const val ROW_GAP = 17

/** How far west of the row an east-fed mixed lane starts its T merge. */
private const val EAST_MERGE_OFFSET = 2

/** How far east of the row a west-fed mixed lane starts its T merge, clear of the product end. */
private const val WEST_MERGE_OFFSET = 8

/**
 * Places the machine rows once: a plain production as a row of copies, a direct-feed band as one
 * row of interleaved machines. Owns the placement order the later delivery and fluid stages read.
 *
 * A row that would need more than [MAX_ROW_BELTS] solid belt rows pairs its lowest-rate eligible
 * inputs two per belt; each pair is fed by a T merge west or east of the row and carries one
 * material per lane. Inputs whose rate fits one lane pair first, so a fast input only shares a belt
 * when the row would otherwise drop a row (or its product). Rows that already fit keep their
 * single-material belts.
 */
internal class RowPlacer(
    private val board: LayoutBoard,
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val planner: RowPlanner,
    private val failures: MutableList<String> = mutableListOf(),
) {

    /** Every row once, in placement order; a band row serves all of its productions. */
    val placedRows = mutableListOf<Row>()

    private val machineInserters = MachineInserterPlacer(board, logistics, planner)
    private val bandInterfaces = BandInterfacePlacer(board, logistics)

    fun placeAll() {
        var y = 0
        for ((plannedRow) in planner.units) {
            val row = placeRow(plannedRow, y)
            placedRows += row
            y = row.belts.map { it.y }.plus(row.factoryBottom).max() + ROW_GAP
        }
    }

    enum class FixedBeltLocation(val side: LineSide, val yfun: (Int, Int) -> Int) {
        NEAR_TOP(LineSide.NORTH, { top, _ -> top - 2 }),
        NEAR_BOTTOM(LineSide.SOUTH, { _, bottom -> bottom + 2 }),
        FAR_TOP(LineSide.NORTH, { top, _ -> top - 3 }),
        FAR_BOTTOM(LineSide.SOUTH, { _, bottom -> bottom + 3 }),
    }

    /** The row's solid input and output rates, the two maps every belt decision reads. */
    private class RowRates(
        val inputs: Map<Material, Double>,
        val outputs: Map<Material, Double>,
    )

    private fun rowShape(
        size: Dimension,
        bandTop: Int,
        plannedRow: PlannedRow,
        machines: List<PlacedMachine>,
    ): RowLanes {
        val linked = plannedRow.links.mapTo(mutableSetOf()) { it.material }
        val outputs = rowOutputs(machines.map { it.production }, logistics).minus(linked)
        // A link only internalizes the consumers it actually reaches. A material another machine
        // of the same row still takes from a belt keeps its input lane.
        val inputs =
            rowInputs(machines.map { it.production }, logistics)
                .minus(plannedRow.fullyInternalInputs)
        val solids =
            (inputs.entries + outputs.entries).sortedByDescending { it.value }.map { it.key }
        val directions =
            (inputs.keys - outputs.keys).associateWith { laneDirection(it, inputs.keys) }
        val packing = packRowBelts(solids, inputs, outputs, directions, logistics.oneLaneCapacity())
        val groups = packing.groups
        if (!packing.fits) {
            failures +=
                "row needs ${groups.size} solid belt rows, only $MAX_ROW_BELTS fit: " +
                    groups.drop(MAX_ROW_BELTS).flatMap { it.materials }.joinToString { it.name }
        }

        val firstOutput = groups.indexOfFirst { group -> group.materials.any { it in outputs } }
        val beltLocations =
            when (firstOutput) {
                0 -> listOf(NEAR_BOTTOM, NEAR_TOP, FAR_TOP, FAR_BOTTOM)
                2 -> listOf(NEAR_TOP, NEAR_BOTTOM, FAR_BOTTOM, FAR_TOP)
                else -> listOf(NEAR_TOP, NEAR_BOTTOM, FAR_TOP, FAR_BOTTOM)
            }

        val locations = beltLocations.take(MAX_ROW_BELTS)
        val used = groups.take(locations.size)
        val topLanes = locations.take(used.size).count { it.side == LineSide.NORTH }

        val factoryTop = bandTop + topLanes + 1
        val factoryBottom = factoryTop + size.height - 1
        val rowRight =
            machines.lastOrNull()?.left?.plus(size.width - 1) ?: (LANE_MARGIN + size.width - 1)

        val rates = RowRates(inputs, outputs)
        val belts =
            locations.zip(used).map { (location, group) ->
                beltFor(
                    location,
                    group,
                    location.yfun(factoryTop, factoryBottom),
                    rowRight,
                    rates,
                )
            }

        return RowLanes(factoryTop = factoryTop, belts = belts)
    }

    /** One belt row: a single input, a pair with its T merge, or the one-lane product belt. */
    private fun beltFor(
        location: FixedBeltLocation,
        group: BeltGroup,
        y: Int,
        rowRight: Int,
        rates: RowRates,
    ): RowBelt {
        val output = group.materials.firstOrNull { it in rates.outputs }
        val io = if (output != null) Io.OUTPUT else Io.INPUT
        val direction =
            if (output != null) EAST
            else group.direction ?: laneDirection(group.materials.first(), rates.inputs.keys)
        val merge =
            if (group.materials.size > 1) {
                BeltMerge(
                    column =
                        if (direction == EAST) LANE_MARGIN - EAST_MERGE_OFFSET
                        else rowRight + WEST_MERGE_OFFSET,
                    above = mergeAbove(location),
                    westMaterial =
                        if (direction == EAST) group.materials[0] else group.materials[1],
                    eastMaterial =
                        if (direction == EAST) group.materials[1] else group.materials[0],
                    direction = direction,
                )
            } else null
        val load =
            when {
                output != null -> productLoad(output, location.side)
                merge != null -> merge.load
                else -> LaneLoad.full(group.materials.single())
            }
        return RowBelt(y, load, direction, io, location.side, merge)
    }

    /**
     * A product belt is filled from the machine edge only. An inserter drops onto the lane farther
     * from it: an eastbound belt above the machines (inserters below it) fills its left lane, one
     * below them (inserters above it) its right lane. The other lane stays free.
     */
    private fun productLoad(material: Material, side: LineSide) =
        when (side) {
            LineSide.NORTH -> LaneLoad.half(material, left = true)
            LineSide.SOUTH -> LaneLoad.half(material, left = false)
        }

    /** A far north or near south lane merges above itself; the other two merge below. */
    private fun mergeAbove(location: FixedBeltLocation) =
        when (location) {
            FAR_TOP,
            NEAR_BOTTOM -> true
            NEAR_TOP,
            FAR_BOTTOM -> false
        }

    /**
     * Builds a row: a plain production repeats its machine, while a direct-feed band interleaves
     * machines. A gap with a link gets an extra column for the inserters that move the intermediate
     * between the two machines; every other neighbor pair touches.
     */
    private fun placeRow(plannedRow: PlannedRow, bandTop: Int): Row {
        val size = planner.footprint(plannedRow.machines.first())

        val linkedGaps = plannedRow.links.mapTo(mutableSetOf()) { it.gap }
        val machines = buildList {
            var left = LANE_MARGIN
            for ((index, production) in plannedRow.machines.withIndex()) {
                add(PlacedMachine(production, left))
                left += size.width
                if (index in linkedGaps) left += 1
            }
        }

        val lanes = rowShape(size, bandTop, plannedRow, machines)
        val row =
            Row(
                plannedRow = plannedRow,
                machines = machines,
                box = Area(Coordinate(LANE_MARGIN, lanes.factoryTop), size),
                lanes = lanes,
            )
        for (machine in machines) {
            board.place(
                MachineEntity(
                    machine.production.factory,
                    Coordinate(machine.left, row.factoryTop).area(size),
                    machine.production.recipe.name,
                    blueprintMachineDirection(machine.production.factory),
                ),
            )
        }
        machineInserters.place(row)
        bandInterfaces.place(row)

        placeLanes(row)

        return row
    }

    /**
     * Places the input output belts of [row] across the whole row, including the gaps of a band. A
     * mixed belt also gets its T merge: the mixed merge tile, the U-turn turn tile and the approach
     * tile; the two feeders' last straight tiles are left to the delivery router.
     */
    private fun placeLanes(row: Row) {
        for (belt in row.belts) {
            val merge = belt.merge
            if (merge == null) {
                for (x in row.left..row.right) {
                    board.placeBelt(Coordinate(x, belt.y), belt.load, belt.direction)
                }
                continue
            }
            for (x in merge.laneColumns(row.right, row.left)) {
                board.placeBelt(Coordinate(x, belt.y), belt.load, belt.direction)
            }
            val mergeRow = merge.mergeRow(belt.y)
            val turnRow = merge.turnRow(belt.y)
            placeMergeBelt(Coordinate(merge.column, mergeRow), merge.load, merge.mergeDirection)
            placeMergeBelt(
                Coordinate(merge.turnColumn, turnRow),
                LaneLoad.full(merge.turnMaterial),
                merge.mergeDirection,
            )
            placeMergeBelt(
                Coordinate(merge.turnColumn, mergeRow),
                LaneLoad.full(merge.turnMaterial),
                merge.approachDirection,
            )
        }
    }

    private fun placeMergeBelt(coordinate: Coordinate, load: LaneLoad, direction: Direction) {
        if (!board.isFree(coordinate)) {
            failures += "merge tile $coordinate is already occupied"
            return
        }
        board.placeBelt(coordinate, load, direction)
    }

    /**
     * Where a lane is fed: imports from the west. Produced materials split by their producer's
     * outlet, the left half feeding from the west and the right half from the east, so two trunks
     * into one row do not have to cross. A lone produced input picks the nearer end.
     */
    fun laneDirection(material: Material, solids: Iterable<Material>): Direction {
        if (material !in plan.beltMaterials) return EAST
        val produced = solids.filter { it in plan.beltMaterials }.sortedBy { outletX(it) }
        return if (produced.indexOf(material) < produced.size / 2) EAST else WEST
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
