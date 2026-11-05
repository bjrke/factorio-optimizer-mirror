package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_COPIES
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.plannedMachines
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.math.ceil

/** The links one band may realize. */
private const val MAX_BAND_LINKS = 3

/** The rows a plan builds; a plain row repeats one recipe, a band interleaves several. */
internal data class DirectFeed(val rows: List<PlannedRow>)

// every link that could be direct-fed, in a deterministic order; a machine that takes or gives a
// fluid is left in (the band row gets the same fluid lane as a plain row), but a cyclic link is not
context(ctx: DirectFeedContext)
private fun candidates() = sequence {
    FeedLink(ctx).traverse()
}
    .filter { !it.hasCyclicIngredient && cycleRatio(it.ioRatio) != null }

// grow groups greedily: a link joins the group sharing a recipe, and is dropped again
// when the merged arrangement cannot be built, so it simply stays belted
private fun DirectFeedContext.groupLinks(): List<List<FeedLink>> =
    buildList<MutableList<FeedLink>> {
        for (link in candidates()) {
            val host =
                firstOrNull { group -> group.any { sharesRecipe(it, link) } }
                    ?: mutableListOf<FeedLink>().also { add(it) }
            if (host.size >= MAX_BAND_LINKS) continue
            host += link
            if (arrange(host) == null) host.remove(link)
        }
    }

/** Splits the plan into band rows, the target row and the remaining belted rows. */
internal fun DirectFeedContext.build(): DirectFeed {
    val bandPlans = groupLinks().filter { it.isNotEmpty() }.mapNotNull(::arrange)
    val bands = buildBands(bandPlans)
    val grouped = bandPlans.flatMap { it.feeds }.map { it.link }.toSet()
    val built = bands.flatMap { it.machines }.groupingBy { it }.eachCount()
    val onBelt = onBeltOf(bandPlans)
    val belted =
        included.values
            .sortedBy { it.recipe.name }
            .mapNotNull { belted(it, grouped, onBelt, built) }
    return DirectFeed(bands + targetRow(built) + belted)
}

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
// consumers
private fun onBeltOf(bandPlans: List<BandPlan>) = buildMap {
    for (plan in bandPlans) {
        val inserted = plan.feeds.mapTo(mutableSetOf()) { it.link.production }
        for ((recipe, machines) in plan.machines.groupingBy { it }.eachCount()) {
            if (recipe in inserted) continue
            this[recipe] = (this[recipe] ?: 0) + machines
        }
    }
}

/**
 * The rate a belted production still has to cover: every consumer outside the direct links, at the
 * rate the plan was sized for.
 */
private fun DirectFeedContext.beltedRate(recipe: Recipe, grouped: Set<FeedLink>) =
    included.values.sumOf { consumer ->
        consumer.recipe.ingredients.keys.sumOf { material ->
            if (countsForBelt(recipe, consumer, material, grouped))
                consumer.consumption(material) * scale
            else 0.0
        }
    }

/** Whether [consumer] still draws [material] over a belt rather than a band insertion. */
private fun DirectFeedContext.countsForBelt(
    recipe: Recipe,
    consumer: FactoryProduction,
    material: Material,
    grouped: Set<FeedLink>,
) =
    material in recipe.result.keys &&
        !logistics.isFluid(material) &&
        producers[material]?.singleOrNull()?.recipe == recipe &&
        grouped.none {
            it.material == material &&
                it.production.recipe == recipe &&
                it.consumer?.production?.recipe == consumer.recipe
        }

/**
 * One belted production per recipe: what the consumers that are not direct-fed still need, at the
 * rate the plan was sized for, less what a band's own output already puts on a belt. Sizing by
 * whole consumer machines would over-build: a complete foundry would demand four iron-gear machines
 * although the plan only ever needs a fraction of its rate, and that row would then have no
 * consumer at all.
 */
private fun DirectFeedContext.belted(
    production: FactoryProduction,
    grouped: Set<FeedLink>,
    onBelt: Map<FactoryProduction, Int>,
    built: Map<FactoryProduction, Int>,
): PlannedRow? {
    val recipe = production.recipe
    val rate = beltedRate(recipe, grouped)
    val perMachine = production.outputPerMachine()
    val demand =
        if (perMachine > 0.0)
            ceil(rate / perMachine - FEED_EPSILON).toInt() - (onBelt[production] ?: 0)
        else 0
    val leftover = (plannedMachines(production, scale) - (built[production] ?: 0)).coerceAtLeast(0)
    val machines = maxOf(demand, leftover).coerceIn(0, MAX_COPIES)
    return if (rate <= 0.0 || machines <= 0) null
    else {
        PlannedRow.copies(production, machines)
    }
}

/** The target always needs a row of its own when no band built it. */
private fun DirectFeedContext.targetRow(built: Map<FactoryProduction, Int>) =
    if ((built[target] ?: 0) > 0) emptyList()
    else listOf(PlannedRow.copies(target, plannedMachines(target, scale)))

/** Whether two links belong to one band: one is the other's consumer, or they share one. */
private fun sharesRecipe(first: FeedLink, second: FeedLink) =
    first.consumer == second || second.consumer == first || first.consumer == second.consumer

/**
 * Splits a plan's recipes into belted productions and direct-feed bands.
 *
 * A solid input whose producer stands in a small whole-machine cycle with its consumer
 * ([cycleRatio]) is inserted machine-to-machine instead of coming over a belt, so the band holds
 * both recipes interleaved. A material the consumers outside the bands still need — after what a
 * band's own output already puts on a belt — gets one belted production, sized at the rate the plan
 * was drawn for. Anything that does not fit a band keeps its plan machine count and stays belted.
 */
internal fun directFeed(
    target: Recipe,
    included: Map<Recipe, FactoryProduction>,
    scale: Double,
    logistics: LogisticsCalculator,
): DirectFeed {
    val targetProduction = included[target]
    return if (targetProduction == null) DirectFeed(emptyList())
    else DirectFeedContext(targetProduction, included, scale, logistics).build()
}
