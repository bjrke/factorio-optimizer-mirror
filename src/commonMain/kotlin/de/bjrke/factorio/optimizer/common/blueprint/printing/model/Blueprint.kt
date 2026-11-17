package de.bjrke.factorio.optimizer.common.blueprint.printing.model

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity

/**
 * One circuit network wire: the two entities it joins (their `entity_number`) and the connector
 * each end plugs into. A chest's circuit condition reads its contents, so the inserters reading it
 * must be wired to it. A wire is undirected; [first] and [second] just name its ends.
 */
data class BlueprintWire(
    val first: Int,
    val firstConnector: Int,
    val second: Int,
    val secondConnector: Int,
)

/**
 * One icon of the blueprint's icon strip: [name] is the prototype the signal names. [fluid] selects
 * the game's `fluid` signal type; every other icon is an item/entity signal, so no type is needed.
 */
data class BlueprintIcon(val name: String, val fluid: Boolean = false)

/** Factorio shows at most four icons on a blueprint. */
private const val MAX_ICONS = 4

/**
 * A planned blueprint: its [label], [icons], the [entities] the layout placed and their [wires].
 *
 * [entities] are the layout's own entities in placement order; an entity's index plus one is the
 * `entity_number` its [wires] and a pole's neighbours refer to. The JSON writer turns them into the
 * wire format, so this model needs no second entity representation.
 */
data class Blueprint(
    val label: String,
    val icons: List<BlueprintIcon> = emptyList(),
    val entities: List<LayoutEntity> = emptyList(),
    val wires: List<BlueprintWire> = emptyList(),
) {
    init {
        require(icons.size <= MAX_ICONS) { "a blueprint shows at most $MAX_ICONS icons" }
    }
}
