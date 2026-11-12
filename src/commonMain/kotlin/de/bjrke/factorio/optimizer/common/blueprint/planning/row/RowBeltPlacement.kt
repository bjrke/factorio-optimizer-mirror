package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BeltGroup
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_ROW_BELTS
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.FAR_BOTTOM
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.FAR_TOP
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.NEAR_BOTTOM
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlacer.FixedBeltLocation.NEAR_TOP
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material

/** The belt rows one side of a row can hold: the north and south half of [MAX_ROW_BELTS]. */
private const val MAX_BELT_ROWS_PER_SIDE = MAX_ROW_BELTS / 2

/** The row's solid input and output rates and the lane capacity every belt decision reads. */
internal data class RowRates(
    val inputs: Map<Material, Double>,
    val outputs: Map<Material, Double>,
    val laneCapacity: Double,
)

/** One belt group and the fixed row beside the machines it was assigned to. */
internal data class BeltPlacement(val location: FixedBeltLocation, val group: BeltGroup)

/**
 * Assigns every belt group a row beside the machines. A topology-aware row puts a product on the
 * side of its consumer and an input on the side of its producer; each side holds at most two rows,
 * so an overflowing group shares a lane or moves to the other side. A row without producer/consumer
 * rows keeps the rate-ranked layout that stands for a single-row plan.
 */
internal fun assignRowBelts(
    planner: RowPlanner,
    unit: RowUnit,
    groups: List<BeltGroup>,
    rates: RowRates,
): List<BeltPlacement> {
    val used = groups.take(MAX_ROW_BELTS)
    return if (planner.isTopologyAware(unit)) topologyPlacements(planner, unit, used, rates)
    else legacyPlacements(used, rates.outputs)
}

private fun legacyPlacements(
    used: List<BeltGroup>,
    outputs: Map<Material, Double>,
): List<BeltPlacement> {
    val firstOutput = used.indexOfFirst { group -> group.materials.any { it in outputs } }
    val locations =
        when (firstOutput) {
            0 -> listOf(NEAR_BOTTOM, NEAR_TOP, FAR_TOP, FAR_BOTTOM)
            2 -> listOf(NEAR_TOP, NEAR_BOTTOM, FAR_BOTTOM, FAR_TOP)
            else -> listOf(NEAR_TOP, NEAR_BOTTOM, FAR_TOP, FAR_BOTTOM)
        }
    return used.zip(locations).map { (group, location) -> BeltPlacement(location, group) }
}

private fun topologyPlacements(
    planner: RowPlanner,
    unit: RowUnit,
    used: List<BeltGroup>,
    rates: RowRates,
): List<BeltPlacement> {
    val bySide = LineSide.entries.associateWith { mutableListOf<BeltGroup>() }
    val ordered =
        used.sortedWith(
            compareByDescending<BeltGroup> { group -> group.materials.any { it in rates.outputs } }
                .thenByDescending { group ->
                    group.materials.maxOf { rates.inputs[it] ?: rates.outputs[it] ?: 0.0 }
                },
        )
    for (group in ordered) {
        val output = group.materials.firstOrNull { it in rates.outputs }
        val preferred =
            if (output != null) planner.preferredOutputSide(unit, output)
            else
                group.materials
                    .maxByOrNull { rates.inputs[it] ?: 0.0 }
                    ?.let { planner.preferredInputSide(unit, it) }
        if (preferred != null && bySide.getValue(preferred).size < MAX_BELT_ROWS_PER_SIDE) {
            bySide.getValue(preferred) += group
            continue
        }
        // A full preferred side can still take the group by sharing a belt with one of its
        // single-input lanes, which keeps a fast input off the far side of the row.
        if (preferred != null && mergeInto(bySide.getValue(preferred), group, rates)) {
            continue
        }
        val available =
            LineSide.entries.filter { bySide.getValue(it).size < MAX_BELT_ROWS_PER_SIDE }
        val side = available.minByOrNull { bySide.getValue(it).size } ?: LineSide.SOUTH
        bySide.getValue(side) += group
    }
    return placementsBySide(bySide)
}

private fun placementsBySide(bySide: Map<LineSide, List<BeltGroup>>): List<BeltPlacement> =
    buildList {
        bySide.getValue(LineSide.NORTH).forEachIndexed { index, group ->
            add(BeltPlacement(if (index == 0) NEAR_TOP else FAR_TOP, group))
        }
        bySide.getValue(LineSide.SOUTH).forEachIndexed { index, group ->
            add(BeltPlacement(if (index == 0) NEAR_BOTTOM else FAR_BOTTOM, group))
        }
    }

/**
 * Pairs a spill-over input with a single-lane input already on its preferred side. Both rates must
 * fit one lane, so the mixed belt costs no throughput.
 */
private fun mergeInto(
    sideGroups: MutableList<BeltGroup>,
    group: BeltGroup,
    rates: RowRates,
): Boolean {
    if (group.materials.size != 1) return false
    val material = group.materials.single()
    if (material in rates.outputs) return false
    if ((rates.inputs[material] ?: 0.0) > rates.laneCapacity) return false
    val index = sideGroups.indexOfFirst { candidate ->
        candidate.materials.size == 1 &&
            candidate.materials.none { it in rates.outputs } &&
            (rates.inputs[candidate.materials.single()] ?: 0.0) <= rates.laneCapacity
    }
    if (index < 0) return false
    val candidate = sideGroups[index]
    val direction =
        when {
            candidate.direction == null -> group.direction ?: EAST
            group.direction == null -> candidate.direction
            candidate.direction == group.direction -> candidate.direction
            else -> EAST
        }
    sideGroups[index] = BeltGroup(candidate.materials + group.materials, direction)
    return true
}
