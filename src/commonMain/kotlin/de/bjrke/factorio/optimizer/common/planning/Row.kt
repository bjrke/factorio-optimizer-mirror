package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.LineSide
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.service.FactoryProduction

/** One machine of a row: the production it builds and the column it starts on. */
internal class PlacedMachine(val production: FactoryProduction, val left: Int)

internal data class RowBelt(
    val y: Int,
    val material: Material,
    val direction: Direction,
    val io: Io,
    val side: LineSide,
)

/**
 * The input and output belt rows of a row and the solid materials those lanes carry.
 *
 * The row geometry before a row exists: where the machines start and which belt rows they need.
 * [nearRow] and [farRow] are the input belts above the machines, [belowRow] a third input belt
 * below them, [outputRow] the product belt.
 */
internal data class RowLanes(
    val factoryTop: Int,
    val belts: List<RowBelt>,
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

    val factoryBottom
        get() = factoryTop + height - 1

    val right
        get() = machines.last().left + width - 1

    val belts
        get() = lanes.belts

    fun inserterRow(side: LineSide) =
        when (side) {
            LineSide.NORTH -> factoryTop - 1
            LineSide.SOUTH -> factoryBottom + 1
        }
}
