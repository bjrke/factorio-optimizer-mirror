package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.compaction.LayoutIndex
import de.bjrke.factorio.optimizer.common.layout.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.UndergroundEntity
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.direction

/**
 * Every underground input needs an output of the same material, direction and kind, within the
 * run's reach: anything else would sever a belt lane or a fluid network.
 */
internal fun validateUndergrounds(
    index: LayoutIndex,
    entities: List<LayoutEntity>,
    errors: MutableList<String>,
) {
    for (input in entities.filterIsInstance<UndergroundEntity>().filter { it.io == Io.INPUT }) {
        if (findUndergroundOutput(index, input) == null) {
            errors += "unpaired ${input.material.name} underground at ${input.area.topLeft}"
        }
    }
}

/** The output [input] pairs with, looking along its direction within its run's reach. */
private fun findUndergroundOutput(
    index: LayoutIndex,
    input: UndergroundEntity,
): UndergroundEntity? {
    val step = input.direction.step
    for (distance in 2..undergroundReach(input)) {
        val candidate = index.at(input.area.topLeft + step * distance) as? UndergroundEntity
        if (candidate != null && pairsWith(input, candidate)) return candidate
    }
    return null
}

/**
 * How far an underground run reaches: pipes-to-ground span ten tiles, and among underground belts
 * the tier decides it (the faster the belt, the longer the run).
 */
internal fun undergroundReach(input: UndergroundEntity): Int =
    when {
        input is PipeToGroundEntity -> 10
        input.name.contains("turbo") -> 11
        input.name.contains("express") -> 9
        input.name.contains("fast") -> 7
        else -> 5
    }

/** Whether [candidate] is the partner of [input]: an output of its own run. */
private fun pairsWith(input: UndergroundEntity, candidate: UndergroundEntity): Boolean {
    if (candidate.io != Io.OUTPUT) return false
    if (candidate.direction != input.direction) return false
    if (candidate.material != input.material) return false
    return (input is PipeToGroundEntity) == (candidate is PipeToGroundEntity)
}
