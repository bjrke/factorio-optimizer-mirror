package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.math.max

/** A mixed belt carries one material per lane; a row's product keeps a lane for itself. */
internal const val MAX_MATERIALS_PER_BELT = 2

/**
 * How many belt rows a row needs for its solid [inputs] and [outputs], and whether it can fit.
 *
 * Only inputs are paired. Pairing is preferred between materials whose plan rate fits one lane, so
 * sharing a belt costs no throughput. A row that would otherwise lose a whole belt row (its product
 * included) pairs the remaining lowest-rate inputs anyway and accepts the smaller lane rate: a belt
 * that cannot carry the full plan rate is still better than a missing belt. The count is
 * direction-agnostic: placement pairs same-direction feeds first and may pair across directions
 * (routing one feed around), so this budget is the optimistic upper bound both the row planner and
 * the band search use.
 */
internal data class BeltRowBudget(
    val materialCount: Int,
    val pairsNeeded: Int,
    /** The pairs whose two inputs each fit one lane, so sharing costs no throughput. */
    val capacitySafePairs: Int,
    /** Every pair the inputs can form, whether or not the lane keeps its full rate. */
    val pairablePairs: Int,
) {
    val fits: Boolean
        get() = pairablePairs >= pairsNeeded

    val rows: Int
        get() = materialCount - minOf(pairsNeeded, pairablePairs)
}

/** The belt-row budget of one row; a material both input and output keeps an output belt. */
internal fun beltRowBudget(
    inputs: Map<Material, Double>,
    outputs: Map<Material, Double>,
    pairCapacity: Double,
): BeltRowBudget {
    val materials = (inputs.keys + outputs.keys).toSet()
    val pairs = max(0, materials.size - MAX_ROW_BELTS)
    val pairable = inputs.keys - outputs.keys
    val capacitySafe = pairable.count { material -> inputs.getValue(material) <= pairCapacity }
    return BeltRowBudget(
        materialCount = materials.size,
        pairsNeeded = pairs,
        capacitySafePairs = capacitySafe / MAX_MATERIALS_PER_BELT,
        pairablePairs = pairable.size / MAX_MATERIALS_PER_BELT,
    )
}

/** The number of mixed pairs a row has to form to get back to [MAX_ROW_BELTS] rows. */
internal fun pairsNeeded(materialCount: Int) = max(0, materialCount - MAX_ROW_BELTS)

/** One belt row: a single material, or a pair with the direction its merge feeds from. */
internal class BeltGroup(val materials: List<Material>, val direction: Direction?)

/** The rows a plan places, and whether every material fits the four belt rows. */
internal data class BeltPacking(val groups: List<BeltGroup>) {
    val fits: Boolean
        get() = groups.size <= MAX_ROW_BELTS
}

/**
 * Packs a row's [solids] into one belt row each, pairing just enough inputs to get back to
 * [MAX_ROW_BELTS]. Pairs form within one feed [directions] group first, lowest rate first; a
 * remaining pair may straddle directions and then runs east, where the west margin has room to
 * route the other feed around. Inputs whose rate fits one lane pair first, so a fast material only
 * shares a belt when the row cannot fit otherwise.
 */
internal fun packRowBelts(
    solids: List<Material>,
    inputs: Map<Material, Double>,
    outputs: Map<Material, Double>,
    directions: Map<Material, Direction>,
    pairCapacity: Double,
): BeltPacking {
    val needed = pairsNeeded(solids.size)
    val pairable = inputs.keys - outputs.keys
    fun byRate(materials: Iterable<Material>) =
        materials.sortedWith(compareBy({ inputs.getValue(it) }, { it.name }))
    val capacitySafe = byRate(pairable.filter { inputs.getValue(it) <= pairCapacity })
    val overCapacity = byRate(pairable.filter { inputs.getValue(it) > pairCapacity })
    val pairs = mutableListOf<BeltGroup>()
    pairs += choosePairs(capacitySafe, directions, needed)
    pairs += choosePairs(overCapacity, directions, needed - pairs.size)
    // an odd count leaves one material per list; those may still pair across the capacity split
    val paired = pairs.flatMapTo(mutableSetOf()) { it.materials }
    pairs +=
        choosePairs(byRate(pairable.filterNot { it in paired }), directions, needed - pairs.size)
    return BeltPacking(groupMaterials(solids, pairs, inputs, outputs))
}

private fun choosePairs(
    pairable: List<Material>,
    directions: Map<Material, Direction>,
    needed: Int,
): List<BeltGroup> {
    if (needed <= 0) return emptyList()
    val pairs = mutableListOf<BeltGroup>()
    val used = mutableSetOf<Material>()

    for (direction in listOf(EAST, WEST)) {
        val group = pairable.filter { material ->
            material !in used && directions.getValue(material) == direction
        }
        for (index in 0 until group.size - 1 step 2) {
            if (pairs.size == needed) break
            pairs += BeltGroup(listOf(group[index], group[index + 1]), direction)
            used += group[index]
            used += group[index + 1]
        }
    }

    val remaining = pairable.filterNot { it in used }
    var index = 0
    while (pairs.size < needed && index + 1 < remaining.size) {
        pairs += BeltGroup(listOf(remaining[index], remaining[index + 1]), EAST)
        index += 2
    }
    return pairs
}

private fun groupMaterials(
    solids: List<Material>,
    pairs: List<BeltGroup>,
    inputs: Map<Material, Double>,
    outputs: Map<Material, Double>,
): List<BeltGroup> {
    val partner = mutableMapOf<Material, Pair<Material, Direction>>()
    for (pair in pairs) {
        val direction = pair.direction ?: continue
        partner[pair.materials[0]] = pair.materials[1] to direction
        partner[pair.materials[1]] = pair.materials[0] to direction
    }
    val grouped = mutableSetOf<Material>()
    val groups = mutableListOf<BeltGroup>()
    for (material in solids) {
        if (material in grouped) continue
        val mate = partner[material]
        if (mate != null) {
            groups += BeltGroup(listOf(material, mate.first), mate.second)
            grouped += material
            grouped += mate.first
        } else {
            groups += BeltGroup(listOf(material), null)
            grouped += material
        }
    }
    return groups.sortedByDescending { group ->
        group.materials.maxOf { material -> inputs[material] ?: outputs.getValue(material) }
    }
}

/** The material rates of [machines], keyed by material and split out of the fluids. */
internal fun solidRates(
    machines: List<FactoryProduction>,
    rates: (FactoryProduction) -> List<Pair<Material, Double>>,
    logistics: LogisticsCalculator,
): Map<Material, Double> =
    machines
        .flatMap(rates)
        .filterNot { (material, _) -> logistics.isFluid(material) }
        .groupingBy { (material, _) -> material }
        .fold(0.0) { acc, (_, rate) -> acc + rate }

internal fun rowInputs(machines: List<FactoryProduction>, logistics: LogisticsCalculator) =
    solidRates(machines, ::inputRates, logistics)

internal fun rowOutputs(machines: List<FactoryProduction>, logistics: LogisticsCalculator) =
    solidRates(machines, ::outputRates, logistics)

internal fun outputRates(production: FactoryProduction) =
    production.recipe.result.entries.map { (material, amount) ->
        material to production.outputPerMachine(amount)
    }

internal fun inputRates(production: FactoryProduction) =
    production.recipe.ingredients.entries.map { (material, amount) ->
        material to production.inputPerMachine(amount)
    }

/** The one-lane rate of the selected belt: the most a material may carry when it shares a belt. */
internal fun LogisticsCalculator.oneLaneCapacity(): Double = beltThroughput(beltStack) / lanes
