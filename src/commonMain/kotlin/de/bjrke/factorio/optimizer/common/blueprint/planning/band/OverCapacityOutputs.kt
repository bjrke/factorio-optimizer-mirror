package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.CAPACITY_EPSILON
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.beltStackFor
import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * The solid outputs whose delivered rate exceeds their producer's one-lane capacity at the drawn
 * scale, mapped to the share of a lane they need. A direct-output machine stacks its own product,
 * so its lane capacity follows the requested belt stack even when the selected inserter cannot
 * stack; any other product is stacked only by a stacking inserter.
 */
internal fun DirectFeedContext.overCapacityOutputs(
    rows: List<PlannedRow>,
    beltRates: Map<Material, Double>,
) = buildMap {
    val onBelt = rows.flatMapTo(mutableSetOf()) { row -> row.beltMaterials }
    for ((material, demand) in beltRates) {
        if (material !in onBelt) continue
        val capacity = laneCapacity(rows, material, logistics)
        if (
            demand > capacity + CAPACITY_EPSILON &&
                builtRate(rows, material) > capacity + CAPACITY_EPSILON
        ) {
            put(material, demand / capacity)
        }
    }
}

/** The one-lane rate of [material]'s product belt, following its producer's stacking. */
private fun laneCapacity(
    rows: List<PlannedRow>,
    material: Material,
    logistics: LogisticsCalculator,
): Double {
    val producer = rows.firstNotNullOfOrNull { row -> row.producerOf(material) }
    val stacks =
        if (producer != null && producer.factory.outputsDirectly) {
            producer.factory.stacksOnBelt
        } else {
            logistics.rates.stacksOnBelt
        }
    return logistics.beltThroughput(logistics.beltStackFor(stacks)) / logistics.lanes
}

/**
 * The rate the built machines can put on a belt for [material] before the belt throttles them. A
 * row that cannot even make the demanded rate is underbuilt, not over capacity, so it does not
 * shrink the plan; once it can make the demand, the demand decides.
 */
private fun builtRate(rows: List<PlannedRow>, material: Material): Double = rows.sumOf { row ->
    val production = row.producerOf(material) ?: return@sumOf 0.0
    row.machines.count { it == production } * production.outputPerMachine(material)
}
