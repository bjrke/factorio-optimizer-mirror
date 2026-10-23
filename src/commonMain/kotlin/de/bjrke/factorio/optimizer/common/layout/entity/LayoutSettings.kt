package de.bjrke.factorio.optimizer.common.layout.entity

import de.bjrke.factorio.optimizer.common.model.Material

/**
 * An infinity chest's settings. With an [item] the chest keeps [item] available (a test input);
 * without one and [removeUnfiltered] it simply deletes whatever reaches it (a test output).
 */
data class InfinitySettings(val item: Material? = null, val removeUnfiltered: Boolean = false)

/**
 * An infinity pipe's settings: it keeps [fluid] available at 100% (the game writes 100% as
 * `percentage = 1`) and at [temperature]. `at-least` means the pipe tops the fluid up whenever it
 * drops below the percentage, so a connected factory can always draw from it.
 */
data class InfinityPipeSettings(
    val fluid: Material,
    val temperature: Int,
    val percentage: Int = 1,
    val mode: String = "at-least",
)

/**
 * A chest's logistic request: it keeps [count] of the item [name] available (a buffer chest
 * requests it, a storage chest filters it). [trashNotRequested] mirrors the buffer chest's "trash
 * unrequested" flag.
 */
data class RequestFilter(
    val name: String,
    val count: Int,
    val trashNotRequested: Boolean = false,
)

/**
 * An inserter's circuit condition: it runs while the item [name] compares [comparator] to
 * [constant].
 */
data class CircuitCondition(val name: String, val comparator: String, val constant: Int)
