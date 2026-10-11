package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Material

private const val MAX_SEARCH_RESULTS = 100

/**
 * Filters and ranks [materials] for a searchable dropdown.
 *
 * Matching is case-insensitive and word based: names and the [query] are split on whitespace and
 * the `-`/`_` separators, and every query token has to match somewhere in the material name. A
 * token matches a word exactly, as a prefix, as a substring, spanning separator boundaries or - as
 * a last resort - as a subsequence (fuzzy search). Results are ordered by match quality and then by
 * name.
 *
 * A blank [query] returns the first [limit] materials unchanged.
 */
fun searchMaterials(
    materials: List<Material>,
    query: String,
    limit: Int = MAX_SEARCH_RESULTS,
): List<Material> {
    val tokens = tokenize(query)
    if (tokens.isEmpty()) return materials.take(limit)
    return materials
        .mapNotNull { material ->
            val candidate = Candidate(material.name)
            val scores = tokens.map { candidate.score(it) }
            if (scores.any { it == null }) null else Match(material, scores.filterNotNull().sum())
        }
        .sortedWith(compareBy({ it.score }, { it.material.name }))
        .take(limit)
        .map { it.material }
}

private data class Match(val material: Material, val score: Int)

private class Candidate(name: String) {
    private val words = tokenize(name)
    private val compact = name.lowercase().filter { it.isLetterOrDigit() }

    fun score(token: String): Int? {
        val scores = buildList {
            for (word in words) {
                if (word == token) add(0)
                if (word.startsWith(token)) add(1)
                if (word.contains(token)) add(2)
            }
            if (compact.contains(token)) add(3)
            if (isSubsequence(token, compact)) add(4)
        }
        return scores.minOrNull()
    }
}

private fun tokenize(value: String): List<String> =
    value.lowercase().split('-', '_', ' ', '\t', '\n', '\r').filter { it.isNotBlank() }

private fun isSubsequence(needle: String, haystack: String): Boolean {
    if (needle.isEmpty()) return true
    var index = 0
    for (character in haystack) {
        if (character == needle[index]) {
            index++
            if (index == needle.length) return true
        }
    }
    return false
}
