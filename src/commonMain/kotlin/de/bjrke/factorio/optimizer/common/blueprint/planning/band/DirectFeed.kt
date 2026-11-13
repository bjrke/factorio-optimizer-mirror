package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MACHINE_COUNT_EPSILON
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_COPIES
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.plannedMachines
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.math.ceil

/**
 * The rows a plan builds; a plain row repeats one recipe, a band interleaves several.
 *
 * [beltRates] is the rate each solid output has to carry over a belt at the drawn scale: the
 * consumers outside the direct links plus, for the target, its external delivery. It is the rate
 * the plan must deliver, not what the whole machines could make, because an overproducing machine
 * is throttled by its product belt.
 */
internal data class DirectFeed(
    val rows: List<PlannedRow>,
    val beltRates: Map<Material, Double>,
)

/** Splits the plan into band rows, the target row and the remaining belted rows. */
internal fun DirectFeedContext.build(forcedMaterials: Set<Material> = emptySet()): DirectFeed {
    val bandPlans = maximalGroups(forcedMaterials = forcedMaterials)
    val bands = buildBands(bandPlans)
    val grouped = bandPlans.flatMap { it.feeds }.map { it.link }.toSet()
    val built = bands.flatMap { it.machines }.groupingBy { it }.eachCount()
    val onBelt = onBeltOf(bandPlans)
    val demand = beltDemand(grouped)
    val belted =
        included.values
            .sortedBy { it.recipe.name }
            .mapNotNull { belted(it, demand.perRecipe, onBelt, built) }
    return DirectFeed(bands + targetRow(built) + beltedBands(belted), demand.perMaterial)
}

/**
 * Runs the same band search over the belted rows. A recipe that still has belt consumers can be
 * direct-fed from its producer even when the main band already inserts it: the belted rows are
 * copied with their whole-machine amount and planned at scale 1, so the copies are distinct from
 * the main band's productions and can sit in a row of their own.
 */
private fun DirectFeedContext.beltedBands(rows: List<PlannedRow>): List<PlannedRow> {
    if (rows.size < 2) return rows
    val productions = rows.associate { row ->
        val production = row.machines.first()
        production.recipe to production.copy(amount = row.machines.size.toDouble())
    }
    // the belted pass must still see the main plan's consumers, so a shared product is not
    // internalized just because its other consumer is in the main band
    val scope =
        included.values.map { production -> productions[production.recipe] ?: production }.toSet()
    val context =
        DirectFeedContext(
            productions.values.first(),
            productions,
            logistics,
            scale = 1.0,
            consumerScope = scope,
        )
    val plans = context.maximalGroups(context.linksBetween(productions.values))
    if (plans.isEmpty()) return rows
    val banded = plans.flatMap { plan -> plan.machines }.mapTo(mutableSetOf()) { it.recipe }
    return buildBands(plans) + rows.filterNot { it.machines.first().recipe in banded }
}

/** Every solid producer link between the [productions], for the belted band search. */
private fun DirectFeedContext.linksBetween(productions: Collection<FactoryProduction>) =
    productions
        .flatMap { consumer ->
            consumer.recipe.ingredients.keys
                .filterNot { logistics.isFluid(it) }
                .flatMap { material -> producers[material].orEmpty() }
                .filter { it != consumer }
                .map { feedLink(it, consumer) }
        }
        .distinct()

/** Builds one row per arranged band, with its machine sequence and the links between them. */
private fun buildBands(bandPlans: List<BandPlan>) = bandPlans.map { plan ->
    PlannedRow(
        machines = plan.machines,
        links =
            plan.feeds.map {
                PlannedLink(it.gap, it.link.material, it.flow, it.producerIsLeft)
            },
    )
}

// machines a band builds whose product still leaves it over a belt, i.e. the band's output as
// opposed to a link that is inserted machine to machine; those already supply the belted
// consumers. A producer with any link of its product cannot export that product: its row keeps no
// lane for a material it inserts, so its surplus stays in the band and the belted row must still
// cover the outside consumers.
private fun onBeltOf(bandPlans: List<BandPlan>) = buildMap {
    for (plan in bandPlans) {
        val inserted = plan.feeds.mapTo(mutableSetOf()) { it.link.producer.production }
        for ((recipe, machines) in plan.machines.groupingBy { it }.eachCount()) {
            if (recipe in inserted) continue
            this[recipe] = (this[recipe] ?: 0) + machines
        }
    }
}

/**
 * The items one machine of [production] puts on a belt. A recipe with one solid result is sized by
 * that result even when the plan labelled the production with one of its fluids, because a fluid
 * never rides a belt row. Multi-solid recipes keep the label's rate.
 */
private fun DirectFeedContext.beltOutputPerMachine(production: FactoryProduction): Double {
    val solid = production.recipe.result.keys.singleOrNull { !logistics.isFluid(it) }
    return production.outputPerMachine(solid ?: production.material)
}

/**
 * The rate every belted production still has to cover: each consumer outside the direct links, at
 * the rate the plan was sized for, grouped by the recipe that alone produces the material and by
 * material. The per-material map is the rate the product belt has to deliver; the target's external
 * delivery is added to it because the target is a sink rather than a consumer. A band's internal
 * material never gets a belt.
 */
private fun DirectFeedContext.beltDemand(grouped: Set<FeedLink>): BeltDemand {
    val groupedTriples =
        grouped
            .map {
                Triple(it.material, it.producer.production.recipe, it.consumer.production.recipe)
            }
            .toSet()
    val perRecipe = mutableMapOf<Recipe, Double>()
    val perMaterial = mutableMapOf<Material, Double>()
    for (consumer in included.values) {
        for (material in consumer.recipe.ingredients.keys) {
            if (logistics.isFluid(material)) continue
            val producer = producers[material]?.singleOrNull() ?: continue
            if (Triple(material, producer.recipe, consumer.recipe) in groupedTriples) continue
            val rate = consumer.consumption(material) * scale
            perRecipe[producer.recipe] = (perRecipe[producer.recipe] ?: 0.0) + rate
            perMaterial[material] = (perMaterial[material] ?: 0.0) + rate
        }
    }
    val targetMaterial = target.production.material
    if (!logistics.isFluid(targetMaterial)) {
        perMaterial[targetMaterial] = (perMaterial[targetMaterial] ?: 0.0) + scale
    }
    return BeltDemand(perRecipe, perMaterial)
}

/** The belted production rates by recipe and the delivered rates by material. */
private data class BeltDemand(
    val perRecipe: Map<Recipe, Double>,
    val perMaterial: Map<Material, Double>,
)

/**
 * One belted production per recipe: what the consumers that are not direct-fed still need, at the
 * rate the plan was sized for, less what a band's own surplus already puts on a belt. The target
 * row is [targetRow]'s job, so a target that another included recipe consumes cannot get a second
 * row here.
 */
private fun DirectFeedContext.belted(
    production: FactoryProduction,
    demand: Map<Recipe, Double>,
    onBelt: Map<FactoryProduction, Int>,
    built: Map<FactoryProduction, Int>,
): PlannedRow? {
    if (production == target.production) return null
    val recipe = production.recipe
    val rate = demand[recipe] ?: 0.0
    val perMachine = beltOutputPerMachine(production)
    val needed =
        if (perMachine > 0.0)
            ceil(rate / perMachine - MACHINE_COUNT_EPSILON).toInt() - (onBelt[production] ?: 0)
        else 0
    val leftover = (plannedMachines(production, scale) - (built[production] ?: 0)).coerceAtLeast(0)
    val machines = maxOf(needed, leftover).coerceIn(0, MAX_COPIES)
    return if (rate <= 0.0 || machines <= 0) null
    else {
        PlannedRow.copies(production, machines)
    }
}

/** The target always needs a row of its own when no band built it. */
private fun DirectFeedContext.targetRow(built: Map<FactoryProduction, Int>): List<PlannedRow> {
    val targetProduction = target.production
    return if ((built[targetProduction] ?: 0) > 0) emptyList()
    else listOf(PlannedRow.copies(targetProduction, plannedMachines(targetProduction, scale)))
}

/**
 * Splits a plan's recipes into belted productions and direct-feed bands.
 *
 * A solid input whose producer and consumer can be arranged in a small whole-machine pattern is
 * inserted machine-to-machine instead of coming over a belt, so the band holds both recipes
 * interleaved. The window is sized by the plan's actual rates: overfeed is allowed and a partly
 * used machine is fine, but no consumer may be underfed. A material the consumers outside the bands
 * still need — after what a band's own producers put on a belt — gets one belted production, sized
 * at the rate the plan was drawn for. Anything that does not fit a band keeps its plan machine
 * count and stays belted.
 *
 * [forcedMaterials] are the materials whose delivered belt rate exceeds one lane: their links seed
 * the band search first, so the plan direct-feeds them instead of thinning every row.
 */
internal fun directFeed(
    targetProduction: FactoryProduction,
    factories: Map<Recipe, FactoryProduction>,
    logistics: LogisticsCalculator,
    scale: Double = 1.0,
    forcedMaterials: Set<Material> = emptySet(),
) =
    DirectFeedContext(
            targetProduction,
            factories,
            logistics,
            scale,
        )
        .build(forcedMaterials)
