package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.chebyshevLen
import de.bjrke.factorio.optimizer.common.model.direction

/**
 * The underground runs passing under the placed tiles. Two runs may share a tile when their axes
 * differ, or when their underground types differ, so only a matching key clashes.
 */
internal class Tunnels {

    /** The axis an underground run travels along; only a same-axis run can clash. */
    private enum class Axis {
        HORIZONTAL,
        VERTICAL,
    }

    /**
     * Identifies an underground run: its axis and its underground prototype (belt tier or pipe).
     */
    private data class Key(val axis: Axis, val type: String)

    private val under = mutableMapOf<Coordinate, MutableSet<Key>>()

    /** Whether a run of [type] along [step] already passes under [coordinate]. */
    fun has(coordinate: Coordinate, step: Coordinate, type: String): Boolean =
        Key(axisOf(step), type) in under[coordinate].orEmpty()

    /** Records the run from [from] to [to] on every tile it passes under. */
    fun mark(from: Coordinate, to: Coordinate, type: String) {
        val delta = to - from
        val step = delta.direction
        val key = Key(axisOf(step), type)
        for (k in 1 until delta.chebyshevLen) {
            under.getOrPut(from + step * k) { mutableSetOf() } += key
        }
    }

    fun clear() = under.clear()

    private fun axisOf(direction: Coordinate) =
        if (direction.x != 0) Axis.HORIZONTAL else Axis.VERTICAL
}
