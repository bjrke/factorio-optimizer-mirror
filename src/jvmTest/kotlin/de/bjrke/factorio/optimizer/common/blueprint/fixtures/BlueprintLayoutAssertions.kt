package de.bjrke.factorio.optimizer.common.blueprint.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
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
                val reach = if (it.name == "long-handed-inserter") 2 else 1
                val tile = it.area.topLeft + it.direction.step * reach
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
                splitter.area.mapNotNull { tile ->
                    val downstream = tile + splitter.direction.step
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
            .filter { it.name.contains("transport-belt") }
            .filterNot { it.name.contains("underground") }
            .associate { it.area.topLeft to it.direction }

    val merges =
        belts
            .filter { (tile, _) ->
                val feeding = belts.filter { (from, direction) -> from + direction.step == tile }
                feeding.map { materials[it.key] }.distinct().size > 1
            }
            .keys
    assertEquals(emptySet(), merges, "belts fed by several materials")
}
