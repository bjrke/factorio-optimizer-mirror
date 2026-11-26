package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineFootprint
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
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

    return when {
        needs.isEmpty() -> null
        needs.size > MAX_BAND_RECIPES -> null
        footPrintsHaveDifferentSize(needs.keys) -> null
        else -> BandCycleSearch(this, links, needs).arrangeBest(requireComplete)
    }
}

/**
 * Plan order: fewest machines first, so a longer window can never win, then the smallest ratio
 * penalty, the least machine-to-machine flow, and the recipe indices for a stable pick.
 */
private val comparator: Comparator<BandPlan> =
    compareBy<BandPlan>(
            { it.machines.size },
            { it.ratioPenalty },
            { plan -> plan.feeds.sumOf { it.flow } },
        )
        .then(::compareMachineIndices)

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
) {

    private val recipeIndices = needs.keys.toTypedArray()

    private val firstRecipeIndex = recipeIndices.first()

    /** The machines still to place per recipe. */
    private val remaining = ProductionIntMap(ctx.arraySize)

    /**
     * Each group recipe's position in [recipeIndices]. The window counts, the link buckets and the
     * coverage array address their entries by it, so they never grow with the plan's recipe count.
     */
    private val recipePositions =
        ProductionIntMap(ctx.arraySize).also {
            for ((position, recipeIndex) in recipeIndices.withIndex()) {
                it[recipeIndex] = position
            }
        }

    /** What one machine of each recipe makes per second. */
    private val outputPerRecipe =
        DoubleArray(recipeIndices.size) { position ->
            ctx.factories.getValue(recipeIndices[position]).outputPerMachine()
        }

    /** The links each position takes, as indices into [links]; [positionOf] picks the end. */
    private fun linksByEnd(positionOf: (FeedLink) -> FeedNode) =
        links
            .withIndex()
            .groupBy(
                { (_, link) -> recipePositions[positionOf(link).index] },
                { (index, _) -> index },
            )
            .mapValues { it.value.toIntArray() }
            .let { grouped ->
                Array(recipeIndices.size) { position -> grouped[position] ?: IntArray(0) }
            }

    /** The links each consumer position takes, as indices into [links]. */
    private val linksByConsumerPosition = linksByEnd(FeedLink::consumer)
    /** The links each producer position makes, as indices into [links]. */
    private val linksByProducerPosition = linksByEnd(FeedLink::producer)

    /** The coverage slot of every link's recipe pair. */
    private val linkPairKeys =
        IntArray(links.size) {
            val link = links[it]
            pairKey(recipePositions[link.producer.index], recipePositions[link.consumer.index])
        }

    /** How many placed gaps realize each unordered pair of recipe positions. */
    private val covered = IntArray(recipeIndices.size * recipeIndices.size)

    inner class InnerBandSearch(
        val counts: Map<RecipeIndex, Int>,
        var best: BandPlan?,
        val requireComplete: Boolean,
    ) {

        private var maxLength = maxBandMachines(links.size)

        // one recipe position per machine slot, in built order; the first is fixed, the DFS
        // fills the rest
        val recipeCycle = IntArray(counts.values.sum())

        init {
            for (recipeIndex in recipeIndices) {
                val count = counts.getValue(recipeIndex)
                remaining[recipeIndex] = count
            }
            remaining[firstRecipeIndex] -= 1

            best?.let {
                if (it.machines.size < maxLength) maxLength = it.machines.size
            }

            search(1)
        }

        fun bestPlan() = best

        private fun search(position: Int) {
            val previousPos = recipeCycle[position - 1]
            for ((nextPos, nextIndex) in recipeIndices.withIndex()) {
                if (remaining[nextIndex] == 0) continue
                recipeCycle[position] = nextPos
                remaining[nextIndex] -= 1
                val key = pairKey(previousPos, nextPos)
                cover(key, 1)
                if (position + 1 == recipeCycle.size) {
                    finish()
                } else if (linksStillReachable(nextIndex)) {
                    search(position + 1)
                }

                cover(key, -1)
                remaining[nextIndex] += 1
            }
        }

        private fun finish() {
            val key = pairKey(recipeCycle.last(), 0)
            cover(key, 1)
            if (linkPairKeys.all { covered[it] > 0 }) {
                window()
                    ?.takeIf {
                        val current = best
                        current == null || comparator.compare(it, current) < 0
                    }
                    ?.let {
                        best = it
                        if (it.machines.size < maxLength) maxLength = it.machines.size
                    }
            }
            cover(key, -1)
        }

        /**
         * The band built from cycling [recipeCycle]: the pattern repeated often enough to cover
         * every recipe's plan machine need, cut to the shortest window at which no consumer is
         * underfed and no producer is left without a consumer next to it. Null when even
         * [maxBandMachines] machines cannot do it.
         */
        private fun window(): BandPlan? {
            val repetitions =
                needs.maxOfOrNull { (recipeIndex, count) ->
                    if (count <= FEED_EPSILON) 0
                    else ceil(count / counts.getValue(recipeIndex) - FEED_EPSILON).toInt()
                } ?: 0
            val start = repetitions.coerceAtLeast(1) * recipeCycle.size
            for (length in start..maxLength) {

                val extendedCycle = IntArray(length)

                for (offset in recipeCycle.indices) {
                    for (position in 0 until length) {
                        extendedCycle[position] =
                            recipeCycle[(offset + position) % recipeCycle.size]
                    }

                    return FeedWindow(extendedCycle).plan(requireComplete) ?: continue
                }
            }
            return null
        }
    }

    /** How far the window's per-recipe machine counts are from the links' per-machine rates */
    private fun ratioPenalty(extendedCycleIndices: IntArray): Double {
        // counts per group recipe, addressed by its position in [recipeIndices] rather than by
        // its recipe index
        val machinesPerRecipe = IntArray(recipeIndices.size)
        for (cycleIndex in extendedCycleIndices) {
            machinesPerRecipe[cycleIndex] += 1
        }
        return links.sumOf { linkPenalty(machinesPerRecipe, it) }
    }

    private fun linkPenalty(machinesPerRecipe: IntArray, link: FeedLink): Double {
        val wanted = link.ioRatio
        val p = machinesPerRecipe[recipePositions[link.producer.index]]
        val c = machinesPerRecipe[recipePositions[link.consumer.index]]

        val diff = 1.0 - p / (c * wanted)
        return diff * if (diff < 0.0) -OVERFEED_PENALTY else UNDERFEED_PENALTY
    }

    /**
     * Whether a solid material the band produces is still taken from a belt by a machine of the
     * same window. The band would need an extra belt for a resource it already makes; such a window
     * is not a valid band. A material produced and consumed outside the window (an export) is fine.
     */
    private fun hasInBandBeltInput(
        machines: List<FactoryProduction>,
        feeds: List<BandFeed>,
    ): Boolean {
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

    /** Whether every uncovered link can still become adjacent somewhere in the remaining cycle. */
    private fun linksStillReachable(lastRecipeIndex: RecipeIndex): Boolean {
        val hasRemaining = recipeIndices.any { remaining[it] > 0 }
        return links.withIndex().all { (index, link) ->
            covered[linkPairKeys[index]] > 0 || reachable(link, lastRecipeIndex, hasRemaining)
        }
    }

    private fun reachable(
        link: FeedLink,
        lastRecipeIndex: RecipeIndex,
        hasRemaining: Boolean,
    ): Boolean {
        val producerIndex = link.producer.index
        val consumerIndex = link.consumer.index
        val remainingProducer = remaining[producerIndex]
        val remainingConsumer = remaining[consumerIndex]
        val bothRemain =
            remainingProducer > 0 &&
                remainingConsumer > 0 &&
                (producerIndex != consumerIndex || remainingProducer >= 2)
        val afterLast =
            (lastRecipeIndex == producerIndex && remainingConsumer > 0) ||
                (lastRecipeIndex == consumerIndex && remainingProducer > 0)
        val viaWrap =
            hasRemaining &&
                ((producerIndex == firstRecipeIndex && remainingConsumer > 0) ||
                    (consumerIndex == firstRecipeIndex && remainingProducer > 0))
        return bothRemain || afterLast || viaWrap
    }

    private fun cover(key: Int, delta: Int) {
        covered[key] += delta
    }

    /**
     * The coverage slot of the two recipes' positions in [recipeIndices]. Adjacency has no
     * direction: the same gap realizes a link from either machine to the other, so `(a, b)` and
     * `(b, a)` share one slot.
     */
    private fun pairKey(first: Int, second: Int): Int {
        return minOf(first, second) * recipeIndices.size + maxOf(first, second)
    }

    fun arrangeBest(requireComplete: Boolean): BandPlan? {
        var best: BandPlan? = null
        for (length in recipeIndices.size..maxBandMachines(links.size)) {
            if (best != null && length > best.machines.size) break
            for (counts in countVectors(recipeIndices, length)) {
                if (counts.values.reduce(::greatestCommonDivisor) != 1) continue
                best = InnerBandSearch(counts, best, requireComplete).bestPlan()
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
    private inner class FeedWindow(private val extendedCycle: IntArray) {

        private val demand = run {
            val consumerCounts =
                IntArray(links.size).also {
                    for (position in extendedCycle) {
                        for (linkIndex in linksByConsumerPosition[position]) {
                            it[linkIndex]++
                        }
                    }
                }
            DoubleArray(links.size) {
                val link = links[it]
                (needs[link.consumer.index] ?: 0.0) * link.inputPerMachine / consumerCounts[it]
            }
        }

        private val length = extendedCycle.size
        private val supply = DoubleArray(length * links.size)
        private val remaining = DoubleArray(length) { outputPerRecipe[extendedCycle[it]] }
        private val used = BooleanArray(length)
        private val feeds = arrayOfNulls<BandFeed>(length - 1)

        @Suppress("ReturnCount")
        private fun feeds(): List<BandFeed>? {
            // seedSupply
            for (position in extendedCycle.indices) {
                val producer = extendedCycle[position]
                for (linkIndex in linksByProducerPosition[producer]) {
                    supply[position * links.size + linkIndex] = links[linkIndex].outputPerMachine
                }
            }

            for (position in extendedCycle.indices) {
                val consumer = extendedCycle[position]
                for (linkIndex in linksByConsumerPosition[consumer]) {
                    if (!consume(position, links[linkIndex], linkIndex)) return null
                }
            }

            // everythingUsed
            for (position in extendedCycle.indices) {
                val producer = extendedCycle[position]
                if (linksByProducerPosition[producer].isNotEmpty() && !used[position]) {
                    return null
                }
            }

            return feeds.filterNotNull()
        }

        private fun consume(position: Int, link: FeedLink, linkIndex: Int): Boolean {
            var demandLeft = demand[linkIndex]

            fun consume(side: Int, gap: Int) {
                val slot = side * links.size + linkIndex
                val taken = minOf(supply[slot], remaining[side], demandLeft)
                if (taken <= FEED_EPSILON) return
                supply[slot] -= taken
                remaining[side] -= taken
                used[side] = true

                val known = feeds[gap]?.flow ?: 0.0
                feeds[gap] = BandFeed(gap, link, known + taken, side <= gap)
                demandLeft -= taken
            }

            val left = position - 1
            val right = position + 1

            if (position > 0 && recipeIndices[extendedCycle[left]] == link.producer.index) {
                consume(left, left)
            }

            if (right < length && recipeIndices[extendedCycle[right]] == link.producer.index) {
                consume(right, position)
            }

            return demandLeft <= FEED_EPSILON
        }

        /** The plan for the window described by [extendedCycle] , or null when its flow fails. */
        fun plan(requireComplete: Boolean): BandPlan? {
            val feeds = feeds() ?: return null
            val machines = extendedCycle.map { ctx.factories.getValue(recipeIndices[it]) }

            if (requireComplete && hasInBandBeltInput(machines, feeds)) return null

            val ratioPenalty = ratioPenalty(extendedCycle)
            return BandPlan(machines, feeds, ratioPenalty)
        }
    }
}

private fun DirectFeedContext.footPrintsHaveDifferentSize(recipeIndices: Iterable<RecipeIndex>) =
    recipeIndices
        .asSequence()
        .map { blueprintMachineFootprint(factories.getValue(it).factory) }
        .zipWithNext()
        .any { (a, b) -> a != b }

private fun compareMachineIndices(planA: BandPlan, planB: BandPlan) =
    planA.machines
        .zip(planB.machines) { machineA, machineB ->
            machineA.index.value.compareTo(machineB.index.value)
        }
        .firstOrNull { it != 0 } ?: 0

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
