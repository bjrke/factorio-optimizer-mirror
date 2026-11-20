package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.test.assertAll
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A blueprint's entities all sit at or east of column zero and at or south of row zero: the import
 * corridor and the north margin hold the layout origin there, so the string Factorio receives never
 * carries a negative coordinate.
 */
fun assertNonNegativeOrigin(entities: List<LayoutEntity>) =
    assertAll(
        {
            assertContains(
                0..Int.MAX_VALUE,
                entities.minOf { it.area.x },
                "a tile sits west of column zero",
            )
        },
        {
            assertContains(
                0..Int.MAX_VALUE,
                entities.minOf { it.area.y },
                "a tile sits north of row zero",
            )
        },
    )

/** Invariants of the entity grid a plan produces: overlap, inserters, tunnels and belt merges. */
@OptIn(ExperimentalKotlinTestApi::class)
fun assertNoOverlap(entities: List<LayoutEntity>) {
    val seen = mutableMapOf<Coordinate, LayoutEntity>()
    for (entity in entities) {
        for (tile in entity.area) {
            val existing = seen.put(tile, entity)
            assertNull(existing) { "${entity.name} and  ${existing?.name} overlaps at $tile" }
        }
    }
}

fun assertNoDanglingInserter(entities: List<LayoutEntity>) {
    val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

    // an inserter always takes from the tile it faces; the long inserter reaches two tiles
    val dangling =
        entities
            .filterIsInstance<InserterEntity>()
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
fun assertSplittersOnlyFeedSurface(entities: List<LayoutEntity>) {
    val inputs =
        entities
            .filterIsInstance<UndergroundBeltEntity>()
            .filter { it.io == Io.INPUT }
            .map { it.area.topLeft }
            .toSet()
    val problems =
        entities.filterIsInstance<SplitterEntity>().flatMap { splitter ->
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
fun assertNoMergingBelts(entities: List<LayoutEntity>) {
    val belts = entities.filterIsInstance<BeltEntity>().associateBy { it.area.topLeft }

    val merges =
        belts
            .filter { (tile, _) ->
                val fed =
                    belts.values
                        .filter { from -> from.area.topLeft + from.direction.step == tile }
                        .map { it.material }
                        .toSet()
                fed.size > 1 && belts.getValue(tile).load.materials != fed
            }
            .keys
    assertEquals(emptySet(), merges, "belts fed by several materials")
}
