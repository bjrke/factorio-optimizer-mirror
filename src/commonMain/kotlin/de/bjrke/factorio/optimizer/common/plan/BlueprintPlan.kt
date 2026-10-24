package de.bjrke.factorio.optimizer.common.plan

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import kotlin.math.ceil

/** How many distinct recipes a single blueprint may build; the rest are imported. */
const val MAX_BLUEPRINT_RECIPES = 8

/** Upper bound on the machines of one recipe row, so a huge rate cannot explode the blueprint. */
internal const val MAX_COPIES = 9

/** Slack for a machine count that should be whole but carries floating-point noise. */
internal const val MACHINE_COUNT_EPSILON = 1e-6

/** The outcome of turning an optimization result into a buildable plan. */
sealed interface BlueprintPlanResult {
    data class Planned(val plan: BlueprintPlan) : BlueprintPlanResult

    /** Nothing (sensible) can be built, with a human-readable explanation. */
    data class Unavailable(val reason: String) : BlueprintPlanResult
}

/**
 * Whole machines a plan builds for [production] at [scale]: the count the optimizer sized, rounded
 * up, and never more than [MAX_COPIES]. A machine count that should be whole but carries floating
 * point noise must not round up.
 */
internal fun plannedMachines(production: FactoryProduction, scale: Double): Int =
    ceil(production.amount * scale - MACHINE_COUNT_EPSILON).toInt().coerceIn(1, MAX_COPIES)

/**
 * The reduced production plan a blueprint is laid out from: the [rows] to build, the external
 * [sources] they draw from, and the [scale] the whole plan is drawn at. A [scale] below 1 means the
 * layout had to shrink to fit its belts; [label] explains that and how many copies reach full rate.
 *
 * A [PlannedRecipe] belongs to exactly one row: a row is the occurrence, so the same recipe in two
 * rows is two independent values, not one identity that has to be tracked.
 */
data class BlueprintPlan(
    val target: Material,
    val rows: List<PlannedRow>,
    val sources: List<PlannedSource>,
    val scale: Double,
    val label: String,
) {
    /** Every planned recipe in plan order; a recipe may appear once per row. */
    val recipes: List<PlannedRecipe>
        get() = rows.flatMap { it.plannedRecipes }

    /** The direct-feed bands, for callers that only care about them. */
    val bands: List<PlannedRow>
        get() = rows.filter { it.links.isNotEmpty() }

    /**
     * The first row that puts [material] on a belt, or null when the plan imports it. A band
     * inserts its link material machine to machine, so that product is not a belt source; another
     * row making the same material still can be.
     */
    fun producerOf(material: Material): PlannedRow? = rows.firstOrNull {
        material in it.beltMaterials
    }

    /**
     * The planned recipe that produces [material] first, ignoring band inserts. Used to order rows
     * by depth; the belt source is [producerOf].
     */
    fun producerRecipeOf(material: Material): PlannedRecipe? = rows.firstNotNullOfOrNull { row ->
        row.plannedRecipes.firstOrNull { material in it.recipe.result.keys }
    }

    /** The materials the plan puts on a belt at all. */
    val beltMaterials: List<Material> by lazy {
        rows.flatMap { it.beltMaterials }.distinct()
    }
}

/** One recipe a row builds; the row's machine list decides how many copies it stands as. */
data class PlannedRecipe(val production: FactoryProduction) {
    val recipe: Recipe
        get() = production.recipe

    val factory: Factory
        get() = production.factory

    override fun toString() = recipe.name
}

/**
 * One row a plan builds: one entry per machine, left to right, plus the direct insertions between
 * them. A plain recipe row repeats the same planned recipe; a direct-feed band holds its recipe
 * sequence. The row owns its recipes, so a planned recipe needs no plan-wide identity.
 */
data class PlannedRow(
    /** The planned recipe of every machine, in machine order; the same recipe may repeat. */
    val machines: List<PlannedRecipe>,
    /** The direct insertions of a band, by the gap they sit in; empty for a plain row. */
    val links: List<PlannedLink> = emptyList(),
) {
    init {
        require(machines.isNotEmpty()) { "a row needs at least one machine" }
        require(links.isNotEmpty() || plannedRecipes.size == 1) {
            "a row without links builds one recipe"
        }
    }

    /** The planned recipes this row builds, once each, in machine order. */
    val plannedRecipes: List<PlannedRecipe>
        get() = machines.distinct()

    /** The products a machine of this row puts on a belt rather than into a neighbor. */
    val beltMaterials: List<Material>
        get() =
            plannedRecipes
                .flatMap { planned ->
                    planned.recipe.result.keys.filter { material -> exports(planned, material) }
                }
                .distinct()

    /** How many machines of [planned] this row builds. */
    fun machinesOf(planned: PlannedRecipe): Int = machines.count { it == planned }

    /** Whether [planned]'s [material] leaves this row over a belt rather than into a neighbor. */
    fun exports(planned: PlannedRecipe, material: Material): Boolean =
        material in planned.recipe.result.keys &&
            links.none { link -> link.material == material && producerOf(link) == planned }

    /** The first planned recipe of this row that puts [material] on a belt. */
    fun producerOf(material: Material): PlannedRecipe? = plannedRecipes.firstOrNull { planned ->
        exports(planned, material)
    }

    /** The planned recipe of the machine left (or right) of [link]'s gap. */
    fun producerOf(link: PlannedLink): PlannedRecipe =
        machines[if (link.producerIsLeft) link.gap else link.gap + 1]

    companion object {
        /** A plain row: [machines] copies of the same planned recipe. */
        fun copies(plannedRecipe: PlannedRecipe, machines: Int): PlannedRow =
            PlannedRow(List(machines) { plannedRecipe })
    }
}

/**
 * One material inserted straight from one machine of a band into the next one, sitting in the gap
 * after machine [gap].
 */
data class PlannedLink(
    val gap: Int,
    val material: Material,
    /** Items per second crossing the gap, for the inserter count. */
    val flow: Double,
    /** Whether the producer is the left machine, i.e. the inserter picks up on the west side. */
    val producerIsLeft: Boolean,
)

/** An external input: one belt/pipe lane of [material] the plan consumes but does not produce. */
data class PlannedSource(val material: Material, val rate: Double, val fluid: Boolean)
