package de.bjrke.factorio.optimizer.common.blueprint.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.model.manhattanDist
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Invariants of the entity grid a plan produces: overlap, inserters, tunnels and belt merges. */
@OptIn(ExperimentalKotlinTestApi::class)
fun assertNoOverlap(entities: List<BlueprintEntity>) {
    val seen = mutableMapOf<Coordinate, BlueprintEntity>()
    for (entity in entities) {
        for (tile in entity.area) {
            val existing = seen.put(tile, entity)
            assertNull(existing) { "${entity.name} and  ${existing?.name} overlaps at $tile" }
        }
    }
}

fun assertNoDanglingInserter(entities: List<BlueprintEntity>) {
    val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

    // an inserter always takes from the tile it faces; the long inserter reaches two tiles
    val dangling =
        entities
            .filter { it.name.endsWith("inserter") }
            .filter {
                val direction = it.direction ?: return@filter false
                val reach = if (it.name == "long-handed-inserter") 2 else 1
                val tile = it.area.topLeft + direction.step * reach
                tile !in occupied
            }
            .map { "${it.name}@${it.area.topLeft}" }
    assertEquals(emptyList(), dangling, "inserter facing nothing")
}

/**
 * A material must reach a belt from one planned recipe only. The layout has one belt source per
 * material, so a second belt-fed producer would be built with a product belt no delivery ever uses
 * — the "factory with an unconnected output belt" a band already covers.
 */
fun assertOneBeltSourcePerMaterial(plan: BlueprintPlan) {
    // a band inserts its link machine to machine, so that producer never puts material on a belt
    val sources =
        plan.rows
            .flatMap { row ->
                row.plannedRecipes.flatMap { planned ->
                    planned.recipe.result.keys
                        .filter { material -> row.exports(planned, material) }
                        .map { material -> planned to material }
                }
            }
            .groupBy({ it.second }, { it.first })
    val doubled = sources.filterValues { it.size > 1 }

    assertEquals(emptyMap(), doubled, "material put on a belt by more than one planned recipe")
}

/** An import connector: a label, chest or inserter sits right next to the buried run. */
fun List<BlueprintEntity>.isImportConnector(entity: BlueprintEntity) = any {
    (it.name == "constant-combinator" ||
        it.name.endsWith("chest") ||
        it.name.endsWith("inserter")) && it.area.topLeft.manhattanDist(entity.area.topLeft) == 1
}

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
        val step = (entity.direction ?: NORTH).step
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
 * A splitter output may not enter an underground belt directly: that feeds one side of the tunnel,
 * and a route can be left with an orphaned tunnel end when the two disagree.
 */
fun assertSplittersOnlyFeedSurface(entities: List<BlueprintEntity>) {
    val inputs =
        entities
            .filter { it.name.endsWith("underground-belt") && it.type == Io.INPUT }
            .map { it.area.topLeft }
            .toSet()
    val problems =
        entities
            .filter { it.name.endsWith("splitter") }
            .flatMap { splitter ->
                val direction = splitter.direction ?: return@flatMap emptyList()
                splitter.area.mapNotNull { tile ->
                    val downstream = tile + direction.step
                    if (downstream in inputs) "$tile -> $downstream" else null
                }
            }
    assertEquals(emptyList(), problems, "splitter output feeds underground belt")
}

/** A belt tile must not be fed by two belts of different materials (they would merge). */
fun assertNoMergingBelts(
    entities: List<BlueprintEntity>,
    materials: Map<Coordinate, Material>,
) {
    val belts =
        entities
            .filter {
                it.name.contains("transport-belt") &&
                    !it.name.contains("underground") &&
                    it.direction != null
            }
            .associate {
                it.area.topLeft to it.direction!!
            }

    val merges =
        belts
            .filter { (tile, _) ->
                val feeding = belts.filter { (from, direction) -> from + direction.step == tile }
                feeding.map { materials[it.key] }.distinct().size > 1
            }
            .keys
    assertEquals(emptySet(), merges, "belts fed by several materials")
}
