package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.placeBelt
import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.LineSide
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineDirection
import de.bjrke.factorio.optimizer.common.plan.inputPerMachine
import de.bjrke.factorio.optimizer.common.plan.outputPerMachine
import de.bjrke.factorio.optimizer.common.planning.RowPlacer.FixedBeltLocation.FAR_BOTTOM
import de.bjrke.factorio.optimizer.common.planning.RowPlacer.FixedBeltLocation.FAR_TOP
import de.bjrke.factorio.optimizer.common.planning.RowPlacer.FixedBeltLocation.NEAR_BOTTOM
import de.bjrke.factorio.optimizer.common.planning.RowPlacer.FixedBeltLocation.NEAR_TOP
import de.bjrke.factorio.optimizer.common.service.FactoryProduction

/** Tiles on the left kept clear for the import markers and the feed belts. */
private const val LANE_MARGIN = 40

/** Free rows between two recipe rows, so their belts can be routed between them. */
private const val ROW_GAP = 17

/**
 * Places the machine rows once: a plain production as a row of copies, a direct-feed band as one
 * row of interleaved machines. Owns the placement order the later delivery and fluid stages read.
 */
internal class RowPlacer(
    private val board: LayoutBoard,
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
    private val planner: RowPlanner,
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

    private fun rowShape(
        height: Int,
        bandTop: Int,
        machines: List<PlacedMachine>,
        internal: Set<Material>,
    ): RowLanes {
        val outputs = rates(machines, ::outputRates).minus(internal)
        val inputs = rates(machines, ::inputRates).minus(internal)

        val solids =
            (inputs.entries + outputs.entries).sortedByDescending { it.value }.map { it.key }

        val firstOutput = solids.indexOfFirst { it in outputs }
        val beltLocations =
            when (firstOutput) {
                0 -> listOf(NEAR_BOTTOM, NEAR_TOP, FAR_TOP, FAR_BOTTOM)
                2 -> listOf(NEAR_TOP, NEAR_BOTTOM, FAR_BOTTOM, FAR_TOP)
                else -> listOf(NEAR_TOP, NEAR_BOTTOM, FAR_TOP, FAR_BOTTOM)
            }

        val topLanes = beltLocations.take(solids.size).count { it.side == LineSide.NORTH }

        val factoryTop = bandTop + topLanes + 1
        val factoryBottom = factoryTop + height - 1

        val belts = buildList {
            beltLocations.zip(solids).forEach { (beltLocation, material) ->
                val side = beltLocation.side
                val y = beltLocation.yfun(factoryTop, factoryBottom)
                val (io, direction) =
                    if (material in outputs) Io.OUTPUT to EAST
                    else Io.INPUT to laneDirection(material, inputs.keys)

                add(RowBelt(y, material, direction, io, side))
            }
        }

        return RowLanes(
            factoryTop = factoryTop,
            belts = belts,
        )
    }

    private fun rates(
        machines: List<PlacedMachine>,
        inp: (FactoryProduction) -> List<Pair<Material, Double>>,
    ) =
        machines
            .map { it.production }
            .flatMap(inp)
            .filterNot { (material, _) -> logistics.isFluid(material) }
            .groupingBy { (material, _) -> material }
            .fold(0.0) { acc, (_, rate) -> acc + rate }

    private fun outputRates(production: FactoryProduction) =
        production.recipe.result.entries.map { (material, amount) ->
            material to outputPerMachine(production, amount)
        }

    private fun inputRates(production: FactoryProduction) =
        production.recipe.ingredients.entries.map { (material, amount) ->
            material to inputPerMachine(production, amount)
        }

    /**
     * Builds a row: a plain production repeats its machine, while a direct-feed band interleaves
     * machines. A gap with a link gets an extra column for the inserters that move the intermediate
     * between the two machines; every other neighbor pair touches.
     */
    private fun placeRow(plannedRow: PlannedRow, bandTop: Int): Row {
        val internal = plannedRow.links.map { it.material }.toSet()
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

        val lanes = rowShape(size.height, bandTop, machines, internal)
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
     * Places the input output belts of [row] across the whole row, including the gaps of a band.
     */
    private fun placeLanes(row: Row) {
        for ((y, material, direction) in row.belts) {
            for (x in row.left..row.right) {
                board.placeBelt(Coordinate(x, y), material, direction)
            }
        }
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
