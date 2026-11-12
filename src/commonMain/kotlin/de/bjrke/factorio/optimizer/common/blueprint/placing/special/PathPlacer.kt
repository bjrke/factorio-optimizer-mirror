package de.bjrke.factorio.optimizer.common.blueprint.placing.special

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.endName
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.markTunnels
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeSurface
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.adjacent
import de.bjrke.factorio.optimizer.common.core.model.directionBetween

/** A tile's role in a route: a plain belt, a tunnel input or a tunnel output, all facing. */
internal sealed interface PathRole {
    val direction: Direction
}

internal data class SurfaceRole(override val direction: Direction) : PathRole

internal data class TunnelInputRole(override val direction: Direction) : PathRole

internal data class TunnelOutputRole(override val direction: Direction) : PathRole

/**
 * Draws the transport entities of [material] along [nodes]; a jump becomes an underground run. A
 * tile carries one entity, so each gets its final role from [roles] before anything is placed. A
 * fluid material routes as pipes. A solid [load] replaces the full belt on every surface tile, so a
 * product lane keeps its half load.
 *
 * Returns the tiles a belt route uses twice; nothing is placed then and the caller has to route
 * again. A belt tile carries one entity with one facing, so a revisit silently drops the first
 * run's belt there (usually the belt a tunnel exit empties onto); a pipe connects on every side, so
 * a revisit is fine for a fluid.
 */
internal fun placePath(
    board: LayoutBoard,
    material: Material,
    nodes: List<Coordinate>,
    finalHeading: Direction? = null,
    load: LaneLoad? = null,
): Set<Coordinate> {
    val revisited = pathConflicts(board, material, nodes)
    if (revisited.isNotEmpty()) return revisited
    for ((tile, role) in roles(nodes, finalHeading)) {
        if (!board.isFree(tile)) continue
        when (role) {
            is SurfaceRole ->
                if (load != null && !board.isFluid(material)) {
                    board.placeBelt(tile, load, role.direction)
                } else {
                    board.placeSurface(material, tile, role.direction)
                }
            is TunnelInputRole -> board.placeEnd(material, tile, role.direction, Io.INPUT)
            is TunnelOutputRole -> board.placeEnd(material, tile, role.direction, Io.OUTPUT)
        }
    }
    board.markTunnels(nodes, board.endName(material))
    return emptySet()
}

/**
 * The tiles a belt route in [nodes] uses twice, so the caller can rule them out and search again. A
 * pipe route may reuse a tile: a pipe tile is a junction.
 */
internal fun pathConflicts(
    board: LayoutBoard,
    material: Material,
    nodes: List<Coordinate>,
): Set<Coordinate> =
    if (board.isFluid(material)) emptySet()
    else nodes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

/**
 * The role of every tile in [nodes], keyed by tile. A corner gets its outgoing direction (the last
 * occurrence wins), while a tunnel end beats a plain belt so a revisit never breaks a paired run. A
 * belt route with two different uses of one tile is rejected by [pathConflicts] before this runs.
 * [finalHeading] is the direction the last tile must face, so a feed can turn onto its target
 * regardless of the side the route arrived from.
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
