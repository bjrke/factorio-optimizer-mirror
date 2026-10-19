package de.bjrke.factorio.optimizer.common.business.fixtures

import de.bjrke.factorio.optimizer.common.business.*
import de.bjrke.factorio.optimizer.common.model.*

val ore = Material("iron-ore")
val coal = Material("coal")
val plate = Material("iron-plate")
val gear = Material("iron-gear-wheel")
val water = Material("water")
val lubricant = Material("lubricant")

fun recipe(
    name: String,
    category: Category,
    result: Map<Material, Double>,
    ingredients: Map<Material, Double> = emptyMap(),
) = Recipe(name, ingredients, result, 1.0, setOf(category))

fun planned(
    recipe: Recipe,
    material: Material,
    factory: Factory,
) =
    PlannedRecipe(
        production = FactoryProduction(material, recipe, factory, 1.0, 0),
        machines = 1.0,
    )

val logistics =
    LogisticsCalculator(
        testLogistics.copy(fluids = setOf(water, lubricant)),
        BeltTier.EXPRESS_TRANSPORT_BELT,
        InserterTier.FAST_INSERTER,
        lanes = 2,
        beltStack = 1,
        inserterStack = 1,
    )

val smeltRecipe = recipe("iron-plate", Category.CRAFTING, mapOf(plate to 1.0), mapOf(ore to 1.0))
val craftRecipe =
    recipe("iron-gear-wheel", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 2.0))

/** A gear machine fed by a plate machine, with iron ore imported from a belt. */
val basePlan =
    BlueprintPlan(
        target = gear,
        recipes =
            listOf(
                planned(craftRecipe, gear, Factory.ASSEMBLING_MACHINE_3),
                planned(smeltRecipe, plate, Factory.ASSEMBLING_MACHINE_1),
            ),
        sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
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

    fun neighbours(tile: Coordinate): List<Coordinate> = buildList {
        for (direction in Direction.entries) {
            val next = tile + direction.step
            if (next in tiles) add(next)
        }
        val mouth = grounds[tile]
        if (mouth != null) {
            val away = mouth.opposite().step
            for (distance in 1..10) {
                val next = tile + away * distance
                if (grounds[next] == mouth.opposite()) {
                    add(next)
                    break
                }
            }
        }
    }

    val seen = mutableSetOf<Coordinate>()
    val components = mutableListOf<Set<Coordinate>>()
    for (start in tiles.keys) {
        if (!seen.add(start)) continue
        val component = mutableSetOf<Coordinate>()
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            component += current
            for (next in neighbours(current)) if (seen.add(next)) queue += next
        }
        components += component
    }
    return components
}
