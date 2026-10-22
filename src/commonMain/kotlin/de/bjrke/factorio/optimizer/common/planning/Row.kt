package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.PlannedLink

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
