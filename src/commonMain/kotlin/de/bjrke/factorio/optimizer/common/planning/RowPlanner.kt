package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRecipe
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.plan.inputPerMachine
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One row to place, ordered by how deep it sits from the sources. */
internal data class RowUnit(val row: PlannedRow, val depth: Int) {
    /** What the row is built from, so two rows with the same depth keep a stable order. */
    val label: String
        get() = row.plannedRecipes.map { it.recipe.name }.sorted().joinToString("+")
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
            .map { row -> RowUnit(row, row.plannedRecipes.maxOf { depthOf(it, emptyList()) }) }
            .sortedWith(compareBy({ it.depth }, { it.label }))

    /**
     * The depth of [planned]: how many production steps away from the sources it is, used to order
     * the rows. A recipe already on [path] contributes zero instead of recursing forever, and a
     * path visits every recipe once, so the walk stays within the plan's recipe cap.
     */
    private fun depthOf(planned: PlannedRecipe, path: List<Recipe>): Int {
        if (planned.recipe in path) return 0
        val next = path + planned.recipe
        val producers =
            planned.recipe.ingredients.keys.mapNotNull { material ->
                plan.producerRecipeOf(material)?.takeIf { it != planned }?.let { material to it }
            }
        return if (producers.isEmpty()) 0
        else 1 + producers.maxOf { (_, producer) -> depthOf(producer, next) }
    }

    fun footprint(plannedRecipe: PlannedRecipe) = blueprintMachineFootprint(plannedRecipe.factory)

    /** How many inserters one machine needs to pull [materials] out of its lane. */
    fun insertersFor(plannedRecipe: PlannedRecipe, materials: List<Material>): Int {
        if (materials.isEmpty()) return 0
        val rate = materials.sumOf { inputPerMachine(plannedRecipe.production, it) }
        return maxOf(1, ceil(logistics.beltInserterCount(rate)).toInt())
    }

    /**
     * A list of inserters per machine edge: one entry per inserter, `true` for a long one (the far
     * lane). The two lanes are interleaved; when the edge is too short for both, the counts are
     * scaled down fairly.
     */
    fun inserterPattern(near: Int, far: Int, width: Int): List<Boolean> {
        var short = near
        var long = far
        // leave the machine's last column free: a pole can sit there and reach the machine row
        val edge = maxOf(1, width - 1)
        if (short + long > edge) {
            short = (short.toDouble() * edge / (short + long)).roundToInt().coerceIn(0, edge)
            long = edge - short
            if (near == 0) {
                short = 0
                long = edge
            }
            if (far == 0) {
                short = edge
                long = 0
            }
        }
        val pattern = mutableListOf<Boolean>()
        while (short > 0 || long > 0) {
            if (short > 0) {
                pattern += false
                short--
            }
            if (long > 0) {
                pattern += true
                long--
            }
        }
        return pattern
    }
}
