package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
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
 * A material must reach a belt from one production only. The layout has one belt source per
 * material, so a second belt-fed producer would be built with a product belt no delivery ever uses
 * — the "factory with an unconnected output belt" a band already covers.
 */
fun assertOneBeltSourcePerMaterial(plan: BlueprintPlan) {
    // a band inserts its link machine to machine, so that producer never puts material on a belt
    val doubled =
        plan.rows
            .flatMap { row ->
                row.productions.flatMap { planned ->
                    planned.recipe.result.keys
                        .filter { material -> row.exports(planned, material) }
                        .map { material -> material to planned }
                }
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }

    assertEquals(emptyMap(), doubled, "material put on a belt by more than one production")
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

/**
 * A belt tile must not be fed by belts of several materials unless the tile's own lanes carry them
 * all: a mixed input belt merges one lane per material on purpose, while any other merge would pour
 * two materials into one lane or discard one.
 */
fun assertNoMergingBelts(
    entities: List<BlueprintEntity>,
    materials: Map<Coordinate, Material>,
    lanes: Map<Coordinate, LaneLoad>,
) {
    val belts =
        entities
            .filter { it.name.contains("transport-belt") }
            .filterNot { it.name.contains("underground") }
            .associate { it.area.topLeft to it.direction }

    val merges =
        belts
            .filter { (tile, _) ->
                val fed =
                    belts
                        .filter { (from, direction) -> from + direction.step == tile }
                        .mapNotNull { materials[it.key] }
                        .toSet()
                fed.size > 1 && lanes[tile]?.materials != fed
            }
            .keys
    assertEquals(emptySet(), merges, "belts fed by several materials")
}
