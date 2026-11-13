package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Material
import kotlin.math.max

/** A mixed belt carries one material per lane; a row's product keeps a lane for itself. */
internal const val MAX_MATERIALS_PER_BELT = 2

/**
 * How many belt rows a row needs for its solid [inputs] and [outputs], and whether it can fit.
 *
 * Only inputs are paired. Every input whose rate fits one lane is paired greedily, so sharing a
 * belt costs no throughput. A single-recipe row ([allowOverCapacityPairs]) that would otherwise
 * lose a whole belt row (its product included) pairs the remaining lowest-rate inputs anyway and
 * accepts the smaller lane rate: a belt that cannot carry the full plan rate is still better than a
 * missing belt, and every machine of the row takes both materials. A multi-recipe band must fit
 * with capacity-safe pairs only; the band search rejects it otherwise and the recipes become their
 * own rows.
 *
 * The count is direction-agnostic: a pair may combine an import with a produced feed, because an
 * import anchors the merge to [EAST] and stays straight while the other feed turns.
 */
internal data class BeltRowBudget(
    val materialCount: Int,
    val pairsNeeded: Int,
    /** The pairs whose two inputs each fit one lane, so sharing costs no throughput. */
    val capacitySafePairs: Int,
    /** Every pair the inputs can form, whether or not the lane keeps its full rate. */
    val pairablePairs: Int,
    /** Whether an over-capacity pair may rescue a single-recipe row that cannot fit otherwise. */
    val allowOverCapacityPairs: Boolean,
) {
    /** The pairs greedy packing forms: every safe pair, plus over-capacity pairs only as needed. */
    private val pairs: Int
        get() {
            val over = if (allowOverCapacityPairs) pairablePairs - capacitySafePairs else 0
            return capacitySafePairs + minOf(over, max(0, pairsNeeded - capacitySafePairs))
        }

    val fits: Boolean
        get() = pairs >= pairsNeeded

    val rows: Int
        get() = materialCount - pairs
}

/** The belt-row budget of one row; a material both input and output keeps an output belt. */
internal fun beltRowBudget(
    inputs: Map<Material, Double>,
    outputs: Map<Material, Double>,
    pairCapacity: Double,
    allowOverCapacityPairs: Boolean,
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
        allowOverCapacityPairs = allowOverCapacityPairs,
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

/** One row's solid inputs and outputs with the lane capacity their packing may use. */
internal data class RowBeltRates(
    val inputs: Map<Material, Double>,
    val outputs: Map<Material, Double>,
    val pairCapacity: Double,
)

/** Everything one row's belt packing needs. */
internal data class BeltPackRequest(
    val rates: RowBeltRates,
    val directions: Map<Material, Direction>,
    val imports: Set<Material>,
    val allowOverCapacityPairs: Boolean,
)

/**
 * Packs a row's solid materials into belt rows. Capacity-safe inputs pair greedily, lowest rate
 * first, so a row that already fits still shares belts: one material per lane saves a belt row, the
 * far-lane long inserter and often a free machine column for a pole.
 *
 * A pair keeps its produced feeds' own directions; if one member is an import, the pair runs [EAST]
 * with the import first, because `BeltMerge` feeds its first material straight and makes the other
 * one turn. The import therefore never U-turns, and an import may share a belt with a feed from the
 * opposite direction. Two produced feeds from opposite directions stay separate: pairing them would
 * make one cross the row to reach a merge at the other end.
 *
 * An over-capacity input is paired only when the request allows it: a single-recipe row that would
 * otherwise lose a belt row may accept a lane below the plan rate, and every machine of that row
 * takes both materials. A multi-recipe band may not, so the band search rejects it and the recipes
 * become their own rows. That last-resort pair may combine any two remaining inputs.
 */
internal fun packRowBelts(request: BeltPackRequest): BeltPacking {
    val inputs = request.rates.inputs
    val outputs = request.rates.outputs
    val directions = request.directions
    val imports = request.imports
    val solids = (inputs.entries + outputs.entries).sortedByDescending { it.value }.map { it.key }
    val pairable = inputs.keys - outputs.keys
    fun byRate(materials: Iterable<Material>) =
        materials.sortedWith(compareBy({ inputs.getValue(it) }, { it.name }))
    val capacitySafe = byRate(pairable.filter { inputs.getValue(it) <= request.rates.pairCapacity })
    val needed = pairsNeeded(solids.size)
    val pairs = pairAll(capacitySafe, directions, imports, inputs).toMutableList()
    if (request.allowOverCapacityPairs && pairs.size < needed) {
        val paired = pairs.flatMapTo(mutableSetOf()) { it.materials }
        val remaining = byRate(pairable.filterNot { it in paired })
        pairs +=
            remaining
                .chunked(MAX_MATERIALS_PER_BELT)
                .filter { it.size == MAX_MATERIALS_PER_BELT }
                .take(needed - pairs.size)
                .map { pairGroup(it[0], it[1], directions, imports, inputs) }
    }
    return BeltPacking(groupMaterials(solids, pairs, inputs, outputs))
}

private fun pairAll(
    materials: List<Material>,
    directions: Map<Material, Direction>,
    imports: Set<Material>,
    inputs: Map<Material, Double>,
): List<BeltGroup> {
    val remaining = materials.toMutableList()
    val pairs = mutableListOf<BeltGroup>()
    while (remaining.isNotEmpty()) {
        val a = remaining.removeAt(0)
        val index = remaining.indexOfFirst { b ->
            directions.getValue(a) == directions.getValue(b) || a in imports || b in imports
        }
        if (index < 0) continue
        pairs += pairGroup(a, remaining.removeAt(index), directions, imports, inputs)
    }
    return pairs
}

/**
 * One mixed belt. An import is always the straight feeder of an [EAST] lane; an all-produced pair
 * runs in the bulk material's direction, with the bulk material straight, so the smaller feed
 * turns.
 */
private fun pairGroup(
    a: Material,
    b: Material,
    directions: Map<Material, Direction>,
    imports: Set<Material>,
    inputs: Map<Material, Double>,
): BeltGroup {
    val aImport = a in imports
    val bImport = b in imports
    val aDirection = directions.getValue(a)
    val bDirection = directions.getValue(b)
    val direction =
        when {
            aImport || bImport -> EAST
            aDirection == bDirection -> aDirection
            inputs.getValue(a) >= inputs.getValue(b) -> aDirection
            else -> bDirection
        }
    val straight =
        when {
            aImport != bImport -> if (aImport) a else b
            inputs.getValue(a) >= inputs.getValue(b) -> a
            else -> b
        }
    return BeltGroup(listOf(straight, if (straight == a) b else a), direction)
}

private fun groupMaterials(
    solids: List<Material>,
    pairs: List<BeltGroup>,
    inputs: Map<Material, Double>,
    outputs: Map<Material, Double>,
): List<BeltGroup> {
    val partner = mutableMapOf<Material, BeltGroup>()
    for (pair in pairs) {
        partner[pair.materials[0]] = pair
        partner[pair.materials[1]] = pair
    }
    val grouped = mutableSetOf<Material>()
    val groups = mutableListOf<BeltGroup>()
    for (material in solids) {
        if (material in grouped) continue
        val pair = partner[material]
        if (pair != null) {
            groups += pair
            grouped += pair.materials
        } else {
            groups += BeltGroup(listOf(material), null)
            grouped += material
        }
    }
    return groups.sortedByDescending { group ->
        group.materials.maxOf { material -> inputs[material] ?: outputs.getValue(material) }
    }
}

/**
 * The one-lane rate of the selected belt loaded by the selected inserter: the most a material may
 * carry when it shares a belt. A non-stacking inserter leaves the belt unstacked, so the belt
 * capacity research level does not raise this limit for it.
 */
internal fun LogisticsCalculator.oneLaneCapacity(): Double =
    beltThroughput(inserterBeltStack) / lanes
