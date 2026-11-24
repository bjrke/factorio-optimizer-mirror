package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource

/**
 * The external inputs the plan [rows] consume but none of them produces. Derived from the rows that
 * are actually built, not from the optimizer's `included` map: a production the planner drops (for
 * example a fluid maker whose solid byproduct is not consumed) must still report its fluid as an
 * import.
 *
 * The rate is not reported: the laid-out rows already carry it, and the plan's scale is not applied
 * a second time. A fluid produced by the same row is fed by that row's joined fluid lanes, so it is
 * not external. A fluid produced by another row is still imported, because rows never pipe fluids
 * to each other.
 */
internal fun plannedSources(rows: List<PlannedRow>) = buildSet {
    // A solid only leaves a row through its belt materials; a material that is merely a recipe
    // result but is inserted internally is not a source for another row. A link covers only the
    // machine it reaches, so the other machines of a partially linked row still import it.
    val produced = rows.flatMap { row -> row.beltMaterials }.toSet()
    for (row in rows) {
        val rowProducts = row.productions.flatMap { it.recipe.result.keys }.toSet()
        val internal = row.internalInputs
        for ((index, machine) in row.machines.withIndex()) {
            for (material in machine.recipe.ingredients.keys) {
                val covered =
                    if (material.isFluid) material in rowProducts
                    else material in produced || (index to material) in internal
                if (!covered) add(material)
            }
        }
    }
}
    .sortedBy { it.name }
    .map { PlannedSource(it, it.isFluid) }
