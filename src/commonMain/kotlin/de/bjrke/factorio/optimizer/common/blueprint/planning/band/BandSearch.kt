package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.IndexSet
import de.bjrke.factorio.optimizer.common.model.ProductionDoubleMap
import de.bjrke.factorio.optimizer.common.model.ProductionIntMap
import de.bjrke.factorio.optimizer.common.model.RecipeIndex
import de.bjrke.factorio.optimizer.common.util.countVectors
import de.bjrke.factorio.optimizer.common.util.greatestCommonDivisor
import kotlin.math.ceil
import kotlin.math.max

/**
 * The best arrangement of the recipes linked by [links], or null when none can be built: the
 * machine sequence that covers every recipe's plan machine need with no consumer underfed and no
 * producer left unused, at most [maxBandMachines] machines long.
 */
internal fun DirectFeedContext.arrange(
    links: List<FeedLink>,
    requireComplete: Boolean = true,
): BandPlan? {
    val needs = groupNeeds(links) ?: return null
    val recipeIndices = needs.keys.toList()

    return when {
        recipeIndices.isEmpty() -> null
        recipeIndices.size > MAX_BAND_RECIPES -> null
        footPrintsHaveDifferentSize(recipeIndices) -> null
        else -> BandCycleSearch(this, links, needs, recipeIndices).arrangeBest(requireComplete)
    }
}

/**
 * Plan order: fewest machines first, so a longer window can never win, then the smallest ratio
 * penalty, the least machine-to-machine flow, and the recipe names for a stable pick.
 */
private val comparator: Comparator<BandPlan> =
    compareBy<BandPlan> { it.machines.size }
        .thenBy { it.ratioPenalty }
        .thenBy { plan -> plan.feeds.sumOf { it.flow } }
        .then { a, b -> compareNames(a.machines, b.machines) }

/**
 * One DFS over the cycles with a fixed machine-count vector. Placing a machine covers the links to
 * its predecessor; a link whose two recipes are both used up but that is not on the cycle's wrap
 * boundary can never become adjacent, so that branch is cut. At the boundary the last machine
 * closes the cycle to the fixed first one, and [window] decides whether that cycle's plan-rate flow
 * works. The first machine position is fixed to [firstRecipeIndex]: every cycle contains it, and
 * [window] already tries every rotation, so no arrangement is lost.
 */
private class BandCycleSearch(
    private val ctx: DirectFeedContext,
    private val links: List<FeedLink>,
    private val needs: Map<RecipeIndex, Double>,
    private val recipeIndices: List<RecipeIndex>,
) {
    private val firstRecipeIndex = recipeIndices.first()

    /** The machines still to place per recipe. */
    private val remaining = ProductionIntMap(ctx.arraySize)

    /**
     * Each group recipe's position in [recipeIndices]. Window counts read it to address a small
     * `IntArray(recipeIndices.size)` instead of one entry per recipe index.
     */
    private val recipePositions = ProductionIntMap(ctx.arraySize)

    /** What one machine of each recipe makes per second. */
    private val outputPerRecipe = ProductionDoubleMap(ctx.arraySize)

    /** The recipes that produce something the band inserts. */
    private val producerRecipeIndices = IndexSet(ctx.arraySize)

    /**
     * The links that become adjacent for each unordered recipe pair. A pair is keyed the same way
     * whichever machine is the producer, because adjacency realizes both directions on one gap.
     */
    private val pairLinks =
        links.indices
            .groupBy { pairKey(links[it].producer.index, links[it].consumer.index) }
            .mapValues { (_, linkIndices) -> linkIndices.toIntArray() }
    private val covered = IntArray(links.size)
    private var counts: Map<RecipeIndex, Int> = emptyMap()
    private var best: BandPlan? = null

    init {
        for ((position, recipeIndex) in recipeIndices.withIndex()) {
            outputPerRecipe[recipeIndex] = ctx.factories.getValue(recipeIndex).outputPerMachine()
            recipePositions[recipeIndex] = position
        }
        for (link in links) producerRecipeIndices[link.producer.index] = true
    }

    fun bestPlan(
        counts: Map<RecipeIndex, Int>,
        best: BandPlan?,
        requireComplete: Boolean,
    ): BandPlan? {
        this.counts = counts
        this.best = best
        var total = 0
        for (recipeIndex in recipeIndices) {
            val count = counts.getValue(recipeIndex)
            remaining[recipeIndex] = count
            total += count
        }
        // one recipe index per machine slot, in built order; the first is fixed, the DFS fills
        // the rest
        val recipeCycle = Array(total) { firstRecipeIndex }
        remaining[firstRecipeIndex] -= 1
        search(1, recipeCycle, requireComplete)
        return this.best
    }

    private fun search(
        position: Int,
        recipeCycle: Array<RecipeIndex>,
        requireComplete: Boolean,
    ) {
        if (position == recipeCycle.size) {
            finish(recipeCycle, requireComplete)
            return
        }
        val previousIndex = recipeCycle[position - 1]
        for (nextIndex in recipeIndices) {
            if (remaining[nextIndex] == 0) continue
            recipeCycle[position] = nextIndex
            remaining[nextIndex] -= 1
            cover(previousIndex, nextIndex, 1)
            if (position + 1 == recipeCycle.size || linksStillReachable(nextIndex)) {
                search(position + 1, recipeCycle, requireComplete)
            }
            cover(previousIndex, nextIndex, -1)
            remaining[nextIndex] += 1
        }
    }

    /**
     * The band built from cycling [recipeCycle]: the pattern repeated often enough to cover every
     * recipe's plan machine need, cut to the shortest window at which no consumer is underfed and
     * no producer is left without a consumer next to it. Null when even [maxBandMachines] machines
     * cannot do it.
     */
    private fun window(
        recipeCycle: Array<RecipeIndex>,
        counts: Map<RecipeIndex, Int>,
        maxLength: Int,
        requireComplete: Boolean,
    ): BandPlan? {
        val repetitions =
            needs.maxOfOrNull { (recipeIndex, count) ->
                if (count <= FEED_EPSILON) 0
                else ceil(count / counts.getValue(recipeIndex) - FEED_EPSILON).toInt()
            } ?: 0
        val start = repetitions.coerceAtLeast(1) * recipeCycle.size
        if (start > maxLength) return null
        for (length in start..minOf(maxBandMachines(links.size), maxLength)) {

            val extendedCycle = Array(length) { recipeCycle[0] }

            for (offset in recipeCycle.indices) {
                for (position in 0 until length) {
                    extendedCycle[position] = recipeCycle[(offset + position) % recipeCycle.size]
                }

                val plan =
                    windowPlan(extendedCycle)?.takeUnless {
                        requireComplete && it.hasInBandBeltInput()
                    }
                if (plan != null) {
                    return plan
                }
            }
        }
        return null
    }

    /** The plan for the window at [offset] of [length] machines, or null when its flow fails. */
    private fun windowPlan(extendedCycle: Array<RecipeIndex>): BandPlan? {
        // counts per group recipe, addressed by its position in [recipeIndices] rather than by
        // its recipe index
        val machinesPerRecipe = IntArray(recipeIndices.size)
        for (recipeIndex in extendedCycle) {
            machinesPerRecipe[recipePositions[recipeIndex]] += 1
        }
        val allNeedsFulfilled = needs.all { (recipeIndex, count) ->
            machinesPerRecipe[recipePositions[recipeIndex]] + FEED_EPSILON >= count
        }
        if (!allNeedsFulfilled) return null

        return FeedWindow(extendedCycle).feeds()?.let { feeds ->
            val machines = extendedCycle.map { ctx.factories.getValue(it) }
            BandPlan(
                machines,
                feeds,
                ratioPenalty(links, machinesPerRecipe, recipePositions),
            )
        }
    }

    /**
     * Whether a solid material the band produces is still taken from a belt by a machine of the
     * same window. The band would need an extra belt for a resource it already makes; such a window
     * is not a valid band. A material produced and consumed outside the window (an export) is fine.
     */
    private fun BandPlan.hasInBandBeltInput(): Boolean {
        val produced =
            machines.flatMap { it.recipe.result.keys }.filterNot(Material::isFluid).toSet()
        val incoming =
            feeds.mapTo(mutableSetOf()) { feed ->
                (if (feed.producerIsLeft) feed.gap + 1 else feed.gap) to feed.link.material
            }
        return machines.withIndex().any { (index, machine) ->
            machine.recipe.ingredients.keys.any { material ->
                !material.isFluid && material in produced && (index to material) !in incoming
            }
        }
    }

    private fun finish(recipeCycle: Array<RecipeIndex>, requireComplete: Boolean) {
        val lastRecipeIndex = recipeCycle.last()
        cover(lastRecipeIndex, firstRecipeIndex, 1)
        if (covered.all { it > 0 }) {
            val current = best
            val plan =
                window(
                    recipeCycle,
                    counts,
                    current?.machines?.size ?: maxBandMachines(links.size),
                    requireComplete,
                )
            if (plan != null && (current == null || comparator.compare(plan, current) < 0))
                best = plan
        }
        cover(lastRecipeIndex, firstRecipeIndex, -1)
    }

    /** Whether every uncovered link can still become adjacent somewhere in the remaining cycle. */
    private fun linksStillReachable(lastRecipeIndex: RecipeIndex): Boolean {
        val hasRemaining = recipeIndices.any { remaining[it] > 0 }
        return links.indices.all { link ->
            covered[link] > 0 || reachable(link, lastRecipeIndex, hasRemaining)
        }
    }

    private fun reachable(
        link: Int,
        lastRecipeIndex: RecipeIndex,
        hasRemaining: Boolean,
    ): Boolean {
        val producerIndex = links[link].producer.index
        val consumerIndex = links[link].consumer.index
        val bothRemain =
            remaining[producerIndex] > 0 &&
                remaining[consumerIndex] > 0 &&
                (producerIndex != consumerIndex || remaining[producerIndex] >= 2)
        val afterLast =
            (lastRecipeIndex == producerIndex && remaining[consumerIndex] > 0) ||
                (lastRecipeIndex == consumerIndex && remaining[producerIndex] > 0)
        val viaWrap =
            hasRemaining &&
                ((producerIndex == firstRecipeIndex && remaining[consumerIndex] > 0) ||
                    (consumerIndex == firstRecipeIndex && remaining[producerIndex] > 0))
        return bothRemain || afterLast || viaWrap
    }

    private fun cover(
        fromIndex: RecipeIndex,
        toIndex: RecipeIndex,
        delta: Int,
    ) {
        val pair = pairLinks[pairKey(fromIndex, toIndex)] ?: return
        for (link in pair) covered[link] += delta
    }

    /**
     * The unordered key of two recipe indices, packed for [ctx]'s dense array size. Adjacency has
     * no direction: the same gap realizes a link from either machine to the other, so `(a, b)` and
     * `(b, a)` must address the same entry of [pairLinks].
     */
    private fun pairKey(
        firstIndex: RecipeIndex,
        secondIndex: RecipeIndex,
    ) =
        minOf(firstIndex.value, secondIndex.value) * ctx.arraySize +
            maxOf(firstIndex.value, secondIndex.value)

    fun arrangeBest(requireComplete: Boolean): BandPlan? {
        var best: BandPlan? = null
        for (length in recipeIndices.size..maxBandMachines(links.size)) {
            val bestLength = best?.machines?.size
            if (bestLength != null && length > bestLength) break
            for (counts in countVectors(recipeIndices, length)) {
                if (counts.values.reduce(::greatestCommonDivisor) != 1) continue
                best = bestPlan(counts, best, requireComplete)
            }
        }
        return best
    }

    /**
     * One window's greedy left-to-right flow: every consumer takes from its left neighbor first and
     * then from its right. Each machine carries its share of the plan's rate, so a partly used
     * machine is allowed and overfeed is harmless. The window is valid only when every consumer is
     * covered and every producer slot fed someone, so no machine is left starving or unused.
     */
    private inner class FeedWindow(private val extendedCycle: Array<RecipeIndex>) {
        private val length = extendedCycle.size
        private val consumerCounts = IntArray(links.size)
        private val demand =
            DoubleArray(links.size) { index ->
                (needs[links[index].consumer.index] ?: 0.0) * links[index].inputPerMachine
            }

        private val supply = DoubleArray(length * links.size)
        private val remaining = extendedCycle.map { outputPerRecipe[it] }.toDoubleArray()
        private val used = BooleanArray(length)
        private val feeds = arrayOfNulls<BandFeed>(length - 1)

        init {
            for (recipeIndex in extendedCycle) {
                for ((index, link) in links.withIndex()) {
                    if (recipeIndex == link.consumer.index) consumerCounts[index]++
                }
            }
        }

        fun feeds(): List<BandFeed>? {
            seedSupply()
            for (position in extendedCycle.indices) {
                val recipeIndex = extendedCycle[position]
                for ((index, link) in links.withIndex()) {
                    if (recipeIndex != link.consumer.index) continue
                    if (!consume(position, link, index)) return null
                }
            }
            return if (everythingUsed()) feeds.filterNotNull() else null
        }

        private fun seedSupply() {
            for (position in extendedCycle.indices) {
                val recipeIndex = extendedCycle[position]
                if (recipeIndex !in producerRecipeIndices) continue
                for ((index, link) in links.withIndex()) {
                    if (recipeIndex != link.producer.index) continue
                    supply[position * links.size + index] = link.outputPerMachine
                }
            }
        }

        private fun consume(position: Int, link: FeedLink, index: Int): Boolean {
            val consumerCount = consumerCounts[index]
            var demandLeft = if (consumerCount <= 0) 0.0 else demand[index] / consumerCount
            for (side in listOf(position - 1, position + 1)) {
                if (side !in 0 until length) continue
                if (extendedCycle[side] != link.producer.index) continue
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
            extendedCycle.withIndex().all { (position, recipeIndex) ->
                recipeIndex !in producerRecipeIndices || used[position]
            }
    }
}

/**
 * How far the window's per-recipe machine counts [machinesPerRecipe] are from the links'
 * per-machine rates; [recipePositions] maps a recipe to its entry in that array.
 */
private fun ratioPenalty(
    links: List<FeedLink>,
    machinesPerRecipe: IntArray,
    recipePositions: ProductionIntMap,
): Double = links.sumOf { link ->
    val producerCount = machinesPerRecipe[recipePositions[link.producer.index]]
    if (producerCount <= 0) return@sumOf 0.0
    val consumerCount = machinesPerRecipe[recipePositions[link.consumer.index]]
    if (consumerCount <= 0) return@sumOf 0.0
    val wanted = link.ioRatio
    if (wanted <= 0.0) return@sumOf 0.0
    val value = producerCount.toDouble() / consumerCount
    val under = max(0.0, wanted - value) / wanted
    val over = max(0.0, value - wanted) / wanted
    under * UNDERFEED_PENALTY + over * OVERFEED_PENALTY
}

private fun DirectFeedContext.footPrintsHaveDifferentSize(recipeIndices: List<RecipeIndex>) =
    recipeIndices
        .asSequence()
        .map { blueprintMachineFootprint(factories.getValue(it).factory) }
        .zipWithNext()
        .any { (a, b) -> a != b }

private fun compareNames(first: List<FactoryProduction>, second: List<FactoryProduction>) =
    first.zip(second) { a, b -> a.recipe.name.compareTo(b.recipe.name) }.firstOrNull { it != 0 }
        ?: 0

/**
 * The whole machines each recipe of the group needs. Only recipes whose product leaves the group
 * start from the plan's own machine need; every other recipe is propagated from its consumers.
 * Needs are fractional: a recipe the plan uses only partly still needs one machine, not a whole
 * consumer machine at a different rate.
 */
internal fun DirectFeedContext.groupNeeds(links: List<FeedLink>): Map<RecipeIndex, Double>? {
    val nodes = links.flatMap { listOf(it.producer, it.consumer) }.distinct()
    val outgoing = links.groupBy { it.producer.production }
    val needs =
        nodes.associateTo(mutableMapOf()) {
            it.index to
                if (it.production in outgoing) 0.0 else max(0.0, it.production.amount * scale)
        }

    var changed = true
    var rounds = 0
    val limit = nodes.size + links.size
    while (changed && rounds < limit) {
        changed = false
        rounds++
        for ((producer, outgoingLinks) in outgoing) {
            val required = outgoingLinks.sumOf { link ->
                (needs[link.consumer.index] ?: 0.0) * link.ioRatio
            }
            if (required > needs.getValue(producer.index) + FEED_EPSILON) {
                needs[producer.index] = required
                changed = true
            }
        }
    }
    return if (changed) null else needs
}

/**
 * A quick machine budget before the expensive arrangement search: one band cannot hold more than
 * [maxBandMachines] machines, and the arrangement search stops at [MAX_BAND_RECIPES] recipes.
 */
internal fun DirectFeedContext.groupMayFit(links: List<FeedLink>): Boolean {
    val nodes = links.flatMap { listOf(it.producer, it.consumer) }.distinct()
    if (nodes.size > MAX_BAND_RECIPES) return false
    var budget = maxBandMachines(links.size).toDouble()
    // The plan's per-recipe machine count overstates a group that internalizes links; the
    // propagated needs are the rates the window actually has to cover. Without a converged
    // propagation the plan's own per-recipe rate is the best estimate left.
    val machineNeeds =
        when (val needs = groupNeeds(links)) {
            null -> nodes.asSequence().map { it.production.amount * scale }
            else -> needs.values.asSequence()
        }

    for (need in machineNeeds) {
        if (budget < 0) return false
        budget -= ceil(max(1.0, need) - FEED_EPSILON)
    }

    return budget >= 0
}
