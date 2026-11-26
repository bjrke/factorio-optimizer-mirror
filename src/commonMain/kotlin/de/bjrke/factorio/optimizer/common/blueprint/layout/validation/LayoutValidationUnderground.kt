package de.bjrke.factorio.optimizer.common.blueprint.layout.validation

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIssue
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestLikeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.isSingleMaterial
import de.bjrke.factorio.optimizer.common.blueprint.layout.issue
import de.bjrke.factorio.optimizer.common.core.model.Io

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
    errors: MutableList<LayoutIssue>,
) {
    for (input in entities.filterIsInstance<UndergroundEntity>().filter { it.io == Io.INPUT }) {
        if (findUndergroundOutput(index, input) == null) {
            errors +=
                input.area.issue(
                    "unpaired ${input.material.name} underground at ${input.area.topLeft}",
                )
        }
        if (input is UndergroundBeltEntity) checkBeltFeed(index, input, errors)
    }
    for (output in entities.filterIsInstance<UndergroundEntity>().filter { it.io == Io.OUTPUT }) {
        if (output is UndergroundBeltEntity) checkBeltExit(index, output, errors)
    }
}

/**
 * A tunnel entrance is entered straight from a belt or from the output end of the previous run,
 * both facing into it.
 */
private fun checkBeltFeed(
    index: LayoutIndex,
    input: UndergroundBeltEntity,
    errors: MutableList<LayoutIssue>,
) {
    if (index.isConnectorEnd(input)) return
    val back = input.area.topLeft - input.direction.step
    val facesIn =
        when (val feeder = index.at(back)) {
            is UndergroundBeltEntity ->
                input.load.feedsInto(feeder.load) &&
                    feeder.io == Io.OUTPUT &&
                    feeder.direction == input.direction
            is ConveyorEntity ->
                input.load.feedsInto(feeder.load) && feeder.direction == input.direction
            is LoaderEntity ->
                input.isSingleMaterial(input.material) &&
                    feeder.material == input.material &&
                    feeder.io == Io.OUTPUT &&
                    feeder.direction == input.direction
            else -> false
        }
    if (!facesIn) {
        errors +=
            input.area.issue(
                "underground ${input.material.name} input at ${input.area.topLeft} " +
                    "is not fed straight from $back",
            )
    }
}

/**
 * A tunnel exit empties onto a belt of its material or into the input end of the next run, both
 * facing along the run.
 */
private fun checkBeltExit(
    index: LayoutIndex,
    output: UndergroundBeltEntity,
    errors: MutableList<LayoutIssue>,
) {
    if (index.isConnectorEnd(output)) return
    val ahead = output.area.topLeft + output.direction.step
    if (!acceptsExit(index, index.at(ahead), output)) {
        errors +=
            output.area.issue(
                "underground ${output.material.name} output at ${output.area.topLeft} " +
                    "leads nowhere at $ahead",
            )
    }
}

/** Whether [target] takes the exit: the next run's input, a belt along the run, or a loader. */
private fun acceptsExit(
    index: LayoutIndex,
    target: LayoutEntity?,
    output: UndergroundBeltEntity,
): Boolean =
    when (target) {
        is UndergroundBeltEntity ->
            target.load.feedsInto(output.load) &&
                target.io == Io.INPUT &&
                target.direction == output.direction
        is ConveyorEntity -> exitsInto(index, target, output)
        is LoaderEntity ->
            output.isSingleMaterial(output.material) &&
                target.material == output.material &&
                target.io == Io.INPUT
        else -> false
    }

/**
 * A belt the exit empties onto: straight keeps the lanes, and a perpendicular belt keeps them only
 * when it turns the run, not when it is already fed from behind and takes the exit as a side-load.
 */
private fun exitsInto(
    index: LayoutIndex,
    target: ConveyorEntity,
    output: UndergroundBeltEntity,
): Boolean =
    when {
        target.direction == output.direction -> target.load.takesStraightFrom(output.load)
        target.direction == output.direction.opposite() -> false
        target.load.takesStraightFrom(output.load) -> !fedStraight(index, target)
        else -> false
    }

/** Whether a belt of the same material already feeds [target] from its back. */
private fun fedStraight(index: LayoutIndex, target: ConveyorEntity): Boolean =
    when (val behind = index.at(target.area.topLeft - target.direction.step)) {
        is UndergroundBeltEntity -> behind.io == Io.OUTPUT && behind.direction == target.direction
        is ConveyorEntity -> behind.direction == target.direction
        else -> false
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
            partner.io == end.io || partner.load != end.load || partner.direction != end.direction
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
internal fun findUndergroundOutput(
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
    if ((input is PipeToGroundEntity) != (candidate is PipeToGroundEntity)) return false
    return if (input is UndergroundBeltEntity && candidate is UndergroundBeltEntity) {
        input.load == candidate.load
    } else {
        input.material == candidate.material
    }
}
