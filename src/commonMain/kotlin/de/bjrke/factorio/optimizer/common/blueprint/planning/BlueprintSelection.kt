package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/**
 * The factory a plan starts from: the one producing the target [recipe]. Null means none does. The
 * target is always buildable as such, so this is not narrowed by [buildable].
 */
internal fun planTarget(
    factories: Collection<FactoryProduction>,
    recipe: Recipe,
): FactoryProduction? = factories.firstOrNull { it.recipe.name == recipe.name }

/**
 * The external inputs the plan [rows] consume but none of them produces, with the rate the imports
 * have to supply at [scale]. Derived from the rows that are actually built, not from the
 * optimizer's `included` map: a production the planner drops (for example a fluid maker whose solid
 * byproduct is not consumed) must still report its fluid as an import.
 *
 * A fluid produced by the same row is fed by that row's joined fluid lanes, so it is not external.
 * A fluid produced by another row is still imported, because rows never pipe fluids to each other.
 */
internal fun plannedSources(
    rows: List<PlannedRow>,
    scale: Double,
    logistics: LogisticsCalculator,
): List<PlannedSource> {
    val produced = rows.flatMap { row -> row.productions.flatMap { it.recipe.result.keys } }.toSet()
    val consumption = buildMap {
        for (row in rows) {
            val rowProducts = row.productions.flatMap { it.recipe.result.keys }.toSet()
            for (machine in row.machines) {
                for ((material, amount) in machine.recipe.ingredients) {
                    val suppliedByPlan =
                        if (logistics.isFluid(material)) material in rowProducts
                        else material in produced
                    if (suppliedByPlan) continue
                    put(material, (this[material] ?: 0.0) + machine.inputPerMachine(amount))
                }
            }
        }
    }
    return consumption
        .map { (material, rate) ->
            PlannedSource(material, rate * scale, logistics.isFluid(material))
        }
        .sortedBy { it.material.name }
}
