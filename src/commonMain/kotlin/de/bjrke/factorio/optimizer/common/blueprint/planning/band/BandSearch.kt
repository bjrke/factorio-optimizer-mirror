package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.core.util.countVectors
import de.bjrke.factorio.optimizer.common.core.util.greatestCommonDivisor
import de.bjrke.factorio.optimizer.common.core.util.permute
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.math.ceil
import kotlin.math.max

/**
 * The best arrangement of the recipes linked by [links], or null when none can be built: the
 * machine sequence that covers every recipe's plan machine need with no consumer underfed and no
 * producer left unused, at most [maxBandMachines] machines long.
 */
internal fun DirectFeedContext.arrange(links: List<FeedLink>): BandPlan? {
    val nodes = links.flatMap { listOf(it.producer, it.consumer) }.distinct()
    val recipes = nodes.map { it.production }

    // all members share the row, so they must share the footprint
    if (footPrintsHaveDifferentSize(nodes)) return null

    val needs = groupNeeds(links) ?: return null
    val group = FeedGroup(links, needs)

    return arrangeBest(recipes, group)
}

/**
 * The best arrangement of [recipes], pruning every window longer than the best plan found so far.
 * Machine count is the comparator's primary key, so a longer window can never win.
 */
private fun DirectFeedContext.arrangeBest(
    recipes: List<FactoryProduction>,
    group: FeedGroup,
): BandPlan? {
    if (recipes.size > MAX_BAND_RECIPES) return null
    val comparator = bandPlanComparator()
    var best: BandPlan? = null
    for (length in recipes.size..MAX_BAND_RECIPES) {
        val bestLength = best?.machines?.size
        if (bestLength != null && length > bestLength) break
        for (counts in countVectors(recipes, length)) {
            if (counts.values.reduce(::greatestCommonDivisor) != 1) continue
            best = bestPlanForCounts(group, counts, best, comparator)
        }
    }
    return best
}

private fun DirectFeedContext.bestPlanForCounts(
    group: FeedGroup,
    counts: Map<FactoryProduction, Int>,
    best: BandPlan?,
    comparator: Comparator<BandPlan>,
): BandPlan? {
    var current = best
    for (cycle in permute(counts)) {
        if (!adjacent(group, cycle)) continue
        val maxLength = current?.machines?.size ?: maxBandMachines(group.links.size)
        val plan = window(group, cycle, counts, maxLength) ?: continue
        if (current == null || comparator.compare(plan, current) < 0) current = plan
    }
    return current
}

private fun footPrintsHaveDifferentSize(nodes: List<FeedNode>) =
    nodes
        .asSequence()
        .map { blueprintMachineFootprint(it.production.factory) }
        .zipWithNext()
        .any { (a, b) -> a != b }

private fun compareNames(first: List<FactoryProduction>, second: List<FactoryProduction>) =
    first.zip(second) { a, b -> a.recipe.name.compareTo(b.recipe.name) }.firstOrNull { it != 0 }
        ?: 0

private fun DirectFeedContext.bandPlanComparator(): Comparator<BandPlan> =
    compareBy<BandPlan> { it.machines.size }
        .thenBy { it.ratioPenalty }
        .thenBy { plan -> plan.feeds.sumOf { inserters(it.flow) } }
        .then { a, b -> compareNames(a.machines, b.machines) }

/**
 * The whole machines each recipe of the group needs. Only recipes whose product leaves the group
 * start from the plan's own machine need; every other recipe is propagated from its consumers.
 * Needs are fractional: a recipe the plan uses only partly still needs one machine, not a whole
 * consumer machine at a different rate.
 */
private fun DirectFeedContext.groupNeeds(links: List<FeedLink>): Map<FactoryProduction, Double>? {
    val nodes = links.flatMap { listOf(it.producer, it.consumer) }.distinct()
    val outgoing = links.groupBy { it.producer.production }
    val needs =
        nodes
            .associate {
                it.production to
                    if (it.production in outgoing) 0.0 else max(0.0, it.production.amount * scale)
            }
            .toMutableMap()

    var changed = true
    var rounds = 0
    val limit = nodes.size + links.size
    while (changed && rounds < limit) {
        changed = false
        rounds++
        for ((producer, outgoingLinks) in outgoing) {
            val required = outgoingLinks.sumOf { link ->
                (needs[link.consumer.production] ?: 0.0) * link.ioRatio
            }
            if (required > needs.getValue(producer) + FEED_EPSILON) {
                needs[producer] = required
                changed = true
            }
        }
    }
    return if (changed) null else needs
}

/** How far a plan's machine counts are from the links' per-machine rates. */
private fun ratioPenalty(machines: List<FactoryProduction>, links: List<FeedLink>): Double {
    val counts = machines.groupingBy { it }.eachCount()
    return links.sumOf { link ->
        val producerCount = counts[link.producer.production] ?: return@sumOf 0.0
        val consumerCount = counts[link.consumer.production] ?: return@sumOf 0.0
        val wanted = link.ioRatio
        if (wanted <= 0.0) return@sumOf 0.0
        val value = producerCount.toDouble() / consumerCount
        val under = max(0.0, wanted - value) / wanted
        val over = max(0.0, value - wanted) / wanted
        under * UNDERFEED_PENALTY + over * OVERFEED_PENALTY
    }
}

/**
 * The band built from cycling [cycle]: the pattern repeated often enough to cover every recipe's
 * plan machine need, cut to the shortest window at which no consumer is underfed and no producer is
 * left without a consumer next to it. Null when even [maxBandMachines] machines cannot do it.
 */
private fun window(
    group: FeedGroup,
    cycle: List<FactoryProduction>,
    counts: Map<FactoryProduction, Int>,
    maxLength: Int,
): BandPlan? {
    val repetitions =
        group.needs.maxOfOrNull { (recipe, count) ->
            if (count <= FEED_EPSILON) 0
            else ceil(count / counts.getValue(recipe) - FEED_EPSILON).toInt()
        } ?: 0
    val start = maxOf(cycle.size, repetitions * cycle.size)
    if (start > maxLength) return null
    for (length in start..minOf(maxBandMachines(group.links.size), maxLength)) {
        for (offset in cycle.indices) {
            val machines = List(length) { cycle[(offset + it) % cycle.size] }
            val allNeedsFulfilled =
                group.needs.all { (recipe, count) ->
                    machines.count { it == recipe } + FEED_EPSILON >= count
                }

            if (!allNeedsFulfilled) continue
            val feeds = FeedWindow(group, machines).feeds() ?: continue
            return BandPlan(machines, feeds, ratioPenalty(machines, group.links))
        }
    }
    return null
}

/** How many inserters one machine-to-machine flow needs, at least one. */
private fun DirectFeedContext.inserters(flow: Double) =
    maxOf(1.0, ceil(logistics.directInserterCount(flow)))

/**
 * One window's greedy left-to-right flow: every consumer takes from its left neighbor first and
 * then from its right. Each machine carries its share of the plan's rate, so a partly used machine
 * is allowed and overfeed is harmless. The window is valid only when every consumer is covered and
 * every producer slot fed someone, so no machine is left starving or unused.
 */
private class FeedWindow(
    group: FeedGroup,
    private val machines: List<FactoryProduction>,
) {
    private val links = group.links
    private val consumerCounts = IntArray(links.size)
    private val demand =
        DoubleArray(links.size) { index ->
            (group.needs[links[index].consumer.production] ?: 0.0) * links[index].inputPerMachine
        }

    private val supply = DoubleArray(machines.size * links.size)
    private val remaining = DoubleArray(machines.size) { machines[it].outputPerMachine() }
    private val used = BooleanArray(machines.size)
    private val feeds = arrayOfNulls<BandFeed>(machines.size - 1)
    private val isProducer = BooleanArray(machines.size)

    init {
        for (position in machines.indices) {
            val machine = machines[position]
            for ((index, link) in links.withIndex()) {
                if (machine == link.consumer.production) consumerCounts[index]++
                if (machine == link.producer.production) isProducer[position] = true
            }
        }
    }

    fun feeds(): List<BandFeed>? {
        seedSupply()
        for (position in machines.indices) {
            for ((index, link) in links.withIndex()) {
                if (machines[position] != link.consumer.production) continue
                if (!consume(position, link, index)) return null
            }
        }
        return if (everythingUsed()) feeds.filterNotNull() else null
    }

    private fun seedSupply() {
        for (position in machines.indices) {
            if (!isProducer[position]) continue
            for ((index, link) in links.withIndex()) {
                if (machines[position] != link.producer.production) continue
                supply[position * links.size + index] = link.outputPerMachine
            }
        }
    }

    private fun consume(position: Int, link: FeedLink, index: Int): Boolean {
        val consumerCount = consumerCounts[index]
        var demandLeft = if (consumerCount <= 0) 0.0 else demand[index] / consumerCount
        for (side in listOf(position - 1, position + 1)) {
            if (side !in machines.indices) continue
            if (machines[side] != link.producer.production) continue
            val slot = side * links.size + index
            val taken = minOf(supply[slot], remaining[side], demandLeft)
            if (taken <= FEED_EPSILON) continue
            supply[slot] -= taken
            remaining[side] -= taken
            used[side] = true
            val gap = minOf(position, side)
            val known = feeds[gap]?.flow ?: 0.0
            feeds[gap] = BandFeed(gap, link, known + taken, side < position)
            demandLeft -= taken
        }
        return demandLeft <= FEED_EPSILON
    }

    /** Every producer machine must have fed a consumer, or it would be built unused. */
    private fun everythingUsed() =
        machines.indices.all { position -> !isProducer[position] || used[position] }
}

/** Whether every link has two adjacent machines in [cycle], ignoring the direction of the flow. */
private fun adjacent(group: FeedGroup, cycle: List<FactoryProduction>) =
    group.links.all { link ->
        cycle.withIndex().any { (index, current) ->
            val next = cycle[(index + 1) % cycle.size]
            (current == link.producer.production && next == link.consumer.production) ||
                (current == link.consumer.production && next == link.producer.production)
        }
    }
