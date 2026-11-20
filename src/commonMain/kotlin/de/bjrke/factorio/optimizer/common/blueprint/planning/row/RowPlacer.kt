package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BeltGroup
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BeltPackRequest
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_ROW_BELTS
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.RowBeltRates
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineDirection
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.oneLaneCapacity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.packRowBelts
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowInputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowOutputs
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Dimension
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area

/**
 * The column the machine rows start on: ten columns east of [IMPORT_FEED], the width of the west
 * corridor. That corridor holds the import connectors, their feed belts and a mixed lane's merge,
 * so it must hold the widest connector (five tiles for a chest), the merge column
 * [EAST_MERGE_OFFSET] columns west of the machines and that merge's approach tile, which puts its
 * floor at eight. Starting the machines east of the import markers keeps every placed tile on a
 * non-negative column, and the layout grows east from here. The import trimmer later moves every
 * connector to the furthest west column with a straight feed and removes the straight feed prefix,
 * so this offsets the blueprint instead of widening it — the finished layout starts at the
 * connector, not here.
 */
internal const val FIRST_MACHINE_X = IMPORT_FEED + 10

/** Free rows between two recipe rows, so their belts can be routed between them. */
private const val ROW_GAP = 17

/**
 * The first row starts this far south of the top of the coordinate field: the north lanes, the
 * fluid lanes and the route margin then stay on non-negative rows, so every absolute tile is
 * positive. It deliberately does not follow [ROW_GAP]: the row spacing may shrink, while this floor
 * must still hold the three rows of a north lane, a stack of fluid lanes and the router's margin.
 * Compaction removes empty corridors inside the placed bounds only, so it preserves this lower
 * bound.
 */
internal const val NORTH_MARGIN = 17

/**
 * How far west of the row an east-fed mixed lane starts its T merge. A fluid lane may sit on the
 * merge's turn row, so the turn column must stay west of the machine column where the lane head
 * starts; three columns leave both the merge and its U-turn clear of it.
 */
internal const val EAST_MERGE_OFFSET = 3

/** How far east of the row a west-fed mixed lane starts its T merge, clear of the product end. */
private const val WEST_MERGE_OFFSET = 8

/**
 * Places the machine rows once: a plain production as a row of copies, a direct-feed band as one
 * row of interleaved machines. Owns the placement order the later delivery and fluid stages read.
 *
 * Capacity-safe inputs pair greedily, one material per lane, fed by a T merge west or east of the
 * row. Pairing a row that already fits still saves a belt row, the far-lane long inserter and often
 * a free machine column for a pole. An import anchors its pair west to east; two produced feeds
 * from opposite directions stay separate so neither has to cross the row. A single-recipe row that
 * would otherwise need more than [MAX_ROW_BELTS] solid rows may also pair over-capacity inputs as a
 * last resort; a multi-recipe band may not, so the band search rejects it and builds the recipes as
 * their own rows.
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
        var y = NORTH_MARGIN
        for (unit in planner.units) {
            val row = placeRow(unit, y)
            placedRows += row
            y = row.belts.map { it.y }.plus(row.factoryBottom).max() + ROW_GAP
        }
    }

    enum class FixedBeltLocation(
        val side: LineSide,
        /** Whether a mixed lane's T merge sits above this belt row. */
        val above: Boolean,
        val yfun: (Int, Int) -> Int,
    ) {
        NEAR_TOP(LineSide.NORTH, false, { top, _ -> top - 2 }),
        NEAR_BOTTOM(LineSide.SOUTH, true, { _, bottom -> bottom + 2 }),
        FAR_TOP(LineSide.NORTH, true, { top, _ -> top - 3 }),
        FAR_BOTTOM(LineSide.SOUTH, false, { _, bottom -> bottom + 3 }),
    }

    private fun rowShape(
        size: Dimension,
        bandTop: Int,
        unit: RowUnit,
        machines: List<PlacedMachine>,
    ): RowLanes {
        val plannedRow = unit.row
        val linked = plannedRow.links.mapTo(mutableSetOf()) { it.material }
        val outputs = rowOutputs(machines.map { it.production }, logistics).minus(linked)
        // A link only internalizes the consumers it actually reaches. A material another machine
        // of the same row still takes from a belt keeps its input lane.
        val inputs =
            rowInputs(machines.map { it.production }, logistics)
                .minus(plannedRow.fullyInternalInputs)
        val directions =
            (inputs.keys - outputs.keys).associateWith { planner.feedDirection(it) ?: EAST }
        val imports = inputs.keys - plan.beltMaterials
        val laneCapacity = logistics.oneLaneCapacity()
        val packing =
            packRowBelts(
                BeltPackRequest(
                    rates = RowBeltRates(inputs, outputs, laneCapacity),
                    directions = directions,
                    imports = imports,
                    allowOverCapacityPairs = plannedRow.productions.size == 1,
                ),
            )
        val groups = packing.groups
        if (!packing.fits) {
            failures +=
                "row needs ${groups.size} solid belt rows, only $MAX_ROW_BELTS fit: " +
                    groups.drop(MAX_ROW_BELTS).flatMap { it.materials }.joinToString { it.name }
        }

        val rates = RowRates(inputs, outputs, logistics.oneLaneCapacity())
        val placements = assignRowBelts(planner, unit, groups, rates)
        val topLanes = placements.count { it.location.side == LineSide.NORTH }

        val factoryTop = bandTop + topLanes + 1
        val factoryBottom = factoryTop + size.height - 1
        val rowRight =
            machines.lastOrNull()?.left?.plus(size.width - 1) ?: (FIRST_MACHINE_X + size.width - 1)

        val belts = placements.map { (location, group) ->
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
            if (output != null) planner.productDirection(output) else group.direction ?: EAST
        val merge =
            if (group.materials.size > 1) {
                BeltMerge(
                    column =
                        if (direction == EAST) FIRST_MACHINE_X - EAST_MERGE_OFFSET
                        else rowRight + WEST_MERGE_OFFSET,
                    above = location.above,
                    westMaterial =
                        if (direction == EAST) group.materials[0] else group.materials[1],
                    eastMaterial =
                        if (direction == EAST) group.materials[1] else group.materials[0],
                    direction = direction,
                )
            } else null
        val load =
            when {
                output != null -> productLoad(output, location.side, direction)
                merge != null -> merge.load
                else -> LaneLoad.full(group.materials.single())
            }
        return RowBelt(y, load, direction, io, location.side, merge)
    }

    /**
     * A product belt is filled from the machine edge only. An inserter drops onto the lane farther
     * from it, in the belt's own left/right frame: an eastbound belt above the machines (inserters
     * below it) fills its left lane, one below them (inserters above it) its right lane; a
     * westbound belt flips both. The other lane stays free.
     */
    private fun productLoad(material: Material, side: LineSide, direction: Direction) =
        LaneLoad.half(
            material,
            left = (side == LineSide.NORTH) == (direction == EAST),
        )

    /**
     * Builds a row: a plain production repeats its machine, while a direct-feed band interleaves
     * machines. A gap with a link gets an extra column for the inserters that move the intermediate
     * between the two machines; every other neighbor pair touches.
     */
    private fun placeRow(unit: RowUnit, bandTop: Int): Row {
        val plannedRow = unit.row
        val size = planner.footprint(plannedRow.machines.first())

        val linkedGaps = plannedRow.links.mapTo(mutableSetOf()) { it.gap }
        val machines = buildList {
            var left = FIRST_MACHINE_X
            for ((index, production) in plannedRow.machines.withIndex()) {
                add(PlacedMachine(production, left))
                left += size.width
                if (index in linkedGaps) left += 1
            }
        }

        val lanes = rowShape(size, bandTop, unit, machines)
        val row =
            Row(
                plannedRow = plannedRow,
                machines = machines,
                box = Area(Coordinate(FIRST_MACHINE_X, lanes.factoryTop), size),
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
     * mixed belt also gets its T merge: the mixed merge tile, and the U-turn turn and approach
     * tiles unless the turn material is produced and can feed the merge from the outside
     * (`sideLoadsTurn`). The two feeders' last straight tiles are left to the delivery router.
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
            placeMergeBelt(Coordinate(merge.column, mergeRow), merge.load, merge.mergeDirection)
            if (!sidesLoadsTurn(belt, merge)) {
                val turnRow = merge.turnRow(belt.y)
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
    }

    /**
     * Whether the delivery router feeds [merge]'s turn material straight into the lane. An import
     * keeps the U-turn: its marker is on the west side, so routing it around the merge is the only
     * way to reach the turn lane. A produced trunk is free to approach from the outside, where the
     * lane has no machines to miss.
     */
    private fun sidesLoadsTurn(belt: RowBelt, merge: BeltMerge) =
        merge.sideLoadsTurn(
            belt.side,
            produced = plan.producerOf(merge.turnMaterial) != null,
        )

    private fun placeMergeBelt(coordinate: Coordinate, load: LaneLoad, direction: Direction) {
        if (!board.isFree(coordinate)) {
            failures += "merge tile $coordinate is already occupied"
            return
        }
        board.placeBelt(coordinate, load, direction)
    }

    /**
     * The row that puts [material] on a belt, or null while it is not placed or when the plan
     * imports it.
     */
    fun producerRow(material: Material): Row? {
        val producer = plan.producerOf(material) ?: return null
        return placedRows.firstOrNull { row -> row.plannedRow == producer }
    }
}
