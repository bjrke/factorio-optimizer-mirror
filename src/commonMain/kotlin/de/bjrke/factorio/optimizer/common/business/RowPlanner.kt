package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One row to place: a direct-feed band, or a single allocation built on its own. */
internal class RowUnit(
    /** What the row is built from, so two rows with the same depth keep a stable order. */
    val label: String,
    val depth: Int,
    val band: PlannedBand?,
    val allocation: Int,
)

/**
 * The plan-level decisions a layout needs before it places anything: which allocation produces each
 * material, how deep every allocation sits from the sources, and the row order. Also the pure
 * sizing helpers (`copiesOf`, `inserterPattern`) the row placer uses, so no geometry leaks in.
 */
internal class RowPlanner(
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
) {

    /** The first belted allocation that makes a material; a band's internal material has none. */
    val producerOf: Map<Material, Int>

    private val depth = mutableMapOf<Int, Int>()

    /** The rows to place, top to bottom: the deepest consumers first, then by name. */
    val units: List<RowUnit>

    init {
        producerOf = producerMap()
        computeDepth()
        units = rowUnits()
    }

    /**
     * The producer of every material, ignoring the ones a band moves machine to machine: a band
     * inserts its intermediate material straight into the next machine, so its producer allocation
     * must not act as a belt source.
     */
    private fun producerMap(): Map<Material, Int> {
        val internal = mutableSetOf<Pair<Int, Material>>()
        for (band in plan.bands) {
            for (link in band.links) {
                val machine = if (link.producerIsLeft) link.gap else link.gap + 1
                internal += band.machines[machine] to link.material
            }
        }
        val producers = mutableMapOf<Material, Int>()
        for ((index, recipe) in plan.recipes.withIndex()) {
            for (material in recipe.recipe.result.keys) {
                if ((index to material) in internal) continue
                producers.getOrPut(material) { index }
            }
        }
        return producers
    }

    /**
     * The depth of every allocation: how many production steps away from the sources it is, used to
     * order the rows. Band links count like any other input, so a band still sits below what feeds
     * it, and a cycle cannot recurse forever.
     */
    private fun computeDepth() {
        val producer = mutableMapOf<Material, Int>()
        for ((index, recipe) in plan.recipes.withIndex()) {
            for (material in recipe.recipe.result.keys) producer.getOrPut(material) { index }
        }
        val visiting = mutableSetOf<Recipe>()
        fun depthOf(index: Int): Int {
            depth[index]?.let {
                return it
            }
            val allocation = plan.recipes[index]
            if (!visiting.add(allocation.recipe)) return 0
            val inputs =
                allocation.recipe.ingredients.keys.filter {
                    it in producer && producer.getValue(it) != index
                }
            val value =
                if (inputs.isEmpty()) 0 else 1 + inputs.maxOf { depthOf(producer.getValue(it)) }
            visiting.remove(allocation.recipe)
            depth[index] = value
            return value
        }
        for (index in plan.recipes.indices) depthOf(index)
    }

    private fun rowUnits(): List<RowUnit> {
        val inBand = mutableSetOf<Int>()
        for (band in plan.bands) inBand += band.machines
        val rows = mutableListOf<RowUnit>()
        for (band in plan.bands) {
            val members = band.machines.distinct()
            rows +=
                RowUnit(
                    label = members.map { plan.recipes[it].recipe.name }.sorted().joinToString("+"),
                    depth = members.maxOf { depth[it] ?: 0 },
                    band = band,
                    allocation = members.first(),
                )
        }
        for (index in plan.recipes.indices) {
            if (index in inBand) continue
            rows +=
                RowUnit(
                    label = plan.recipes[index].recipe.name,
                    depth = depth[index] ?: 0,
                    band = null,
                    allocation = index,
                )
        }
        return rows.sortedWith(compareBy({ it.depth }, { it.label }))
    }

    fun copiesOf(recipe: PlannedRecipe): Int =
        // a machine count that should be whole but carries floating point noise must not round up
        ceil(recipe.machines - 1e-6).toInt().coerceIn(1, MAX_COPIES)

    fun footprint(recipe: PlannedRecipe) = blueprintMachineFootprint(recipe.factory.identifier)

    /** How many inserters one machine needs to pull [materials] out of its lane. */
    fun insertersFor(recipe: PlannedRecipe, materials: List<Material>): Int {
        if (materials.isEmpty()) return 0
        val rate = materials.sumOf { inputPerMachine(recipe.production, it) }
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
