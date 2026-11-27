package de.bjrke.factorio.optimizer.common.game.selection

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.context.MaterialRegistry

/**
 * Placeholder for the `excluded` URL segment when no material is excluded. It is not a material
 * name and decodes to the empty set; it only keeps the segment present so the route always matches.
 */
const val NO_EXCLUSIONS = "-"

/**
 * The materials the `excluded` URL segment names, sorted and comma-separated. An excluded material
 * is imported instead of built: the optimizer drops every recipe that produces it, so the plan
 * neither calculates nor lays out its production. The target is never excluded.
 *
 * A name this universe does not have is dropped instead of interned: a typo or a hand-edited URL
 * must not grow the registry or invent a material nothing produces.
 */
fun MaterialRegistry.excludedMaterialsFromUrl(segment: String?): Set<Material> =
    segment
        ?.split(',')
        ?.filter { it.isNotEmpty() && it != NO_EXCLUSIONS }
        ?.mapNotNullTo(mutableSetOf()) { existing(it) }
        .orEmpty()

/** Encodes [materials] into the `excluded` URL segment; the empty set becomes [NO_EXCLUSIONS]. */
fun excludedMaterialsToUrl(materials: Set<Material>): String =
    materials.map { it.name }.sorted().joinToString(",").ifEmpty { NO_EXCLUSIONS }
