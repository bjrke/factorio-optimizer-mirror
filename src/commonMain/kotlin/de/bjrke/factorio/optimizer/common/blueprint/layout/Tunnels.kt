package de.bjrke.factorio.optimizer.common.blueprint.layout

import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.chebyshevLen
import de.bjrke.factorio.optimizer.common.core.model.direction

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
     * One tile of an underground run: the tile, the run's axis and its underground prototype (belt
     * tier or pipe).
     */
    private data class Key(val pos: Coordinate, val axis: Axis, val type: String)

    private val under = mutableSetOf<Key>()

    /** Whether a run of [type] along [step] already passes under [coordinate]. */
    fun has(coordinate: Coordinate, step: Coordinate, type: String) =
        Key(coordinate, axisOf(step), type) in under

    /** Records the run from [from] to [to] on every tile it passes under. */
    fun mark(from: Coordinate, to: Coordinate, type: String) {
        val delta = to - from
        val step = delta.direction
        val axis = axisOf(step)
        for (k in 1 until delta.chebyshevLen) {
            under.add(Key(from + step * k, axis, type))
        }
    }

    fun clear() = under.clear()

    private fun axisOf(direction: Coordinate) =
        if (direction.x != 0) Axis.HORIZONTAL else Axis.VERTICAL
}
