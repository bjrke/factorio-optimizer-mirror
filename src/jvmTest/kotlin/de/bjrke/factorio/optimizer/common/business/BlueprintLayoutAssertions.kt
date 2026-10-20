package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import kotlin.test.*

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
 * A material must reach a belt from one allocation only. `producerOf` in the layout has one belt
 * source per material, so a second belt-fed producer would be built with a product belt no delivery
 * ever uses — the "factory with an unconnected output belt" a band already covers.
 */
fun assertOneBeltSourcePerMaterial(plan: BlueprintPlan) {
    // a band inserts its link machine to machine, so that producer never puts material on a belt
    val inserted =
        plan.bands.flatMapTo(mutableSetOf()) { band ->
            band.links.map { link ->
                val machine = if (link.producerIsLeft) link.gap else link.gap + 1
                band.machines[machine] to link.material
            }
        }
    val sources =
        plan.recipes
            .withIndex()
            .flatMap { (index, allocation) ->
                allocation.recipe.result.keys.map { index to it }
            }
            .filterNot { it in inserted }
            .groupBy({ it.second }, { it.first })
    val doubled = sources.filterValues { it.size > 1 }

    assertEquals(emptyMap(), doubled, "material put on a belt by more than one allocation")
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

/**
 * A run shorter than the belt tier's reach must not still have a belt of its own material to
 * swallow on either end: `stretchUndergroundRuns` grows it as far as the straight run allows.
 */
fun assertUndergroundRunsAreStretched(
    entities: List<BlueprintEntity>,
    materials: Map<Coordinate, Material>,
) {
    val byTile = entities.associateBy { it.area.topLeft }
    fun range(name: String) =
        when {
            name.contains("turbo") -> 11
            name.contains("express") -> 9
            name.contains("fast") -> 7
            else -> 5
        }
    fun plainBelt(tile: Coordinate, direction: Direction, material: Material?) =
        byTile[tile]?.let {
            it.name.endsWith("transport-belt") && it.direction == direction
        } == true && materials[tile] == material

    fun underground(tile: Coordinate) = byTile[tile]?.name?.endsWith("underground-belt") == true

    val problems = mutableListOf<String>()
    for (input in entities.filter { it.name.endsWith("underground-belt") && it.type == Io.INPUT }) {
        // the import connector is a fixed marker pair, not a route to stretch
        if (entities.isImportConnector(input)) continue
        val direction = input.direction ?: continue
        val step = direction.step
        val limit = range(input.name)
        var reach = 0
        var output: BlueprintEntity? = null
        for (k in 2..limit) {
            val entity = byTile[input.area.topLeft + step * k] ?: continue
            // the partner carries the same material: another run may tunnel in the same column
            val sameRun =
                entity.type == Io.OUTPUT &&
                    entity.name == input.name &&
                    materials[entity.area.topLeft] == materials[input.area.topLeft]
            if (!sameRun) continue
            reach = k
            output = entity
            break
        }
        if (output == null || reach >= limit) continue
        val material = materials[input.area.topLeft]
        val back = input.area.topLeft - step
        // the new end must still join a surface belt, not the next run's underground end
        if (
            plainBelt(back, direction, material) &&
                back - step in byTile &&
                !underground(back - step)
        ) {
            problems += "input ${input.area.topLeft} can still grow back"
        }
        val forward = output.area.topLeft + step
        if (
            plainBelt(forward, direction, material) &&
                forward + step in byTile &&
                !underground(forward + step)
        ) {
            problems += "output ${output.area.topLeft} can still grow forward"
        }
    }
    assertEquals(emptyList(), problems.take(5))
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
