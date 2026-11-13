package de.bjrke.factorio.optimizer.common.blueprint.printing.output

import de.bjrke.factorio.optimizer.common.game.production.normalizeTargetFromUrl

/**
 * The seven path segments of a Factorio-Optimizer web URL, decoded far enough that the CLI can run
 * the same selection the browser had:
 *
 * `localhost:8080/#/<normalizeTarget>/<target>/<selection>/<beltStack>/<inserterStack>/<intermediates>/<excluded>`
 *
 * The selection string is kept raw; [surfaceFromUrl], [FactoryConfig.fromUrl] and
 * [LogisticsConfig.belt]/[inserter]/[beltLanes] decode it the same way the web UI does. The
 * intermediate limit is kept raw too, so the caller can clamp it with `validIntermediateLimit`. The
 * excluded segment is the raw comma-separated material list; `excludedMaterialsFromUrl` decodes it.
 */
data class FactorioUrl(
    val normalizeTarget: Double,
    val target: String,
    val selection: String?,
    val beltStack: String?,
    val inserterStack: String?,
    val intermediates: String?,
    val excluded: String? = null,
)

/**
 * Parses a Factorio-Optimizer web URL in any form a user might paste: a full
 * `http://localhost:8080/#/...`, a host and hash without scheme (`localhost:8080/#/...`) or a bare
 * `#/...`. Returns null when [text] is not such a URL (no `#`, or too few path segments).
 */
fun parseFactorioUrl(text: String): FactorioUrl? {
    val hash = text.indexOf('#')
    if (hash < 0) return null
    // everything after a `?` is a query, not part of the path
    val fragment = text.substring(hash + 1).substringBefore('?')
    val segments = fragment.trim('/').split('/').filter { it.isNotEmpty() }
    // normalizeTarget and target are required; the later segments default when absent
    if (segments.size < 2) return null
    return FactorioUrl(
        normalizeTarget = normalizeTargetFromUrl(segments[0]),
        target = segments[1],
        selection = segments.getOrNull(2),
        beltStack = segments.getOrNull(3),
        inserterStack = segments.getOrNull(4),
        intermediates = segments.getOrNull(5),
        excluded = segments.getOrNull(6),
    )
}
