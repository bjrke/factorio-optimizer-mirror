package de.bjrke.factorio.optimizer.common.game.selection

import de.bjrke.factorio.optimizer.common.game.prototype.Surface

private val surfacesByCode = Surface.entries.filter { it.code != null }.associateBy { it.code }

/** Scans the `selection` URL parameter for a surface's single code; the default is Nauvis. */
fun surfaceFromUrl(s: String?) = s?.firstNotNullOfOrNull { surfacesByCode[it] }

/** Resolves the CLI's `--planet=` value: an exact [Surface.identifier] or a single code. */
fun surfaceFromPlanet(s: String?) =
    Surface.entries.firstOrNull { it.identifier == s } ?: surfaceFromUrl(s)
