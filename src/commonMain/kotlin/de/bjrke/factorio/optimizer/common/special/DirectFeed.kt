package de.bjrke.factorio.optimizer.common.special

import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.plan.FEED_EPSILON
import de.bjrke.factorio.optimizer.common.plan.FeedLink
import de.bjrke.factorio.optimizer.common.plan.MAX_COPIES
import de.bjrke.factorio.optimizer.common.plan.PlannedBand
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.PlannedRecipe
import de.bjrke.factorio.optimizer.common.plan.cycleRatio
import de.bjrke.factorio.optimizer.common.plan.inputPerMachine
import de.bjrke.factorio.optimizer.common.plan.outputPerMachine
import de.bjrke.factorio.optimizer.common.plan.plannedMachines
import de.bjrke.factorio.optimizer.common.planning.BandSearch
import de.bjrke.factorio.optimizer.common.service.LogisticsCalculator
import kotlin.math.ceil

/** The recipes one band may hold; a wider group is left to a later increment. */
private const val MAX_BAND_RECIPES = 3

/** The links one band may realize. */
private const val MAX_BAND_LINKS = 3

/** The allocations a plan builds and the direct-feed bands they form. */
internal class DirectFeed(val recipes: List<PlannedRecipe>, val bands: List<PlannedBand>)

private class DirectFeedBuilder(
    val target: Recipe,
    val included: List<FactoryProduction>,
    val scale: Double,
    val logistics: LogisticsCalculator,
) {
    private val production = included.associateBy { it.recipe }
    private val need = included.associate { it.recipe to plannedMachines(it, scale) }
    // the single in-plan producer of each material
    private val producers =
        included
            .flatMap { f -> f.recipe.result.keys.map { it to f } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (m, p) -> p.singleOrNull().let { m to it } }
            .toMap()

    // the band cycle search, on the plan's per-recipe data
    private val search = BandSearch(production, need, logistics)

    // every link that could be direct-fed, in a deterministic order
    private val candidates = sequence {
        for (consumer in included) {
            // a machine that takes a fluid needs its port on the outside of the row, where no
            // interface inserter sits; the fluid feed is not reworked for bands yet, so those
            // recipes stay belted
            if (consumer.recipe.ingredients.keys.any { logistics.isFluid(it) }) continue
            for (material in consumer.recipe.ingredients.keys.sortedBy { it.name }) {
                linkOf(consumer, material)?.let { yield(it) }
            }
        }
    }

    /**
     * The link from [material]'s in-plan producer to [consumer], when the producer is a different
     * solid recipe and the whole-machine ratio is a small cycle.
     */
    private fun linkOf(consumer: FactoryProduction, material: Material): FeedLink? {
        val producer = producers[material] ?: return null
        val usable =
            !logistics.isFluid(material) &&
                producer.recipe !== consumer.recipe &&
                producer.recipe.ingredients.keys.none { logistics.isFluid(it) } &&
                cycleRatio(
                    inputPerMachine(consumer, material) / outputPerMachine(producer, material),
                ) != null
        return if (usable) FeedLink(material, producer.recipe, consumer.recipe) else null
    }

    // grow groups greedily: a link joins the group sharing a recipe, and is dropped again
    // when the merged arrangement cannot be built, so it simply stays belted
    private val builders = groupLinks()

    private fun groupLinks(): List<MutableList<FeedLink>> {
        val groups = mutableListOf<MutableList<FeedLink>>()
        for (link in candidates) {
            val host =
                groups.firstOrNull { group -> group.any { sharesRecipe(it, link) } }
                    ?: mutableListOf<FeedLink>().also { groups += it }
            if (host.size >= MAX_BAND_LINKS) continue
            host += link
            val recipes = host.recipes()
            if (recipes.size > MAX_BAND_RECIPES) {
                host.remove(link)
                continue
            }
            if (search.arrange(recipes, host) == null) host.remove(link)
        }
        return groups
    }

    // the arrangements that survived, as bands
    private val bandPlans = builders.mapNotNull { links ->
        if (links.isEmpty()) return@mapNotNull null
        search.arrange(links.recipes(), links)
    }

    fun build(): DirectFeed {
        val recipes = mutableListOf<PlannedRecipe>()
        val bands = buildBands(recipes)
        val grouped = bandPlans.flatMap { it.feeds }.map { it.link }.toSet()
        val built = builtOf(recipes)
        val onBelt = onBeltOf()
        val belted =
            included
                .sortedBy { it.recipe.name }
                .mapNotNull {
                    belted(it, grouped, onBelt, built)
                }
        return DirectFeed(recipes + targetRow(built) + belted, bands)
    }

    /**
     * Adds one allocation per recipe a band builds and returns the bands with those allocation
     * indices.
     */
    private fun buildBands(recipes: MutableList<PlannedRecipe>): List<PlannedBand> =
        bandPlans.map { plan ->
            val allocationOf = buildMap {
                for (recipe in plan.machines.toSet()) {
                    this[recipe] = recipes.size
                    recipes +=
                        PlannedRecipe(
                            production = production.getValue(recipe),
                            machines = plan.machines.count { it == recipe }.toDouble(),
                        )
                }
            }

            PlannedBand(
                plan.machines.map { allocationOf.getValue(it) },
                plan.feeds.map {
                    PlannedLink(it.gap, it.link.material, it.flow, it.producerIsLeft)
                },
            )
        }

    private fun builtOf(recipes: List<PlannedRecipe>) =
        recipes
            .groupBy { it.recipe }
            .mapValues { (_, plannedRecipes) -> plannedRecipes.sumOf { it.machines.toInt() } }

    // machines a band builds whose product still leaves it over a belt, i.e. the band's output as
    // opposed to a link that is inserted machine to machine; those already supply the belted
    // consumers
    private fun onBeltOf() = buildMap {
        for (plan in bandPlans) {
            val inserted = plan.feeds.mapTo(mutableSetOf()) { it.link.producer }
            for ((recipe, machines) in plan.machines.groupingBy { it }.eachCount()) {
                if (recipe in inserted) continue
                this[recipe] = (this[recipe] ?: 0) + machines
            }
        }
    }

    /**
     * The rate a belted allocation still has to cover: every consumer outside the direct links, at
     * the rate the plan was sized for.
     */
    private fun beltedRate(recipe: Recipe, grouped: Set<FeedLink>) = included.sumOf { consumer ->
        consumer.recipe.ingredients.keys.sumOf { material ->
            if (countsForBelt(recipe, consumer, material, grouped)) {
                consumer.consumption(material) * scale
            } else {
                0.0
            }
        }
    }

    /** Whether [consumer] still draws [material] over a belt rather than a band insertion. */
    private fun countsForBelt(
        recipe: Recipe,
        consumer: FactoryProduction,
        material: Material,
        grouped: Set<FeedLink>,
    ) =
        material in recipe.result.keys &&
            !logistics.isFluid(material) &&
            producers[material]?.recipe === recipe &&
            FeedLink(material, recipe, consumer.recipe) !in grouped

    /**
     * One belted allocation per recipe: what the consumers that are not direct-fed still need, at
     * the rate the plan was sized for, less what a band's own output already puts on a belt. Sizing
     * by whole consumer machines would over-build: a complete foundry would demand four iron-gear
     * machines although the plan only ever needs a fraction of its rate, and that row would then
     * have no consumer at all.
     */
    private fun belted(
        factory: FactoryProduction,
        grouped: Set<FeedLink>,
        onBelt: Map<Recipe, Int>,
        built: Map<Recipe, Int>,
    ): PlannedRecipe? {
        val recipe = factory.recipe
        val material = recipe.result.keys.singleOrNull() ?: return null
        val rate = beltedRate(recipe, grouped)
        val perMachine = outputPerMachine(factory, material)
        val demand =
            if (perMachine > 0.0)
                ceil(rate / perMachine - FEED_EPSILON).toInt() - (onBelt[recipe] ?: 0)
            else 0
        val leftover = (need.getValue(recipe) - (built[recipe] ?: 0)).coerceAtLeast(0)
        val machines = maxOf(demand, leftover).coerceIn(0, MAX_COPIES)
        return if (rate <= 0.0 || machines <= 0) null
        else {
            require(factory == production.getValue(recipe))
            PlannedRecipe(factory, machines.toDouble())
        }
    }

    /** The target always needs a row of its own when no band built it. */
    private fun targetRow(built: Map<Recipe, Int>) =
        if ((built[target] ?: 0) > 0) emptyList()
        else listOf(PlannedRecipe(production.getValue(target), need.getValue(target).toDouble()))
}

private fun List<FeedLink>.recipes() = flatMap { listOf(it.producer, it.consumer) }.distinct()

private fun sharesRecipe(first: FeedLink, second: FeedLink) =
    first.producer == second.producer ||
        first.producer == second.consumer ||
        first.consumer == second.producer ||
        first.consumer == second.consumer

/**
 * Splits a plan's recipes into allocations and direct-feed bands.
 *
 * A solid input whose producer stands in a small whole-machine cycle with its consumer
 * ([cycleRatio]) is inserted machine-to-machine instead of coming over a belt, so the band holds
 * both recipes interleaved. A material the consumers outside the bands still need — after what a
 * band's own output already puts on a belt — gets one belted allocation, sized at the rate the plan
 * was drawn for. Anything that does not fit a band keeps its plan machine count and stays belted.
 */
internal fun directFeed(
    target: Recipe,
    included: List<FactoryProduction>,
    scale: Double,
    logistics: LogisticsCalculator,
): DirectFeed {
    return if (included.isEmpty()) DirectFeed(emptyList(), emptyList())
    else DirectFeedBuilder(target, included, scale, logistics).build()
}
