package de.bjrke.factorio.optimizer.common.band

import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.plan.plannedMachines
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.util.countVectors
import de.bjrke.factorio.optimizer.common.util.greatestCommonDivisor
import de.bjrke.factorio.optimizer.common.util.permute
import kotlin.math.ceil

/** The longest cycle a band repeats; a longer pattern is never searched. */
private const val MAX_CYCLE = 8

/**
 * The best arrangement of the recipes linked by [links], or null when none can be built: the
 * machine sequence that covers every recipe's whole machine need with no consumer left starving and
 * no producer left unused, at most [GROUP_MAX_MACHINES] machines long.
 */
internal fun DirectFeedContext.arrange(links: List<FeedLink>): BandPlan? {
    val recipes = links.flatMap { listOfNotNull(it.producer, it.consumer) }.distinct()

    // all members share the row, so they must share the footprint
    if (footPrintsHaveDifferentSize2(links)) return null

    val needs = groupNeeds(links) ?: return null
    val group = FeedGroup(links, needs)

    return arrangeSequence(recipes, group).minWithOrNull(bandPlanComparator())
}

private fun arrangeSequence(recipes: List<Recipe>, group: FeedGroup): Sequence<BandPlan> =
    sequence {
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

private fun footPrintsHaveDifferentSize2(links: List<FeedLink>) =
    links
        .asSequence()
        .flatMap { listOfNotNull(it, it.parent) }
        .map {
            blueprintMachineFootprint(it.production.factory)
        }
        .zipWithNext()
        .any { (a, b) -> a != b }

private fun compareNames(first: List<Recipe>, second: List<Recipe>) =
    first.zip(second) { a, b -> a.name.compareTo(b.name) }.firstOrNull { it != 0 } ?: 0

private fun DirectFeedContext.bandPlanComparator(): Comparator<BandPlan> =
    compareBy<BandPlan> { it.machines.size }
        .thenBy { plan -> plan.feeds.sumOf { inserters(it.flow) } }
        .then { a, b -> compareNames(a.machines, b.machines) }

/**
 * The whole machines each recipe of the group needs: the root consumer as the plan sized it, every
 * other recipe from its consumers inside the group.
 */
private fun DirectFeedContext.groupNeeds(links: List<FeedLink>): Map<Recipe, Int>? {
    // the root is the recipe whose product leaves the group again: the final consumer
    val root =
        links.mapNotNull { it.parent }.firstOrNull { link -> links.none { it == link } }
            ?: return null
    val need = plannedMachines(root.production, scale)
    val needs = mutableMapOf(root.production.recipe to need)
    var progress = true
    while (progress) {
        progress = false
        for (link in links) {
            if (link.producer in needs) continue
            val consumer = needs[link.consumer] ?: continue
            val ratio = link.ioRatio
            needs[link.producer] = maxOf(needs[link.producer] ?: 0, ceil(consumer * ratio).toInt())
            progress = true
        }
    }
    return if (needs.size == links.size + 1) needs else null
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
        val producerCount = counts.getValue(link.producer)
        val consumer = link.consumer ?: return@all false
        val consumerCount = counts.getValue(consumer)
        val shared =
            greatestCommonDivisor(
                producerCount,
                consumerCount,
            )
        val p = producerCount / shared
        val q = consumerCount / shared
        if (p + q > MAX_RATIO_MACHINES) return@all false
        val wanted = link.ioRatio
        val value = p.toDouble() / q
        value >= wanted / UNDER_SUPPLY - FEED_EPSILON &&
            value <= wanted * OVER_SUPPLY + FEED_EPSILON
    }

/**
 * The band built from cycling [cycle]: the pattern repeated often enough to cover every recipe's
 * whole machine need, cut to the shortest window at which no consumer starves and no producer is
 * left without a consumer next to it. Null when even [GROUP_MAX_MACHINES] machines cannot do it.
 */
private fun window(
    group: FeedGroup,
    cycle: List<Recipe>,
    counts: Map<Recipe, Int>,
): BandPlan? {
    val repetitions =
        group.needs.maxOf { (recipe, count) ->
            ceil(count.toDouble() / counts.getValue(recipe)).toInt()
        }
    for (length in maxOf(cycle.size, repetitions * cycle.size)..GROUP_MAX_MACHINES) {
        for (offset in cycle.indices) {
            val machines = List(length) { cycle[(offset + it) % cycle.size] }
            val allNeedsFulfilled =
                group.needs.all { (recipe, count) -> machines.count { it == recipe } >= count }

            if (!allNeedsFulfilled) continue
            val feeds = FeedWindow(group, machines).feeds() ?: continue
            return BandPlan(machines, feeds)
        }
    }
    return null
}

/** How many inserters one machine-to-machine flow needs, at least one. */
private fun DirectFeedContext.inserters(flow: Double) =
    maxOf(1.0, ceil(logistics.directInserterCount(flow)))

/**
 * One window's greedy left-to-right flow: every consumer takes from its left neighbor first and
 * then from its right. The window is valid only when every consumer is covered and every producer
 * slot fed someone, so no machine is left starving or unused.
 */
private class FeedWindow(
    group: FeedGroup,
    private val machines: List<Recipe>,
) {
    private val links = group.links
    private val supply = DoubleArray(machines.size * links.size)
    private val used = mutableSetOf<Int>()
    private val feeds = mutableMapOf<Int, BandFeed>()

    fun feeds(): List<BandFeed>? {
        seedSupply()
        for (position in machines.indices) {
            for ((index, link) in links.withIndex()) {
                if (machines[position] != link.consumer) continue
                if (!consume(position, link, index)) return null
            }
        }
        return if (everythingUsed()) feeds.values.sortedBy { it.gap } else null
    }

    private fun seedSupply() {
        for (position in machines.indices) {
            for ((index, link) in links.withIndex()) {
                if (machines[position] != link.producer) continue
                supply[position * links.size + index] = link.outputPerMachine
            }
        }
    }

    private fun consume(position: Int, link: FeedLink, index: Int): Boolean {
        var demand = link.inputPerMachine
        for (side in listOf(position - 1, position + 1)) {
            if (side !in machines.indices) continue
            if (machines[side] != link.producer) continue
            val slot = side * links.size + index
            val taken = minOf(supply[slot], demand)
            if (taken <= FEED_EPSILON) continue
            supply[slot] -= taken
            used += slot
            val gap = minOf(position, side)
            val known = feeds[gap]?.flow ?: 0.0
            feeds[gap] = BandFeed(gap, link, known + taken, side < position)
            demand -= taken
        }
        return demand <= FEED_EPSILON
    }

    /** Every producer slot must have fed a consumer, or a machine would be built unused. */
    private fun everythingUsed() =
        machines.indices.all { position ->
            links.indices.all { index ->
                machines[position] != links[index].producer || position * links.size + index in used
            }
        }
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
