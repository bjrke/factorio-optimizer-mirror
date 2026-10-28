package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.service.FactoryProduction

/** One machine of a row: the production it builds and the column it starts on. */
internal class PlacedMachine(val production: FactoryProduction, val left: Int)

/**
 * The row geometry before a row exists: where the machines start and which belt rows they need.
 * [laneNearRow] and [laneFarRow] are the input belts above the machines, [laneBelowRow] a third
 * input belt below them, [outputLaneRow] the product belt.
 */
internal class RowShape(
    val factoryTop: Int,
    val laneNearRow: Int?,
    val laneFarRow: Int?,
    val laneBelowRow: Int?,
    val outputLaneRow: Int,
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
    /** The plan row this geometry places. */
    val plannedRow: PlannedRow,
    /** The production each machine builds, left to right; one entry per machine. */
    val machines: List<PlacedMachine>,
    val box: Area,
    val lanes: RowLanes,
) {

    /** The direct insertions between the machines, by the gap they sit in. */
    val interfaces: List<PlannedLink>
        get() = plannedRow.links

    /** The belt rows around the machines: the input lanes above and a third input belt below. */
    val beltRows: List<Int>
        get() = listOfNotNull(lanes.nearRow, lanes.farRow, lanes.belowRow)

    val copies
        get() = machines.size

    val left
        get() = box.topLeft.x

    val factoryTop
        get() = box.topLeft.y

    val width
        get() = box.width

    val height
        get() = box.height

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
        get() = machines.last().left + width - 1
}
