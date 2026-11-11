package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/** One machine of a row: the production it builds and the column it starts on. */
internal class PlacedMachine(val production: FactoryProduction, val left: Int)

/**
 * The T merge that turns two single-material feeds into one mixed lane.
 *
 * The merge tile sits on [column] in the row beyond the lane (above for a far north lane and a near
 * south lane, below for a near north lane and a far south lane). One feeder enters from the west
 * and one from the east; the [direction] says which side the two source belts come from and
 * therefore which feeder is the straight one and which makes the U-turn. The two feeder sides map
 * to the lanes by the merge tile's own direction: an above merge (south) fills its right lane from
 * the west and its left lane from the east; a below merge (north) fills left from the west and
 * right from the east. A turn keeps the lane slots.
 */
internal data class BeltMerge(
    val column: Int,
    val above: Boolean,
    val westMaterial: Material,
    val eastMaterial: Material,
    /** The lane's travel direction: the source belts approach from this side. */
    val direction: Direction,
) {

    /** The mixed lane load; a turn keeps these left/right slots. */
    val load: LaneLoad
        get() =
            if (above) LaneLoad.mixed(left = eastMaterial, right = westMaterial)
            else LaneLoad.mixed(left = westMaterial, right = eastMaterial)

    /** The merge tile's own facing (it is perpendicular to the lane). */
    val mergeDirection: Direction
        get() = if (above) SOUTH else NORTH

    /** The feeder that enters the merge straight from the lane's source side. */
    val straightMaterial: Material
        get() = if (direction == EAST) westMaterial else eastMaterial

    /** The feeder that makes the U-turn into the other side of the merge. */
    val turnMaterial: Material
        get() = if (direction == EAST) eastMaterial else westMaterial

    /** The column of the U-turn feeder's turn and approach tiles. */
    val turnColumn: Int
        get() = if (direction == EAST) column + 1 else column - 1

    /** The direction of the U-turn feeder's approach tile (it feeds the merge from the side). */
    val approachDirection: Direction
        get() = if (direction == EAST) WEST else EAST

    fun mergeRow(y: Int) = if (above) y - 1 else y + 1

    fun turnRow(y: Int) = if (above) y - 2 else y + 2

    /** The lane's columns, including the merge column. */
    fun laneColumns(right: Int, left: Int) = if (direction == EAST) column..right else left..column
}

/**
 * One belt row of a row. A plain input belt fills both lanes with [LaneLoad.material]; a mixed
 * input belt has one material per lane; a product belt fills only the lane its inserters drop onto.
 */
internal data class RowBelt(
    val y: Int,
    val load: LaneLoad,
    val direction: Direction,
    val io: Io,
    val side: LineSide,
    /** Set only for a mixed input belt. */
    val merge: BeltMerge? = null,
) {
    val material: Material
        get() = load.material

    val materials: Set<Material>
        get() = load.materials

    val isMixed: Boolean
        get() = load.isMixed
}

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
