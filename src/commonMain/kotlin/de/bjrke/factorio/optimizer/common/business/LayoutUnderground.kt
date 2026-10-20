package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/** The fluid the pipe or pipe-to-ground at [coordinate] carries, if any. */
internal fun LayoutBoard.fluidAt(coordinate: Coordinate) =
    (entityAt(coordinate) as? Plumbing)?.material

/** The output end of the underground run [input] feeds, within the material's reach, if any. */
internal fun LayoutBoard.undergroundOutputOf(input: UndergroundEntity): UndergroundEntity? {
    val step = input.direction.step
    for (reach in 2..range(input.material)) {
        val entity = entityAt(input.area.topLeft + step * reach)
        if (entity is UndergroundEntity && entity.isOutputOf(input)) return entity
    }
    return null
}

private fun UndergroundEntity.isOutputOf(input: UndergroundEntity) =
    io == Io.OUTPUT && direction == input.direction && material == input.material

/** How far [material] may travel underground: a pipe's own reach or the belt tier's. */
internal fun LayoutBoard.range(material: Material) =
    if (isFluid(material)) PIPE_UNDERGROUND_RANGE else undergroundRange

/** The name of an underground end of [material]: a pipe-to-ground or the belt tier's. */
internal fun LayoutBoard.endName(material: Material) =
    if (isFluid(material)) names.pipeToGround else names.undergroundBelt

/** Whether a run of [type] along [step] already passes under [coordinate]. */
internal fun LayoutBoard.hasTunnel(coordinate: Coordinate, step: Coordinate, type: String) =
    tunnels.has(coordinate, step, type)

/** Records the underground run from [from] to [to] on every tile it passes under. */
internal fun LayoutBoard.markTunnel(from: Coordinate, to: Coordinate, type: String) =
    tunnels.mark(from, to, type)

/** Records every underground run of [nodes], so a later route sees the tiles under it. */
internal fun LayoutBoard.markTunnels(nodes: List<Coordinate>, type: String) {
    nodes.zipWithNext().forEach { (from, to) ->
        if (!from.adjacent(to)) markTunnel(from, to, type)
    }
}

/** Re-marks every underground run after the grid was rebuilt. */
internal fun LayoutBoard.rebuildTunnels() {
    tunnels.clear()
    for (input in placed.filterIsInstance<UndergroundEntity>().filter { it.io == Io.INPUT }) {
        val output = undergroundOutputOf(input) ?: continue
        markTunnel(input.area.topLeft, output.area.topLeft, endName(input.material))
    }
}
