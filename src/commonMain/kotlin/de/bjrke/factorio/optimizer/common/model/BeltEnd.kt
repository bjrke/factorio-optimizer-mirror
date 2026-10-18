package de.bjrke.factorio.optimizer.common.model

/**
 * How a solid belt is terminated: the plain constant-combinator label (the default), an infinity
 * chest (a cheat test source/sink) or one of the chest markers that actually move items.
 *
 * Chest markers need ground, so a space platform only allows the constant-combinator label; every
 * other surface offers all of them.
 */
enum class BeltEnd(val code: Char?, val identifier: String) {
    CONSTANT_COMBINATOR(null, "constant-combinator"),
    INFINITY_CHEST('X', "infinity-chest"),
    BUFFER_CHEST('H', "buffer-chest"),
    STORAGE_CHEST('G', "storage-chest"),
    WOODEN_CHEST('W', "wooden-chest"),
    STEEL_CHEST('D', "steel-chest");

    /** Whether this marker may be built on [surface]; every chest needs ground. */
    fun allowedOn(surface: Surface) = this == CONSTANT_COMBINATOR || surface != Surface.SPACE

    /** Whether this is a chest marker (rather than the plain constant-combinator label). */
    val isChest: Boolean
        get() = this != CONSTANT_COMBINATOR

    companion object {
        private val byCode = entries.filter { it.code != null }.associateBy { it.code }

        /**
         * The marker selected by [selection], clamped to what [surface] allows: a chest code on a
         * space platform, or an absent code, falls back to [CONSTANT_COMBINATOR].
         */
        fun fromSelection(selection: String?, surface: Surface): BeltEnd =
            selection?.firstNotNullOfOrNull { byCode[it] }?.takeIf { it.allowedOn(surface) }
                ?: CONSTANT_COMBINATOR

        /** Resolves a CLI value: a full [identifier] or a single selection [code]. */
        fun fromIdentifier(value: String) =
            entries.firstOrNull { it.identifier == value }
                ?: byCode[value.firstOrNull()]
                ?: CONSTANT_COMBINATOR
    }
}
