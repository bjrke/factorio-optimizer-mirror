package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlin.math.ceil

/** Upper bound on the machines of one direct-feed band, so a band cannot run away. */
const val GROUP_MAX_MACHINES = 18

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
                if (logistics.isFluid(material)) continue
                val producer = producers[material] ?: continue
                if (producer.recipe === consumer.recipe) continue
                if (producer.recipe.ingredients.keys.any { logistics.isFluid(it) }) continue
                val ratio =
                    inputPerMachine(consumer, material) / outputPerMachine(producer, material)
                if (cycleRatio(ratio) != null)
                    yield(FeedLink(material, producer.recipe, consumer.recipe))
            }
        }
    }

    // grow groups greedily: a link joins the group sharing a recipe, and is dropped again
    // when the merged arrangement cannot be built, so it simply stays belted
    private val builders = buildList {
        for (link in candidates) {
            val host =
                firstOrNull { group ->
                    group.any {
                        it.producer == link.producer ||
                            it.producer == link.consumer ||
                            it.consumer == link.producer ||
                            it.consumer == link.consumer
                    }
                } ?: mutableListOf<FeedLink>().also { add(it) }
            if (host.size >= MAX_BAND_LINKS) continue
            host += link
            val recipes = host.flatMap { listOf(it.producer, it.consumer) }.distinct()
            if (recipes.size > MAX_BAND_RECIPES) {
                host.remove(link)
                continue
            }
            if (search.arrange(recipes, host) == null) host.remove(link)
        }
    }

    // the arrangements that survived, as bands
    private val bandPlans = builders.mapNotNull { links ->
        if (links.isEmpty()) return@mapNotNull null
        val recipes = links.flatMap { listOf(it.producer, it.consumer) }.distinct()
        search.arrange(recipes, links)
    }

    fun build(): DirectFeed {
        val recipes = mutableListOf<PlannedRecipe>()

        val bands = mutableListOf<PlannedBand>()
        for (plan in bandPlans) {
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

            val machines = plan.machines.map { allocationOf.getValue(it) }
            bands +=
                PlannedBand(
                    machines,
                    plan.feeds.map {
                        PlannedLink(it.gap, it.link.material, it.flow, it.producerIsLeft)
                    },
                )
        }

        val grouped = bandPlans.flatMap { it.feeds }.map { it.link }.toSet()
        // what every recipe is built as so far, band members included
        val built =
            recipes
                .groupBy { it.recipe }
                .mapValues { (_, plannedRecipes) -> plannedRecipes.sumOf { it.machines.toInt() } }

        // machines a band builds whose product still leaves it over a belt, i.e. the band's output
        // as opposed to a link that is inserted machine to machine; those already supply the
        // belted consumers
        val onBelt = buildMap {
            for (plan in bandPlans) {
                val inserted = plan.feeds.mapTo(mutableSetOf()) { it.link.producer }
                for ((recipe, machines) in plan.machines.groupingBy { it }.eachCount()) {
                    if (recipe in inserted) continue
                    this[recipe] = (this[recipe] ?: 0) + machines
                }
            }
        }

        // one belted allocation per recipe: what the consumers that are not direct-fed still
        // need, at the rate the plan was sized for (the same rate an import draws), less what a
        // band's own output already puts on a belt. Sizing by whole consumer machines would
        // over-build: a complete foundry would demand four iron-gear machines although the plan
        // only ever needs a fraction of its rate, and that row would then have no consumer at all.
        fun mapBelted(factory: FactoryProduction): PlannedRecipe? {
            val recipe = factory.recipe
            var rate = 0.0

            for (consumer in included) {
                for (material in consumer.recipe.ingredients.keys) {
                    if (material !in recipe.result.keys) continue
                    if (logistics.isFluid(material)) continue
                    if (producers[material]?.recipe !== recipe) continue
                    if (FeedLink(material, recipe, consumer.recipe) in grouped) continue
                    rate += consumer.consumption(material) * scale
                }
            }
            // every consumer takes this recipe over a band's insertion, so the band already
            // builds it
            if (rate <= 0.0) return null
            val material = recipe.result.keys.singleOrNull() ?: return null
            val perMachine = outputPerMachine(factory, material)
            val demand =
                if (perMachine > 0.0)
                    ceil(rate / perMachine - FEED_EPSILON).toInt() - (onBelt[recipe] ?: 0)
                else 0
            val leftover = (need.getValue(recipe) - (built[recipe] ?: 0)).coerceAtLeast(0)
            val machines = maxOf(demand, leftover).coerceIn(0, MAX_COPIES)
            if (machines <= 0) return null
            require(factory == production.getValue(recipe))
            return PlannedRecipe(factory, machines.toDouble())
        }

        val belted = included.sortedBy { it.recipe.name }.mapNotNull(::mapBelted)

        // the target always needs a row of its own when no band built it
        val targetRow =
            if ((built[target] ?: 0) > 0) emptyList()
            else
                listOf(PlannedRecipe(production.getValue(target), need.getValue(target).toDouble()))

        return DirectFeed(recipes + targetRow + belted, bands)
    }
}

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
