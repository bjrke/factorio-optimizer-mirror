package de.bjrke.factorio.optimizer.common.prototype.selection

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.prototype.Surface

/**
 * The marker selected by a `selection` URL parameter, clamped to what [surface] allows: a chest
 * code on a space platform, or an absent code, falls back to the constant-combinator label.
 */
fun beltEndFromSelection(selection: String?, surface: Surface): BeltEnd =
    selection?.firstNotNullOfOrNull { BeltEnd.byCode[it] }?.takeIf { it.allowedOn(surface) }
        ?: BeltEnd.CONSTANT_COMBINATOR

/** Resolves a CLI value: a full [BeltEnd.identifier] or a single selection code. */
fun beltEndFromIdentifier(value: String): BeltEnd =
    BeltEnd.entries.firstOrNull { it.identifier == value }
        ?: BeltEnd.byCode[value.firstOrNull()]
        ?: BeltEnd.CONSTANT_COMBINATOR
