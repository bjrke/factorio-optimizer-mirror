package de.bjrke.factorio.optimizer.common.band

import de.bjrke.factorio.optimizer.common.plan.BandFeed
import de.bjrke.factorio.optimizer.common.plan.BandPlan
import de.bjrke.factorio.optimizer.common.plan.FEED_EPSILON
import de.bjrke.factorio.optimizer.common.plan.FeedGroup
import de.bjrke.factorio.optimizer.common.plan.FeedLink
import de.bjrke.factorio.optimizer.common.plan.GROUP_MAX_MACHINES
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.MAX_RATIO_MACHINES
import de.bjrke.factorio.optimizer.common.plan.OVER_SUPPLY
import de.bjrke.factorio.optimizer.common.plan.UNDER_SUPPLY
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.plan.inputPerMachine
import de.bjrke.factorio.optimizer.common.plan.outputPerMachine
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import kotlin.math.ceil

/** The longest cycle a band repeats; a longer pattern is never searched. */
private const val MAX_CYCLE = 8

/**
 * Searches the machine cycles a direct-feed group can be built as: the shortest band that covers
 * every recipe's whole machine need, with the insertions between its machines. [production] and
 * [need] are the plan's per-recipe data, [logistics] sizes the inserters.
 */
internal class BandSearch(
    private val production: Map<Recipe, FactoryProduction>,
    private val need: Map<Recipe, Int>,
    private val logistics: LogisticsCalculator,
) {
    /**
     * The best arrangement of [recipes] linked by [links], or null when none can be built: the
     * machine sequence that covers every recipe's whole machine [need] with no consumer left
     * starving and no producer left unused, at most [GROUP_MAX_MACHINES] machines long.
     */
    fun arrange(
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

    private fun footPrintsHaveDifferentSize(recipes: List<Recipe>) =
        recipes
            .map {
                blueprintMachineFootprint(production.getValue(it).factory.identifier)
            }
            .zipWithNext()
            .any { (a, b) -> a != b }

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
            value >= wanted / UNDER_SUPPLY - FEED_EPSILON &&
                value <= wanted * OVER_SUPPLY + FEED_EPSILON
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
                val feeds = FeedWindow(group, machines, production).feeds() ?: continue
                return BandPlan(machines, feeds)
            }
        }
        return null
    }

    /** How many inserters one machine-to-machine flow needs, at least one. */
    private fun inserters(flow: Double) = maxOf(1.0, ceil(logistics.directInserterCount(flow)))
}

/**
 * One window's greedy left-to-right flow: every consumer takes from its left neighbor first and
 * then from its right. The window is valid only when every consumer is covered and every producer
 * slot fed someone, so no machine is left starving or unused.
 */
private class FeedWindow(
    group: FeedGroup,
    private val machines: List<Recipe>,
    private val production: Map<Recipe, FactoryProduction>,
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
                supply[position * links.size + index] =
                    outputPerMachine(production.getValue(link.producer), link.material)
            }
        }
    }

    private fun consume(position: Int, link: FeedLink, index: Int): Boolean {
        var demand = inputPerMachine(production.getValue(link.consumer), link.material)
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
