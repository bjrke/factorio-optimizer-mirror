package de.bjrke.factorio.optimizer.common.game.selection

import de.bjrke.factorio.optimizer.common.game.prototype.Surface

private val surfacesByCode = Surface.entries.filter { it.code != null }.associateBy { it.code }

/**
 * The code the CLI accepts for Nauvis. It stays out of [surfacesByCode] because Nauvis is the
 * default surface and therefore carries no code in a URL; `--planet=n` still names it explicitly.
 */
private val cliSurfaceCodes = mapOf('n' to Surface.NAUVIS)

/** Scans the `selection` URL parameter for a surface's single code; the default is Nauvis. */
fun surfaceFromUrl(s: String?) = s?.firstNotNullOfOrNull { surfacesByCode[it] }

/**
 * Resolves the CLI's `--planet=` value strictly: an exact [Surface.identifier] or exactly one code
 * character. Returns null for anything else, so the caller can reject it.
 */
fun surfaceFromPlanet(s: String?) =
    Surface.entries.firstOrNull { it.identifier == s }
        ?: s?.singleOrNull()?.let { surfacesByCode[it] ?: cliSurfaceCodes[it] }
