package de.bjrke.factorio.optimizer.common.game.selection

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.game.prototype.Surface

/**
 * The marker selected by a `selection` URL parameter, clamped to what [surface] allows: a chest
 * code on a space platform, or an absent code, falls back to the constant-combinator label.
 */
fun beltEndFromSelection(selection: String?, surface: Surface): BeltEnd =
    selection?.firstNotNullOfOrNull { BeltEnd.byCode[it] }?.takeIf { it.allowedOn(surface) }
        ?: BeltEnd.CONSTANT_COMBINATOR

/**
 * Resolves a CLI value strictly: a full [BeltEnd.identifier] or exactly one selection code. Returns
 * null for anything else, so the caller can reject it instead of selecting another marker.
 */
fun beltEndFromIdentifier(value: String): BeltEnd? =
    BeltEnd.entries.firstOrNull { it.identifier == value }
        ?: value.singleOrNull()?.let { BeltEnd.byCode[it] }
