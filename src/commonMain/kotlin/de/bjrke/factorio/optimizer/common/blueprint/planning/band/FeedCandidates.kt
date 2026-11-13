package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.util.MinHeap
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

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
 *
 * A link of a [forcedMaterials] material sorts ahead of the score order, so an output whose belt
 * lane is too small is direct-fed first. Forcing only changes the seed order: a forced link that
 * cannot arrange or fit is still dropped like any other seed.
 */
internal fun DirectFeedContext.maximalGroups(
    candidates: List<FeedLink> = candidateLinks(),
    forcedMaterials: Set<Material> = emptySet(),
): List<BandPlan> {
    val pool = candidates.toMutableList()
    val groups = mutableListOf<MutableList<FeedLink>>()
    val assigned = mutableSetOf<FactoryProduction>()
    val completeCache = mutableMapOf<List<FeedLink>, BandPlan?>()
    val growthCache = mutableMapOf<List<FeedLink>, BandPlan?>()
    val seedComparator =
        compareBy<FeedLink> { it.material !in forcedMaterials }.then(linkComparator)

    fun arranged(links: List<FeedLink>) = completeCache.getOrPut(linkKey(links)) { arrange(links) }

    // growth may pass through a partial link: the added link can complete the band
    fun growthArranged(links: List<FeedLink>) =
        growthCache.getOrPut(linkKey(links)) { arrange(links, requireComplete = false) }

    while (true) {
        pool.sortWith(seedComparator)
        val seed = pool.indexOfFirst { link -> link.productions().none { it in assigned } }
        if (seed < 0) break
        val seedLink = pool.removeAt(seed)
        val group = mutableListOf(seedLink)
        growGroup(group, pool, assigned, ::growthArranged)
        val plan = arranged(group)
        if (plan == null || !bandBeltFits(plan)) {
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
    if (internalizesSharedOutput(group, candidate)) return false
    val combined = group + candidate
    if (!groupMayFit(combined)) return false
    if (!bandBeltMayFit(group)) return true
    return bandBeltMayFit(combined)
}

/**
 * Whether [candidate] would turn a product that currently leaves the group over a belt into an
 * internal link while another plan consumer still needs it. A band cannot export such a product, so
 * its other consumers would be stranded.
 */
internal fun DirectFeedContext.internalizesSharedOutput(
    group: List<FeedLink>,
    candidate: FeedLink,
): Boolean {
    val groupNodes = group.flatMap { it.productions() }.toSet()
    if (candidate.producer.production !in groupNodes) return false
    val groupProducers = group.mapTo(mutableSetOf()) { it.producer.production }
    if (candidate.producer.production in groupProducers) return false
    val nodes = groupNodes + candidate.consumer.production
    return consumersInPlan.any { it !in nodes && candidate.material in it.recipe.ingredients }
}

private tailrec fun DirectFeedContext.growGroup(
    group: MutableList<FeedLink>,
    pool: MutableList<FeedLink>,
    assigned: Set<FactoryProduction>,
    arranged: (List<FeedLink>) -> BandPlan?,
) {

    val groupNodes = group.flatMap { it.productions() }.toSet()
    val adjacent =
        MinHeap(
                linkComparator,
                pool.filter { candidate -> group.any { sharesRecipe(it, candidate) } },
            )
            .drainAll()

    for (candidate in adjacent) {
        // a production belongs to exactly one row: never grow into a group another seed assigned
        if (candidate.productions().any { it in assigned && it !in groupNodes }) continue
        if (!mayGrow(group, candidate)) continue
        if (arranged(group + candidate) != null) {
            group += candidate
            pool.remove(candidate)
            return growGroup(group, pool, assigned, arranged)
        }
        if (swapInto(group, candidate, pool, arranged)) {
            return growGroup(group, pool, assigned, arranged)
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
    val kept = group.filterNot { it == weakest }
    // test the group without the candidate: adding it first would hide the shared consumer
    if (internalizesSharedOutput(kept, candidate)) return false
    val trial = kept + candidate
    if (!groupMayFit(trial)) return false
    if (bandBeltMayFit(group) && !bandBeltMayFit(trial)) return false
    val plan = arranged(trial) ?: return false
    if (!bandBeltFits(plan)) return false
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
                if (
                    !groupMayFit(combined) ||
                        !bandBeltMayFit(combined) ||
                        !group.any { link -> groups[index].any { sharesRecipe(it, link) } }
                )
                    return@firstOrNull false
                val plan = arranged(combined) ?: return@firstOrNull false
                bandBeltFits(plan)
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
                val combined = mergedLinks(groups[first], groups[second], arranged) ?: continue
                groups[first] = combined.toMutableList()
                groups.removeAt(second)
                merged = true
                break@outer
            }
        }
    }
}

/** The distinct links of two groups that may merge, or null when the merge does not fit. */
private fun DirectFeedContext.mergedLinks(
    first: List<FeedLink>,
    second: List<FeedLink>,
    arranged: (List<FeedLink>) -> BandPlan?,
): List<FeedLink>? {
    if (!groupAnyShared(first, second)) return null
    val combined = first + second
    if (!groupMayFit(combined) || !bandBeltMayFit(combined)) return null
    val plan = arranged(combined) ?: return null
    return if (bandBeltFits(plan)) combined.distinct() else null
}

private fun groupAnyShared(first: List<FeedLink>, second: List<FeedLink>) = first.any { link ->
    second.any { sharesRecipe(it, link) }
}

/** Whether two links belong to one band: one feeds the other, or they share a consumer. */
private fun sharesRecipe(first: FeedLink, second: FeedLink) =
    first.consumer == second.producer ||
        second.consumer == first.producer ||
        first.consumer == second.consumer
