package de.bjrke.factorio.optimizer.common.blueprint.layout.entity

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * What a belt tile carries in its two lanes, in the belt's own left/right frame.
 *
 * A plain input belt fills both lanes with the same [material]. A mixed input belt carries one
 * material per lane. An output belt only fills the lane its inserters drop onto; the other lane is
 * `null` and is free for a later side-load. Left and right are the belt's own sides (looking along
 * [BeltEntity.direction]), not the compass; a turn keeps the slots.
 */
data class LaneLoad(val left: Material?, val right: Material?) {

    init {
        require(left != null || right != null) { "a belt needs at least one lane material" }
    }

    /** The materials on the belt, without duplicates. */
    val materials: Set<Material>
        get() = setOfNotNull(left, right)

    /** The material the belt is labelled with; a half belt uses its one filled lane. */
    val material: Material
        get() = left ?: requireNotNull(right) { "a belt needs at least one lane material" }

    /** Whether both lanes are filled. */
    val isFull: Boolean
        get() = left != null && right != null

    /** Whether the two lanes carry different materials. */
    val isMixed: Boolean
        get() = left != null && right != null && left != right

    /**
     * The material on the lane a neighbor on [side] side-loads into, or null when [side] is not a
     * side of the belt (a straight feed or the belt's back).
     */
    fun laneAt(direction: Direction, side: Direction): Material? =
        when (side) {
            leftSide(direction) -> left
            rightSide(direction) -> right
            else -> null
        }

    companion object {

        /** A plain belt: both lanes carry [material]. */
        fun full(material: Material) = LaneLoad(material, material)

        /** A mixed belt: one material per lane. */
        fun mixed(left: Material, right: Material) = LaneLoad(left, right)

        /** A belt whose inserters fill only one lane, e.g. a row's output belt. */
        fun half(material: Material, left: Boolean) =
            if (left) LaneLoad(material, null) else LaneLoad(null, material)

        /** The compass side of a belt's left lane. */
        fun leftSide(direction: Direction): Direction =
            when (direction) {
                NORTH -> WEST
                EAST -> NORTH
                SOUTH -> EAST
                WEST -> SOUTH
            }

        /** The compass side of a belt's right lane. */
        fun rightSide(direction: Direction): Direction =
            when (direction) {
                NORTH -> EAST
                EAST -> SOUTH
                SOUTH -> WEST
                WEST -> NORTH
            }
    }
}
