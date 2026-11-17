package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import kotlin.test.assertEquals

/**
 * Every underground belt must pair up (input with the output up to nine tiles ahead) and lead into
 * something: an exit is useless when the tile it faces is empty, and an entry is useless when
 * nothing feeds its back. An output may feed the next run's input directly.
 */
fun assertConnectedUndergroundBelts(entities: List<LayoutEntity>) {
    val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

    val underground = entities.filterIsInstance<UndergroundBeltEntity>()
    val inputs = underground.filter { it.io == Io.INPUT }.map { it.area.topLeft }.toSet()
    val outputs = underground.filter { it.io == Io.OUTPUT }.map { it.area.topLeft }.toSet()
    val problems = mutableListOf<String>()
    for (entity in underground) {
        if (entities.isImportConnector(entity)) continue
        val step = entity.direction.step
        val topLeft = entity.area.topLeft
        if (entity.io == Io.INPUT) {
            val exit = (1..9).any { outputs.contains(topLeft + step * it) }
            if (!exit) problems += "input $topLeft has no exit ahead"
            if ((topLeft - step) !in occupied) problems += "input $topLeft is not fed"
        } else {
            val entry = (1..9).any { inputs.contains(topLeft - step * it) }
            if (!entry) problems += "exit $topLeft has no entry behind"
            if ((topLeft + step) !in occupied) problems += "exit $topLeft leads nowhere"
        }
    }
    assertEquals(emptyList(), problems)
}

/**
 * A routed tunnel end must connect through a belt of its material or the previous run: the entrance
 * is entered straight from the belt behind it or from an output end facing it, and the exit empties
 * onto a belt that can take the material or into the next run's input. A compaction corridor can
 * remove the feeding turn while leaving the pair intact, so checking only that the back tile is
 * occupied is not enough.
 */
fun assertTunnelEndsAreFed(entities: List<LayoutEntity>) {
    val byTile = entities.flatMap { entity -> entity.area.map { it to entity } }.toMap()
    val problems =
        entities
            .filterIsInstance<UndergroundBeltEntity>()
            .filterNot { entities.isImportConnector(it) }
            .mapNotNull { tunnelProblem(it, byTile) }
    assertEquals(emptyList(), problems)
}

private fun tunnelProblem(
    entity: UndergroundBeltEntity,
    byTile: Map<Coordinate, LayoutEntity>,
): String? {
    val direction = entity.direction
    val topLeft = entity.area.topLeft
    return if (entity.io == Io.INPUT) {
        inputProblem(topLeft, direction, byTile)
    } else {
        outputProblem(topLeft, direction, byTile)
    }
}

private fun inputProblem(
    topLeft: Coordinate,
    direction: Direction,
    byTile: Map<Coordinate, LayoutEntity>,
): String? {
    val feeder = byTile[topLeft - direction.step] ?: return "input $topLeft is not fed"
    return when {
        feeder is LoaderEntity ->
            if (feeder.io == Io.OUTPUT && feeder.direction == direction) null
            else "input $topLeft is not fed by the loader behind it"
        feeder is UndergroundBeltEntity ->
            if (feeder.io == Io.OUTPUT && feeder.direction == direction) null
            else "input $topLeft is not fed by the run behind it"
        feeder !is ConveyorEntity -> "input $topLeft is not fed by a belt"
        feeder.direction != direction -> "input $topLeft is fed from the side, not straight"
        else -> null
    }
}

private fun outputProblem(
    topLeft: Coordinate,
    direction: Direction,
    byTile: Map<Coordinate, LayoutEntity>,
): String? {
    val target = byTile[topLeft + direction.step] ?: return "exit $topLeft leads nowhere"
    return when {
        target is LoaderEntity ->
            if (target.io == Io.INPUT) null else "exit $topLeft leads into a loader output"
        target is UndergroundBeltEntity ->
            if (target.io == Io.INPUT && target.direction == direction) null
            else "exit $topLeft leads into the run's output end"
        target !is ConveyorEntity -> "exit $topLeft leads onto no belt"
        target.direction == direction.opposite() -> "exit $topLeft faces into a belt"
        else -> null
    }
}

/**
 * An import connector: the buried input/output pair is 2 tiles (label) or 4 tiles (chest) long with
 * the marker, chest or inserter halfway. Being merely adjacent to some connector is not enough — a
 * route entrance can lose its feeding belt and land beside another import's loader.
 */
private fun List<LayoutEntity>.isImportConnector(entity: UndergroundBeltEntity): Boolean {
    val direction = entity.direction
    val type = entity.io
    val step = direction.step
    val sign = if (type == Io.INPUT) 1 else -1
    return listOf(2, 4).any { distance ->
        val partner =
            firstOrNull { it.area.contains(entity.area.topLeft + step * distance * sign) }
                ?: return@any false
        if (
            partner !is UndergroundBeltEntity ||
                partner.io == type ||
                partner.direction != direction
        ) {
            return@any false
        }
        val middle = firstOrNull {
            it.area.contains(entity.area.topLeft + step * (distance / 2) * sign)
        }
        middle != null && middle.name.isConnectorFurniture()
    }
}

private fun String.isConnectorFurniture() =
    this == "constant-combinator" || endsWith("chest") || endsWith("inserter") || endsWith("loader")
