package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/**
 * The invariant checks a compacted layout has to keep. It is deliberately absolute rather than a
 * diff: the original layout satisfies these, so any corridor that keeps them cannot merge two
 * materials, split a fluid network, misalign an underground run or leave an inserter without a
 * source or target.
 */
internal fun validateLayout(entities: List<LayoutEntity>): List<String> {
    val errors = mutableListOf<String>()
    val tiles = HashMap<Coordinate, LayoutEntity>()
    for (entity in entities) {
        for (tile in entity.area) {
            val previous = tiles.put(tile, entity)
            if (previous != null && previous !== entity) errors += "overlap at $tile"
        }
    }
    val index = LayoutIndex(entities)

    for (entity in entities) {
        if (entity is ConveyorEntity) {
            val tile = entity.area.topLeft
            val ahead = index.at(tile + entity.direction.step)
            if (ahead is ConveyorEntity && ahead.material != entity.material) {
                errors +=
                    "${entity.material.name} runs into ${ahead.material.name} at " +
                        "${tile + entity.direction.step}"
            }
            for (side in fourNeighbours(tile)) {
                val other = index.at(side) as? ConveyorEntity ?: continue
                if (other.direction.step == tile - side && other.material != entity.material) {
                    errors += "foreign side-load into ${entity.material.name} at $tile"
                }
            }
        }
        if (entity is Plumbing) {
            val tile = entity.area.topLeft
            for (side in fourNeighbours(tile)) {
                val other = index.at(side) as? Plumbing ?: continue
                if (other.material != entity.material) {
                    errors +=
                        "fluids ${entity.material.name} and ${other.material.name} touch at $side"
                }
            }
        }
    }

    for (input in entities.filterIsInstance<UndergroundEntity>().filter { it.io == Io.INPUT }) {
        if (findUndergroundOutput(index, input) == null) {
            errors += "unpaired ${input.material.name} underground at ${input.area.topLeft}"
        }
    }

    for (inserter in entities.filterIsInstance<InserterEntity>()) {
        val reach = if (inserter.long) 2 else 1
        val source = index.at(inserter.area.topLeft + inserter.direction.step * reach)
        when {
            source == null ||
                (source !is MachineEntity &&
                    source !is TransportEntity &&
                    source !is ChestLikeEntity) ->
                errors += "inserter at ${inserter.area.topLeft} has no source"
            source is TransportEntity && source.material != inserter.material ->
                errors +=
                    "inserter at ${inserter.area.topLeft} draws ${source.material.name} " +
                        "instead of ${inserter.material.name}"
        }
        val drop = index.at(inserter.area.topLeft - inserter.direction.step)
        when {
            drop == null ||
                (drop !is MachineEntity && drop !is TransportEntity && drop !is ChestLikeEntity) ->
                errors += "inserter at ${inserter.area.topLeft} has no drop"
            drop is TransportEntity && drop.material != inserter.material ->
                errors +=
                    "inserter at ${inserter.area.topLeft} drops ${drop.material.name} " +
                        "instead of ${inserter.material.name}"
        }
    }
    return errors
}

private fun fourNeighbours(tile: Coordinate) =
    listOf(tile.north(), tile.east(), tile.south(), tile.west())

private fun findUndergroundOutput(
    index: LayoutIndex,
    input: UndergroundEntity,
): UndergroundEntity? {
    val step = input.direction.step
    val range =
        when {
            input is PipeToGroundEntity -> 10
            input.name.contains("turbo") -> 11
            input.name.contains("express") -> 9
            input.name.contains("fast") -> 7
            else -> 5
        }
    for (distance in 2..range) {
        val candidate = index.at(input.area.topLeft + step * distance)
        if (candidate !is UndergroundEntity || candidate.io != Io.OUTPUT) continue
        if (candidate.direction != input.direction) continue
        if (candidate.material != input.material) continue
        if ((input is PipeToGroundEntity) != (candidate is PipeToGroundEntity)) continue
        return candidate
    }
    return null
}
