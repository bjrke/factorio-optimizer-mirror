package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_ROW_BELTS
import de.bjrke.factorio.optimizer.common.core.util.MinHeap
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.math.ceil
import kotlin.math.max

// Maximal grouping for direct feed: a group keeps growing while an arrangement exists, merging
// groups that meet and staying inside the machine budget.

/**
 * Grows groups maximally and returns the best arrangement of every finished group. A seed grows by
 * adding the best adjacent link that still arranges, retries links after every growth and tries a
 * single bounded swap when a better link does not fit. Growth may pass through a group that needs
 * too many belts, because adding a producer can internalize an ingredient and bring the row back
 * under the limit; only the finished group is filtered. A seed whose group never fits is dropped
 * and its links return to the pool, so another seed can still use them. Groups that meet later are
 * merged when the merged group arranges and fits.
 */
internal fun DirectFeedContext.maximalGroups(): List<BandPlan> {
    val pool = candidateLinks().toMutableList()
    val groups = mutableListOf<MutableList<FeedLink>>()
    val assigned = mutableSetOf<FactoryProduction>()
    val cache = mutableMapOf<List<FeedLink>, BandPlan?>()

    fun arranged(links: List<FeedLink>) = cache.getOrPut(linkKey(links)) { arrange(links) }

    while (true) {
        pool.sortWith(linkComparator)
        val seed = pool.indexOfFirst { link -> link.productions().none { it in assigned } }
        if (seed < 0) break
        val seedLink = pool.removeAt(seed)
        val group = mutableListOf(seedLink)
        growGroup(group, pool, ::arranged)
        if (arranged(group) == null || !bandBeltFits(group)) {
            // the best arrangement of this seed still leaves a lane without a belt; retry the links
            // it gathered from another seed
            pool += group.filterNot { it == seedLink }
            continue
        }
        mergeIntoGroup(group, groups, ::arranged)
        assigned += group.flatMap { it.productions() }
        groups += group
    }
    mergeGroups(groups, ::arranged)
    return groups.mapNotNull { arranged(it) }
}

private fun FeedLink.productions() = listOf(producer.production, consumer.production)

/**
 * Whether [group] may grow by [candidate]. A group that already fits the row's belt budget may only
 * add a link that keeps the combined group fitting, so a link that would push a good row over the
 * limit is skipped and another candidate gets a chance. A group over the budget may still grow: a
 * later link can internalize the ingredient that made it too wide, which is why the filter runs
 * only on the finished group.
 */
private fun DirectFeedContext.mayGrow(group: List<FeedLink>, candidate: FeedLink): Boolean {
    val combined = group + candidate
    if (!groupMayFit(combined)) return false
    if (bandBeltMaterials(group).size > MAX_ROW_BELTS) return true
    return bandBeltMaterials(combined).size <= MAX_ROW_BELTS
}

private tailrec fun DirectFeedContext.growGroup(
    group: MutableList<FeedLink>,
    pool: MutableList<FeedLink>,
    arranged: (List<FeedLink>) -> BandPlan?,
) {

    val adjacent =
        MinHeap(
                linkComparator,
                pool.filter { candidate -> group.any { sharesRecipe(it, candidate) } },
            )
            .drainAll()

    for (candidate in adjacent) {
        if (!mayGrow(group, candidate)) continue
        if (arranged(group + candidate) != null) {
            group += candidate
            pool.remove(candidate)
            return growGroup(group, pool, arranged)
        }
        if (swapInto(group, candidate, pool, arranged)) {
            return growGroup(group, pool, arranged)
        }
    }
}

private fun DirectFeedContext.swapInto(
    group: MutableList<FeedLink>,
    candidate: FeedLink,
    pool: MutableList<FeedLink>,
    arranged: (List<FeedLink>) -> BandPlan?,
): Boolean {
    if (group.size <= 1) return false
    val weakest = group.minWithOrNull(linkComparator) ?: return false
    if (candidate.candidateScore <= weakest.candidateScore + FEED_EPSILON) return false
    val trial = group.filterNot { it == weakest } + candidate
    if (!groupMayFit(trial)) return false
    if (
        bandBeltMaterials(group).size <= MAX_ROW_BELTS &&
            bandBeltMaterials(trial).size > MAX_ROW_BELTS
    )
        return false
    if (arranged(trial) == null) return false
    group.remove(weakest)
    group += candidate
    pool.remove(candidate)
    pool += weakest
    return true
}

private fun DirectFeedContext.mergeIntoGroup(
    group: MutableList<FeedLink>,
    groups: MutableList<MutableList<FeedLink>>,
    arranged: (List<FeedLink>) -> BandPlan?,
) {
    while (true) {
        val index =
            groups.indices.firstOrNull { index ->
                val combined = groups[index] + group
                groupMayFit(combined) &&
                    bandBeltFits(combined) &&
                    group.any { link -> groups[index].any { sharesRecipe(it, link) } } &&
                    arranged(combined) != null
            } ?: return
        val combined = (groups[index] + group).distinct()
        group.clear()
        group += combined
        groups.removeAt(index)
    }
}

private fun DirectFeedContext.mergeGroups(
    groups: MutableList<MutableList<FeedLink>>,
    arranged: (List<FeedLink>) -> BandPlan?,
) {
    var merged = true
    while (merged) {
        merged = false
        outer@ for (first in groups.indices) {
            for (second in first + 1 until groups.size) {
                val combined = groups[first] + groups[second]
                if (!groupAnyShared(groups[first], groups[second])) continue
                if (!groupMayFit(combined) || !bandBeltFits(combined) || arranged(combined) == null)
                    continue
                groups[first] = combined.distinct().toMutableList()
                groups.removeAt(second)
                merged = true
                break@outer
            }
        }
    }
}

private fun groupAnyShared(first: List<FeedLink>, second: List<FeedLink>) = first.any { link ->
    second.any { sharesRecipe(it, link) }
}

/** Whether two links belong to one band: one feeds the other, or they share a consumer. */
private fun sharesRecipe(first: FeedLink, second: FeedLink) =
    first.consumer == second.producer ||
        second.consumer == first.producer ||
        first.consumer == second.consumer

/**
 * A quick machine budget before the expensive arrangement search: one band cannot hold more than
 * [maxBandMachines] machines, and the arrangement search stops at [MAX_BAND_RECIPES] recipes.
 */
private fun DirectFeedContext.groupMayFit(links: List<FeedLink>): Boolean {
    val nodes = links.flatMap { listOf(it.producer, it.consumer) }.distinct()
    if (nodes.size > MAX_BAND_RECIPES) return false
    val budget = maxBandMachines(links.size)
    val machines = nodes.sumOf { node ->
        ceil(max(1.0, node.production.amount * scale) - FEED_EPSILON)
            .toInt()
            .coerceAtMost(budget + 1)
    }
    return machines <= budget
}
