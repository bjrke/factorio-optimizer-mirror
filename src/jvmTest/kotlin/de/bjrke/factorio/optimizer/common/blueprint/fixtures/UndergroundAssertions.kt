package de.bjrke.factorio.optimizer.common.blueprint.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import kotlin.test.assertEquals

/**
 * Every underground belt must pair up (input with the output up to nine tiles ahead) and lead into
 * something: an exit is useless when the tile it faces is empty, and an entry is useless when
 * nothing feeds its back.
 */
fun assertConnectedUndergroundBelts(entities: List<BlueprintEntity>) {
    val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

    val underground = entities.filter { it.name.endsWith("underground-belt") }
    val inputs = underground.filter { it.type == Io.INPUT }.map { it.area.topLeft }.toSet()
    val outputs = underground.filter { it.type == Io.OUTPUT }.map { it.area.topLeft }.toSet()
    val problems = mutableListOf<String>()
    for (entity in underground) {
        if (entities.isImportConnector(entity)) continue
        val step = entity.direction.step
        val topLeft = entity.area.topLeft
        if (entity.type == Io.INPUT) {
            val exit = (1..9).any { outputs.contains(topLeft + step * it) }
            if (!exit) problems += "input $topLeft has no exit ahead"
            if ((topLeft - step) !in occupied) problems += "input $topLeft is not fed"
            // an exit empties onto a surface belt; an input right there cannot take it
            if ((topLeft - step) in outputs)
                problems += "input $topLeft is fed by an exit, not a belt"
        } else {
            val entry = (1..9).any { inputs.contains(topLeft - step * it) }
            if (!entry) problems += "exit $topLeft has no entry behind"
            if ((topLeft + step) !in occupied) problems += "exit $topLeft leads nowhere"
            // an exit feeds a surface belt, never the entrance of the next run directly
            if ((topLeft + step) in inputs) problems += "exit $topLeft leads straight into an input"
        }
    }
    assertEquals(emptyList(), problems)
}

/**
 * A routed tunnel end must connect through a belt of its material: the entrance is entered straight
 * from the belt behind it, and the exit empties onto a belt that can take the material. A
 * compaction corridor can remove the feeding turn while leaving the pair intact, so checking only
 * that the back tile is occupied is not enough.
 */
fun assertTunnelEndsAreFed(entities: List<BlueprintEntity>) {
    val byTile = entities.flatMap { entity -> entity.area.map { it to entity } }.toMap()
    val problems =
        entities
            .filter { it.name.endsWith("underground-belt") }
            .filterNot { entities.isImportConnector(it) }
            .mapNotNull { tunnelProblem(it, byTile) }
    assertEquals(emptyList(), problems)
}

private fun tunnelProblem(
    entity: BlueprintEntity,
    byTile: Map<Coordinate, BlueprintEntity>,
): String? {
    val direction = entity.direction
    val topLeft = entity.area.topLeft
    return if (entity.type == Io.INPUT) {
        inputProblem(topLeft, direction, byTile)
    } else {
        outputProblem(topLeft, direction, byTile)
    }
}

private fun inputProblem(
    topLeft: Coordinate,
    direction: Direction,
    byTile: Map<Coordinate, BlueprintEntity>,
): String? {
    val feeder = byTile[topLeft - direction.step] ?: return "input $topLeft is not fed"
    return when {
        feeder.name.isLoader() ->
            if (feeder.type == Io.OUTPUT && feeder.direction == direction) null
            else "input $topLeft is not fed by the loader behind it"
        !feeder.name.isConveyor() -> "input $topLeft is not fed by a belt"
        feeder.type != null -> "input $topLeft is fed by a tunnel end, not a belt"
        feeder.direction != direction -> "input $topLeft is fed from the side, not straight"
        else -> null
    }
}

private fun outputProblem(
    topLeft: Coordinate,
    direction: Direction,
    byTile: Map<Coordinate, BlueprintEntity>,
): String? {
    val target = byTile[topLeft + direction.step] ?: return "exit $topLeft leads nowhere"
    return when {
        target.name.isLoader() ->
            if (target.type == Io.INPUT) null else "exit $topLeft leads into a loader output"
        !target.name.isConveyor() -> "exit $topLeft leads onto no belt"
        target.type != null -> "exit $topLeft leads straight into a tunnel end"
        target.direction == direction.opposite() -> "exit $topLeft faces into a belt"
        else -> null
    }
}

/**
 * An import connector: the buried input/output pair is 2 tiles (label) or 4 tiles (chest) long with
 * the marker, chest or inserter halfway. Being merely adjacent to some connector is not enough — a
 * route entrance can lose its feeding belt and land beside another import's loader.
 */
private fun List<BlueprintEntity>.isImportConnector(entity: BlueprintEntity): Boolean {
    val direction = entity.direction
    val type = entity.type ?: return false
    val step = direction.step
    val sign = if (type == Io.INPUT) 1 else -1
    return listOf(2, 4).any { distance ->
        val partner =
            firstOrNull {
                it.area.contains(entity.area.topLeft + step * distance * sign)
            } ?: return@any false
        if (
            !partner.name.endsWith("underground-belt") ||
                partner.type == type ||
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

private fun String.isConveyor() =
    endsWith("transport-belt") || endsWith("splitter") || endsWith("underground-belt")

private fun String.isLoader() = endsWith("loader")
