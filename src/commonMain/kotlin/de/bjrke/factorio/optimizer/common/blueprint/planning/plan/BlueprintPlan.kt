package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.math.ceil

/** Upper bound on the machines of one recipe row, so a huge rate cannot explode the blueprint. */
internal const val MAX_COPIES = 9

/**
 * The solid belt rows one row can place: two above and two below its machines. A mixed input belt
 * carries two materials, one per lane, so a row can feed up to six imported inputs plus its
 * product. The row planner pairs only as many inputs as are needed to get back to this limit; a row
 * that still needs more rows reports a failure instead of losing a lane. Must match the belt
 * locations `RowPlacer.FixedBeltLocation` offers.
 */
internal const val MAX_ROW_BELTS = 4

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
internal fun plannedMachines(production: FactoryProduction, scale: Double = 1.0): Int =
    ceil(production.amount * scale - MACHINE_COUNT_EPSILON).toInt().coerceIn(1, MAX_COPIES)

/**
 * The reduced production plan a blueprint is laid out from: the [rows] to build, the external
 * [sources] they draw from, and the [scale] the whole plan is drawn at. A [scale] the planner had
 * to reduce to fit the product belts is reported by [label] as the covered share and the copies
 * that reach the requested rate.
 *
 * A [FactoryProduction] belongs to exactly one row: a row is the occurrence, so the same recipe in
 * two rows is two independent values, not one identity that has to be tracked.
 */
data class BlueprintPlan(
    val target: Material,
    val rows: List<PlannedRow>,
    /**
     * The external inputs the [rows] consume but none of them produces, derived from [rows] by
     * `plannedSources`. The layout places a source for exactly these materials, so the list cannot
     * drift from what the blueprint imports.
     */
    val sources: List<PlannedSource>,
    val scale: Double = 1.0,
    val label: String,
) {

    /**
     * The materials that enter the blueprint from outside, for the layout's import classification.
     */
    val sourceMaterials: Set<Material>
        get() = sources.map { it.material }.toSet()

    /**
     * The first row that puts [material] on a belt, or null when the plan imports it. A band
     * inserts its link material machine to machine, so that product is not a belt source; another
     * row making the same material still can be.
     */
    fun producerOf(material: Material) = rows.firstOrNull {
        material in it.beltMaterials
    }

    /** The materials the plan puts on a belt at all. */
    val beltMaterials by lazy {
        rows.flatMap { it.beltMaterials }.toSet()
    }
}

/**
 * One row a plan builds: one entry per machine, left to right, plus the direct insertions between
 * them. A plain recipe row repeats the same production; a direct-feed band holds its recipe
 * sequence. The row owns its productions, so a production needs no plan-wide identity.
 */
data class PlannedRow(
    /** The production of every machine, in machine order; the same recipe may repeat. */
    val machines: List<FactoryProduction>,
    /** The direct insertions of a band, by the gap they sit in; empty for a plain row. */
    val links: List<PlannedLink> = emptyList(),
) {
    init {
        require(machines.isNotEmpty()) { "a row needs at least one machine" }
        require(links.isNotEmpty() || productions.size == 1) {
            "a row without links builds one recipe"
        }
    }

    /** The productions this row builds, once each, in machine order. */
    val productions
        get() = machines.toSet()

    /**
     * The (machine index, material) pairs a link inserts directly into a machine. A link only
     * internalizes the consumer it reaches: another machine of the row may still take the same
     * material from a belt.
     */
    val internalInputs: Set<Pair<Int, Material>>
        get() =
            links.mapTo(mutableSetOf()) { link ->
                (if (link.producerIsLeft) link.gap + 1 else link.gap) to link.material
            }

    /** The link materials every consuming machine of this row takes through a link. */
    val fullyInternalInputs: Set<Material>
        get() {
            val incoming = internalInputs
            return links
                .mapTo(mutableSetOf()) { it.material }
                .filterTo(mutableSetOf()) { material ->
                    machines.withIndex().all { (index, machine) ->
                        material !in machine.recipe.ingredients || (index to material) in incoming
                    }
                }
        }

    /** The products a machine of this row puts on a belt rather than into a neighbor. */
    val beltMaterials
        get() =
            productions
                .flatMap { production ->
                    production.recipe.result.keys.filter { material ->
                        exports(production, material)
                    }
                }
                .toSet()

    /**
     * Whether [production]'s [material] leaves this row over a belt rather than into a neighbor.
     */
    fun exports(production: FactoryProduction, material: Material) =
        material in production.recipe.result.keys &&
            links.none { link -> link.material == material && producerOf(link) == production }

    /** The first production of this row that puts [material] on a belt. */
    fun producerOf(material: Material) = productions.firstOrNull {
        exports(it, material)
    }

    /** The production of the machine left (or right) of [link]'s gap. */
    fun producerOf(link: PlannedLink) =
        machines[if (link.producerIsLeft) link.gap else link.gap + 1]

    companion object {
        /** A plain row: [machines] copies of the same production. */
        fun copies(production: FactoryProduction, machines: Int) =
            PlannedRow(List(machines) { production })
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
