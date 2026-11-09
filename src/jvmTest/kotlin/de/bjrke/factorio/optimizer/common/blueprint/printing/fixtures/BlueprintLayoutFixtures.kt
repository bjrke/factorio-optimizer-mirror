package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.neighbors
import de.bjrke.factorio.optimizer.common.core.util.connectedComponents
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

fun planned(
    recipe: Recipe,
    factory: Factory,
) = FactoryProduction(recipe.result.keys.single(), recipe, factory, 1.0, 0)

fun planned(
    recipe: Recipe,
    material: Material,
    factory: Factory,
) = FactoryProduction(material, recipe, factory, 1.0, 0)

val logistics =
    LogisticsCalculator(
        testLogistics.copy(fluids = setOf(water, lubricant, petroleum, sulfuricAcid)),
        LogisticsCalculatorSelection(belt = BeltTier.EXPRESS_TRANSPORT_BELT),
    )

/** A gear machine fed by a plate machine, with iron ore imported from a belt. */
val basePlan =
    BlueprintPlan(
        target = gear,
        rows =
            listOf(
                PlannedRow(listOf(planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3))),
                PlannedRow(listOf(planned(ironPlateRecipe, Factory.ASSEMBLING_MACHINE_1))),
            ),
        sources = listOf(PlannedSource(ironOre, 1.0, fluid = false)),
        scale = 1.0,
        label = "iron-gear-wheel",
    )

/**
 * The connected pipe networks of [entities]. Pipes connect by touching. A pipe-to-ground carries
 * only the side its surface pipe attaches on (the blueprint has no input/output `type` for pipes),
 * so two ends of an underground run face away from each other: the partner of an end sits one step
 * in the direction *opposite* its facing, up to the pipe's reach, and itself faces that same way.
 */
fun plumbingComponents(entities: List<BlueprintEntity>): List<Set<Coordinate>> {
    val tiles =
        entities
            .filter { it.name == "pipe" || it.name == "pipe-to-ground" }
            .associateBy { it.area.topLeft }
    val grounds =
        tiles.filterValues { it.name == "pipe-to-ground" }.mapValues { it.value.direction }
    return connectedComponents(tiles.keys) { tile -> plumbingNeighbors(tile, tiles, grounds) }
}

private fun plumbingNeighbors(
    tile: Coordinate,
    tiles: Map<Coordinate, BlueprintEntity>,
    grounds: Map<Coordinate, Direction?>,
) = buildList {
    val mouth = grounds[tile]
    for (neighbor in tile.neighbors) {
        if (neighbor !in tiles) continue
        // a plain pipe joins every side, a pipe-to-ground only its mouth; the neighbour must join
        // back, so two different fluids may stand beside a foreign end without merging
        if (mouth != null && mouth.step != neighbor - tile) continue
        val otherMouth = grounds[neighbor]
        if (otherMouth != null && otherMouth.step != tile - neighbor) continue
        add(neighbor)
    }
    if (mouth == null) return@buildList
    val away = mouth.opposite().step
    for (distance in 1..10) {
        val next = tile + away * distance
        if (grounds[next] == mouth.opposite()) {
            add(next)
            break
        }
    }
}
