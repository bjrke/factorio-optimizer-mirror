package de.bjrke.factorio.optimizer.common.prototype

import de.bjrke.factorio.optimizer.common.model.Material

/** The parsed prototype data: every known material and recipe plus the machine and belt stats. */
data class ParsedRecipe(
    val materials: List<Material>,
    val recipes: List<Recipe>,
    /**
     * Crafting categories, speed and built-in productivity of the machines, derived from the
     * `data.raw` dump and keyed by the machine prototype name. [Factory] carries curated fallbacks
     * for synthetic tests that build a [ParsedRecipe] without a dump.
     */
    val machineStats: Map<String, MachineStats> = emptyMap(),
    /** Belt/inserter/pump throughput and the set of fluid materials, derived from the dump. */
    val logistics: Logistics = Logistics(),
)

/** The crafting-relevant stats of a machine prototype. */
data class MachineStats(
    val categories: Set<Category>,
    val speed: Double,
    val productivity: Double,
)

/** A recipe: its inputs, outputs, crafting time and the machine categories that can run it. */
data class Recipe(
    val name: String,
    val ingredients: Map<Material, Double>,
    val result: Map<Material, Double>,
    val time: Double,
    val categories: Set<Category>,
    val subgroup: Subgroup = Subgroup(),
    val surfaceConditions: List<SurfaceCondition> = emptyList(),
)

/** The recipe-book subgroup a recipe belongs to, interned per name. */
@ConsistentCopyVisibility
data class Subgroup private constructor(val name: String) {

    companion object {
        private val cache = mutableMapOf<String, Subgroup>()

        operator fun invoke(name: String = "no subgroup") = cache.getOrPut(name) { Subgroup(name) }
    }
}
