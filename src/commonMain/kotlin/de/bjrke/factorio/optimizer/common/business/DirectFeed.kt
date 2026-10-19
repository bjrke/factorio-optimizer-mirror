package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import kotlin.math.abs
import kotlin.math.ceil

/** Upper bound on the machines of one direct-feed band, so a band cannot run away. */
const val GROUP_MAX_MACHINES = 18

/** Under-supply accepted when no cycle matches the needed machine rate exactly. */
private const val UNDER_SUPPLY = 1.05

/** Over-supply accepted; a shorter cycle wins over an exact but longer one. */
private const val OVER_SUPPLY = 1.25

/** The longest cycle ratio `p + q` one link may be simplified to. */
private const val MAX_RATIO_MACHINES = 6

/** The longest cycle a band repeats; a longer pattern is never searched. */
private const val MAX_CYCLE = 8

/** The recipes one band may hold; a wider group is left to a later increment. */
private const val MAX_BAND_RECIPES = 3

/** The links one band may realize. */
private const val MAX_BAND_LINKS = 3

/** Slack for the floating point rate and supply comparisons. */
private const val EPSILON = 1e-9

/** Items of [material] one machine of [production] takes per second. */
internal fun inputPerMachine(production: FactoryProduction, material: Material): Double {
    val items = production.recipe.ingredients[material] ?: return 0.0
    val time = production.recipe.time
    return if (time <= 0.0) 0.0 else production.speed * items / time
}

/**
 * Items of [material] one machine of [production] makes per second. The productivity bonus counts:
 * that is what a direct-fed consumer actually receives, so it belongs in the ratio.
 */
internal fun outputPerMachine(production: FactoryProduction, material: Material): Double {
    val items = production.recipe.result[material] ?: return 0.0
    val time = production.recipe.time
    return if (time <= 0.0) 0.0
    else production.speed * (1.0 + production.productivity) * items / time
}

/**
 * The smallest whole-machine cycle `p:q` (producer machines per consumer machine) that can stand in
 * for [ratio]: `ratio / 1.05 <= p / q <= ratio * 1.25`, with `p + q` at most 6. Ties go to the
 * closest ratio. Null when no small cycle fits, i.e. the link cannot be direct-fed.
 */
internal fun cycleRatio(ratio: Double): Pair<Int, Int>? {
    if (ratio <= 0.0) return null
    for (sum in 2..MAX_RATIO_MACHINES) {
        var best: Pair<Int, Int>? = null
        var bestError = Double.MAX_VALUE
        for (p in 1 until sum) {
            val q = sum - p
            val value = p.toDouble() / q
            if (value < ratio / UNDER_SUPPLY - EPSILON) continue
            if (value > ratio * OVER_SUPPLY + EPSILON) continue
            val error = abs(value - ratio) / ratio
            if (error < bestError) {
                best = p to q
                bestError = error
            }
        }
        best?.let {
            return it
        }
    }
    return null
}

/** One material that can be inserted straight from a producer's machines into a consumer's. */
private data class FeedLink(val material: Material, val producer: Recipe, val consumer: Recipe)

/** A group of recipes that feed each other directly, with the whole machine need of each recipe. */
private class FeedGroup(
    val recipes: List<Recipe>,
    val links: List<FeedLink>,
    val needs: Map<Recipe, Int>,
)

/** One machine-to-machine insertion an arrangement realizes. */
private class BandFeed(
    val gap: Int,
    val link: FeedLink,
    val flow: Double,
    val producerIsLeft: Boolean,
)

/** One arrangement of a group: the machine sequence and the insertions between the machines. */
private class BandPlan(val machines: List<Recipe>, val feeds: List<BandFeed>)

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
            if (arrange(recipes, host) == null) host.remove(link)
        }
    }

    // the arrangements that survived, as bands
    private val bandPlans = builders.mapNotNull { links ->
        if (links.isEmpty()) return@mapNotNull null
        val recipes = links.flatMap { listOf(it.producer, it.consumer) }.distinct()
        arrange(recipes, links)
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
        // need,
        // at the rate the plan was sized for (the same rate an import draws), less what a
        // band's
        // own output already puts on a belt. Sizing by whole consumer machines would
        // over-build: a
        // complete foundry would demand four iron-gear machines although the plan only ever
        // needs a
        // fraction of its rate, and that row would then have no consumer at all.
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
                    ceil(rate / perMachine - EPSILON).toInt() - (onBelt[recipe] ?: 0)
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

    private fun footPrintsHaveDifferentSize(recipes: List<Recipe>) =
        recipes
            .map {
                blueprintMachineFootprint(production.getValue(it).factory.identifier)
            }
            .zipWithNext()
            .any { (a, b) -> a != b }

    /**
     * The best arrangement of [recipes] linked by [links], or null when none can be built: the
     * machine sequence that covers every recipe's whole machine [need] with no consumer left
     * starving and no producer left unused, at most [GROUP_MAX_MACHINES] machines long.
     */
    private fun arrange(
        recipes: List<Recipe>,
        links: List<FeedLink>,
    ) = sequence {
        // all members share the row, so they must share the footprint
        if (footPrintsHaveDifferentSize(recipes)) return@sequence

        val needs = groupNeeds(recipes, links) ?: return@sequence
        val group = FeedGroup(recipes, links, needs)

        for (length in recipes.size..MAX_CYCLE) {
            for (counts in countVectors(recipes, length)) {
                if (counts.values.reduce(::greatestCommonDivisor) != 1) continue
                if (!ratiosFit(group, counts)) continue
                for (cycle in permute(counts)) {
                    if (!adjacent(group, cycle)) continue
                    val plan = window(group, cycle, counts) ?: continue
                    yield(plan)
                }
            }
        }
    }
        .minWithOrNull(bandPlanComparator())

    private fun compareNames(first: List<Recipe>, second: List<Recipe>) =
        first.zip(second) { a, b -> a.name.compareTo(b.name) }.firstOrNull { it != 0 } ?: 0

    private fun bandPlanComparator(): Comparator<BandPlan> =
        compareBy<BandPlan> { it.machines.size }
            .thenBy { plan -> plan.feeds.sumOf { inserters(it.flow) } }
            .then { a, b -> compareNames(a.machines, b.machines) }

    /**
     * The whole machines each recipe of the group needs: the root consumer as the plan sized it,
     * every other recipe from its consumers inside the group.
     */
    private fun groupNeeds(
        recipes: List<Recipe>,
        links: List<FeedLink>,
    ): Map<Recipe, Int>? {
        // the root is the recipe whose product leaves the group again: the final consumer
        val roots = recipes.filter { recipe -> links.none { it.producer == recipe } }
        if (roots.size != 1) return null
        val needs = mutableMapOf(roots.single() to need.getValue(roots.single()))
        var progress = true
        while (progress) {
            progress = false
            for (link in links) {
                if (link.producer in needs) continue
                val consumer = needs[link.consumer] ?: continue
                val ratio =
                    inputPerMachine(production.getValue(link.consumer), link.material) /
                        outputPerMachine(production.getValue(link.producer), link.material)
                needs[link.producer] =
                    maxOf(needs[link.producer] ?: 0, ceil(consumer * ratio).toInt())
                progress = true
            }
        }
        return if (needs.size == recipes.size) needs else null
    }

    /**
     * Whether every link's *reduced* machine ratio fits its rate: `p + q` at most 6 and within the
     * under/over-supply tolerance. This is the cycle pattern the user's rule accepts.
     */
    private fun ratiosFit(
        group: FeedGroup,
        counts: Map<Recipe, Int>,
    ) =
        group.links.all { link ->
            val shared =
                greatestCommonDivisor(
                    counts.getValue(link.producer),
                    counts.getValue(link.consumer),
                )
            val p = counts.getValue(link.producer) / shared
            val q = counts.getValue(link.consumer) / shared
            if (p + q > MAX_RATIO_MACHINES) return@all false
            val wanted =
                inputPerMachine(production.getValue(link.consumer), link.material) /
                    outputPerMachine(production.getValue(link.producer), link.material)
            val value = p.toDouble() / q
            value >= wanted / UNDER_SUPPLY - EPSILON && value <= wanted * OVER_SUPPLY + EPSILON
        }

    /**
     * The band built from cycling [cycle]: the pattern repeated often enough to cover every
     * recipe's whole machine [needs], cut to the shortest window at which no consumer starves and
     * no producer is left without a consumer next to it. Null when even [GROUP_MAX_MACHINES]
     * machines cannot do it.
     */
    private fun window(
        group: FeedGroup,
        cycle: List<Recipe>,
        counts: Map<Recipe, Int>,
    ): BandPlan? {
        val repetitions =
            group.recipes.maxOf { recipe ->
                ceil(group.needs.getValue(recipe).toDouble() / counts.getValue(recipe)).toInt()
            }
        for (length in maxOf(cycle.size, repetitions * cycle.size)..GROUP_MAX_MACHINES) {
            for (offset in cycle.indices) {
                val machines = List(length) { cycle[(offset + it) % cycle.size] }
                val allNeedsFulfilled =
                    group.recipes.all { recipe ->
                        machines.count { it == recipe } >= group.needs.getValue(recipe)
                    }

                if (!allNeedsFulfilled) continue
                val feeds = feedsOf(group, machines) ?: continue
                return BandPlan(machines, feeds)
            }
        }
        return null
    }

    /**
     * Splits one window's machines into the insertions between them: the greedy left-to-right flow,
     * in which every consumer takes from its left neighbor first and then from its right. Null when
     * a consumer cannot be covered or a producer ends up without a consumer to feed.
     */
    private fun feedsOf(
        group: FeedGroup,
        machines: List<Recipe>,
    ): List<BandFeed>? {
        val links = group.links
        val supply = DoubleArray(machines.size * links.size)
        for (position in machines.indices) {
            for ((index, link) in links.withIndex()) {
                if (machines[position] != link.producer) continue
                supply[position * links.size + index] =
                    outputPerMachine(production.getValue(link.producer), link.material)
            }
        }
        val used = mutableSetOf<Int>()
        val feeds = mutableMapOf<Int, BandFeed>()
        for (position in machines.indices) {
            for ((index, link) in links.withIndex()) {
                if (machines[position] != link.consumer) continue
                var demand = inputPerMachine(production.getValue(link.consumer), link.material)
                for (side in listOf(position - 1, position + 1)) {
                    if (side !in machines.indices) continue
                    if (machines[side] != link.producer) continue
                    val slot = side * links.size + index
                    val taken = minOf(supply[slot], demand)
                    if (taken <= EPSILON) continue
                    supply[slot] -= taken
                    used += slot
                    val gap = minOf(position, side)
                    val known = feeds[gap]?.flow ?: 0.0
                    feeds[gap] = BandFeed(gap, link, known + taken, side < position)
                    demand -= taken
                }
                if (demand > EPSILON) return null
            }
        }
        for (position in machines.indices) {
            for ((index, link) in links.withIndex()) {
                if (machines[position] != link.producer) continue
                if (position * links.size + index !in used) return null
            }
        }
        return feeds.values.sortedBy { it.gap }
    }

    /** How many inserters one machine-to-machine flow needs, at least one. */
    private fun inserters(flow: Double) = maxOf(1.0, ceil(logistics.directInserterCount(flow)))
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

/** Every way to give [recipes] at least one machine each in [length] machines. */
private fun countVectors(recipes: List<Recipe>, length: Int): List<Map<Recipe, Int>> {
    val result = mutableListOf<Map<Recipe, Int>>()
    val current = mutableMapOf<Recipe, Int>()

    fun assign(index: Int, left: Int) {
        val remainingRecipes = recipes.size - index
        for (count in 1..(left - remainingRecipes + 1)) {
            current[recipes[index]] = count
            if (index == recipes.lastIndex) {
                if (left == count) result += current.toMap()
            } else {
                assign(index + 1, left - count)
            }
        }
        current.remove(recipes[index])
    }

    assign(0, length)
    return result
}

/** Every sequence of recipes with the machine [counts] of each. */
private fun permute(counts: Map<Recipe, Int>) = sequence {
    suspend fun SequenceScope<List<Recipe>>.build(
        remaining: Map<Recipe, Int>,
        current: List<Recipe>,
    ) {
        if (remaining.isEmpty()) yield(current)
        else
            for ((recipe, left) in remaining) {
                val newRemaining =
                    if (left > 1) remaining + (recipe to left - 1) else remaining - recipe
                build(newRemaining, current + recipe)
            }
    }

    build(counts.filterValues { it > 0 }, listOf())
}

/** Whether every link has two adjacent machines in [cycle], ignoring the direction of the flow. */
private fun adjacent(group: FeedGroup, cycle: List<Recipe>) =
    group.links.all { link ->
        cycle.withIndex().any { (index, current) ->
            val next = cycle[(index + 1) % cycle.size]
            (current == link.producer && next == link.consumer) ||
                (current == link.consumer && next == link.producer)
        }
    }

private fun greatestCommonDivisor(first: Int, second: Int): Int =
    if (second == 0) first else greatestCommonDivisor(second, first % second)
