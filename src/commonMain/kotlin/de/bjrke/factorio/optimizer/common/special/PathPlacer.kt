package de.bjrke.factorio.optimizer.common.special

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.endName
import de.bjrke.factorio.optimizer.common.layout.markTunnels
import de.bjrke.factorio.optimizer.common.layout.placeEnd
import de.bjrke.factorio.optimizer.common.layout.placeSurface
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.adjacent

/** All the directions, Clock-wise EAST first, like most of the stuff has to be delivered. */
internal val DIRECTION_STEPS = Direction.entries.map { it.step }

/** A tile's role in a route: a plain belt, a tunnel input or a tunnel output, all facing. */
internal sealed interface PathRole {
    val direction: Direction
}

internal data class SurfaceRole(override val direction: Direction) : PathRole

internal data class TunnelInputRole(override val direction: Direction) : PathRole

internal data class TunnelOutputRole(override val direction: Direction) : PathRole

/**
 * Draws the transport entities of [material] along [nodes]; a jump becomes an underground run. A
 * route may revisit a tile: to enter a tunnel a belt must already head straight, so the search can
 * step back onto a tile to turn first. A tile carries one entity, so each gets its final role from
 * [roles] before anything is placed. A fluid material routes as pipes.
 */
internal fun placePath(
    board: LayoutBoard,
    material: Material,
    nodes: List<Coordinate>,
    finalHeading: Direction? = null,
) {
    for ((tile, role) in roles(nodes, finalHeading)) {
        if (!board.isFree(tile)) continue
        when (role) {
            is SurfaceRole -> board.placeSurface(material, tile, role.direction)
            is TunnelInputRole -> board.placeEnd(material, tile, role.direction, Io.INPUT)
            is TunnelOutputRole -> board.placeEnd(material, tile, role.direction, Io.OUTPUT)
        }
    }
    board.markTunnels(nodes, board.endName(material))
}

/**
 * The role of every tile in [nodes], keyed by tile. A tunnel end beats a plain belt, so a revisit
 * never breaks a paired run; otherwise the last occurrence wins. [finalHeading] is the direction
 * the last tile must face, so a feed can turn onto its target regardless of the side the route
 * arrived from.
 */
private fun roles(
    nodes: List<Coordinate>,
    finalHeading: Direction?,
): Map<Coordinate, PathRole> {
    if (nodes.size <= 1) return emptyMap()

    val roles = LinkedHashMap<Coordinate, PathRole>()

    fun canBeAssigned(tile: Coordinate) =
        when (roles[tile]) {
            is TunnelInputRole,
            is TunnelOutputRole -> false
            else -> true
        }

    fun assign(tile: Coordinate, role: SurfaceRole) {
        if (canBeAssigned(tile)) roles[tile] = role
    }

    for ((tile, next) in nodes.zipWithNext()) {
        val direction = directionBetween(tile, next)
        if (tile.adjacent(next)) {
            val surfaceRole = SurfaceRole(direction)
            assign(tile, surfaceRole)
            assign(next, surfaceRole)
        } else {
            roles[tile] = TunnelInputRole(direction)
            roles[next] = TunnelOutputRole(direction)
        }
    }
    if (finalHeading != null) roles[nodes.last()] = SurfaceRole(finalHeading)

    return roles
}

/** The cardinal direction from [from] to [to]; the caller guarantees they share a row or column. */
internal fun directionBetween(from: Coordinate, to: Coordinate) =
    when {
        to.x > from.x -> EAST
        to.x < from.x -> WEST
        to.y > from.y -> SOUTH
        else -> NORTH
    }
