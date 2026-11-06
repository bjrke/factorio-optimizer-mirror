package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource

/**
 * The external inputs the plan [rows] consume but none of them produces, with the rate the imports
 * have to supply at [scale]. Derived from the rows that are actually built, not from the
 * optimizer's `included` map: a production the planner drops (for example a fluid maker whose solid
 * byproduct is not consumed) must still report its fluid as an import.
 *
 * A fluid produced by the same row is fed by that row's joined fluid lanes, so it is not external.
 * A fluid produced by another row is still imported, because rows never pipe fluids to each other.
 */
internal fun plannedSources(rows: List<PlannedRow>, scale: Double, logistics: LogisticsCalculator) =
    buildMap {
        val produced =
            rows.flatMap { row -> row.productions.flatMap { it.recipe.result.keys } }.toSet()
        for (row in rows) {
            val rowProducts = row.productions.flatMap { it.recipe.result.keys }.toSet()
            for (machine in row.machines) {
                for ((material, amount) in machine.recipe.ingredients) {
                    val products = if (logistics.isFluid(material)) rowProducts else produced
                    if (material !in products)
                        put(material, (this[material] ?: 0.0) + machine.inputPerMachine(amount))
                }
            }
        }
    }
    .map { (material, rate) ->
        PlannedSource(material, rate * scale, logistics.isFluid(material))
    }
    .sortedBy { it.material.name }
