package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.plan.inputPerMachine
import de.bjrke.factorio.optimizer.common.plan.outputPerMachine
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction

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

    /** How many inserters one machine needs to pull [items] out of its lane. */
    fun insertersFor(production: FactoryProduction, io: Io, items: Double) =
        logistics.beltInserterCount(
            when (io) {
                Io.INPUT -> inputPerMachine(production, items)
                Io.OUTPUT -> outputPerMachine(production, items)
            },
        )

    /**
     * A list of inserters per machine edge: one entry per inserter, `true` for a long one (the far
     * lane). [width] is how many columns the edge leaves free for inserters. The two lanes are
     * interleaved; when they do not fit, the counts are scaled down fairly.
     */
    fun <T> inserterPattern(amounts: Map<T, Number>, width: Int) = sequence {
        if (width <= 0) return@sequence
        val sum =
            amounts.values.sumOf {
                val v = it.toDouble()
                if (v > 0) v else 0.0
            }
        if (sum <= 0) return@sequence
        val ratio = (width / sum).coerceAtMost(1.0)

        val queue =
            amounts.entries.mapNotNullTo(ArrayDeque()) { (k, v) ->
                val v = v.toDouble()
                if (v > 0) k to v * ratio else null
            }
        queue.sortByDescending { (_, v) -> v }

        while (true) {
            val (k, v) = queue.removeFirstOrNull() ?: return@sequence
            when {
                v > 1 -> {
                    yield(k)
                    queue += k to v - 1
                }
                v > 0 -> {
                    yield(k)
                }
            }
        }
    }
        .take(width)
}
