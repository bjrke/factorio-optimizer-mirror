package de.bjrke.factorio.optimizer.common.layout.validation

import de.bjrke.factorio.optimizer.common.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.layout.entity.ChestLikeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundEntity
import de.bjrke.factorio.optimizer.common.model.Io

/**
 * Every underground input needs an output of the same material, direction and kind, within the
 * run's reach: anything else would sever a belt lane or a fluid network. A routed belt run also
 * needs a belt on the tile behind an input and on the tile ahead of an output; a compaction
 * corridor that removes that belt would leave a tunnel that looks paired but carries nothing.
 * Connector ends are exempt: their marker/chest/inserter is the point the player feeds.
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
        if (input is UndergroundBeltEntity) checkBeltFeed(index, input, errors)
    }
    for (output in entities.filterIsInstance<UndergroundEntity>().filter { it.io == Io.OUTPUT }) {
        if (output is UndergroundBeltEntity) checkBeltExit(index, output, errors)
    }
}

/** A tunnel entrance is only entered straight from a belt that faces into it. */
private fun checkBeltFeed(
    index: LayoutIndex,
    input: UndergroundBeltEntity,
    errors: MutableList<String>,
) {
    if (index.isConnectorEnd(input)) return
    val back = input.area.topLeft - input.direction.step
    val feeder = index.at(back)
    val facesIn =
        when (feeder) {
            is UndergroundEntity -> false
            is ConveyorEntity ->
                feeder.material == input.material && feeder.direction == input.direction
            is LoaderEntity ->
                feeder.material == input.material &&
                    feeder.io == Io.OUTPUT &&
                    feeder.direction == input.direction
            else -> false
        }
    if (!facesIn) {
        errors +=
            "underground ${input.material.name} input at ${input.area.topLeft} " +
                "is not fed straight from $back"
    }
}

/** A tunnel exit empties onto a belt of its material, never into the next tunnel end. */
private fun checkBeltExit(
    index: LayoutIndex,
    output: UndergroundBeltEntity,
    errors: MutableList<String>,
) {
    if (index.isConnectorEnd(output)) return
    val ahead = output.area.topLeft + output.direction.step
    val target = index.at(ahead)
    val accepts =
        when (target) {
            is UndergroundEntity -> false
            is ConveyorEntity ->
                target.material == output.material &&
                    target.direction != output.direction.opposite()
            is LoaderEntity -> target.material == output.material && target.io == Io.INPUT
            else -> false
        }
    if (!accepts) {
        errors +=
            "underground ${output.material.name} output at ${output.area.topLeft} " +
                "leads nowhere at $ahead"
    }
}

/**
 * A connector end is one half of the two-tile (label) or four-tile (chest) connector: its partner
 * lies 2 or 4 tiles along the run and the marker/inserter/chest sits halfway between them. Merely
 * being next to some connector is not enough: a routed input can lose its feeding belt and end up
 * beside another import's loader.
 */
private fun LayoutIndex.isConnectorEnd(end: UndergroundBeltEntity): Boolean {
    val step = end.direction.step
    val sign = if (end.io == Io.INPUT) 1 else -1
    for (distance in listOf(2, 4)) {
        val partner =
            at(end.area.topLeft + step * distance * sign) as? UndergroundBeltEntity ?: continue
        if (
            partner.io == end.io ||
                partner.material != end.material ||
                partner.direction != end.direction
        ) {
            continue
        }
        val middle = end.area.topLeft + step * (distance / 2) * sign
        if (at(middle).isConnectorFurniture()) return true
    }
    return false
}

private fun LayoutEntity?.isConnectorFurniture() =
    when (this) {
        is MarkerEntity,
        is ChestLikeEntity,
        is InserterEntity,
        is LoaderEntity -> true
        else -> false
    }

/** The output [input] pairs with, looking along its direction within its run's reach. */
private fun findUndergroundOutput(
    index: LayoutIndex,
    input: UndergroundEntity,
): UndergroundEntity? {
    val step = input.direction.step
    // a pipe-to-ground may surface in the very next tile: that one buried tile is the walkway the
    // player crosses the line on. Underground belts keep one tile between their ends.
    val nearest = if (input is PipeToGroundEntity) 1 else 2
    for (distance in nearest..undergroundReach(input)) {
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
