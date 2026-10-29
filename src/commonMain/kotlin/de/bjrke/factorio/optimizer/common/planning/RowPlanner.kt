package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.plan.inputPerMachine
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One row to place, ordered by how deep it sits from the sources. */
internal data class RowUnit(val row: PlannedRow, val depth: Int) {
    /** What the row is built from, so two rows with the same depth keep a stable order. */
    val label: String
        get() = row.productions.map { it.recipe.name }.sorted().joinToString("+")
}

/**
 * The plan-level decisions a layout needs before it places anything: how deep every row sits from
 * the sources and the row order. Also the pure sizing helpers (`inserterPattern`) the row placer
 * uses, so no geometry leaks in.
 */
internal class RowPlanner(
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
) {

    /** The rows to place, top to bottom: the deepest consumers first, then by name. */
    val units: List<RowUnit> =
        plan.rows
            .map { row -> RowUnit(row, row.productions.maxOf { depthOf(it, emptyList()) }) }
            .sortedWith(compareBy({ it.depth }, { it.label }))

    /**
     * The depth of [planned]: how many production steps away from the sources it is, used to order
     * the rows. A recipe already on [path] contributes zero instead of recursing forever, and a
     * path visits every recipe once, so the walk stays within the plan's recipe cap.
     */
    private fun depthOf(planned: FactoryProduction, path: List<Recipe>): Int {
        if (planned.recipe in path) return 0
        val next = path + planned.recipe
        val producers =
            planned.recipe.ingredients.keys.mapNotNull { material ->
                plan.rows
                    .firstNotNullOfOrNull { row ->
                        row.productions.firstOrNull {
                            it != planned && material in it.recipe.result.keys
                        }
                    }
                    ?.let { material to it }
            }
        return if (producers.isEmpty()) 0
        else 1 + producers.maxOf { (_, producer) -> depthOf(producer, next) }
    }

    fun footprint(production: FactoryProduction) = blueprintMachineFootprint(production.factory)

    /** How many inserters one machine needs to pull [materials] out of its lane. */
    fun insertersFor(production: FactoryProduction, materials: List<Material>): Int {
        if (materials.isEmpty()) return 0
        val rate = materials.sumOf { inputPerMachine(production, it) }
        return maxOf(1, ceil(logistics.beltInserterCount(rate)).toInt())
    }

    /**
     * A list of inserters per machine edge: one entry per inserter, `true` for a long one (the far
     * lane). [width] is how many columns the edge leaves free for inserters. The two lanes are
     * interleaved; when they do not fit, the counts are scaled down fairly.
     */
    fun inserterPattern(near: Int, far: Int, width: Int) = buildList {
        var (short, long) =
            when {
                near + far <= width -> near to far
                near == 0 -> 0 to width
                far == 0 || width <= 1 -> width to 0
                else -> {
                    val short =
                        (near.toDouble() * width / (near + far)).roundToInt().coerceIn(1, width - 1)
                    short to width - short
                }
            }

        while (short > 0 || long > 0) {
            if (short-- > 0) {
                add(false)
            }
            if (long-- > 0) {
                add(true)
            }
        }
    }
}
