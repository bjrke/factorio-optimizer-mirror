package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestLikeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.TransportEntity
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.core.model.Direction

/**
 * The hover text of one blueprint entity: what the blueprint says about it, plus the layout facts
 * the blueprint cannot carry (belt lanes, the material an inserter moves, the fluid in a pipe).
 *
 * [index] is the entity's position in the blueprint, which is also its one-based `entity_number`
 * and the number wires and neighbours refer to. The position is the area's top-left, the same
 * coordinate the ASCII layout dump uses, so a tooltip can be matched against `--layout` output.
 * [placed] is the layout entity at the same index; a missing one only drops the extra facts.
 */
internal fun blueprintTitle(index: Int, entity: BlueprintEntity, placed: LayoutEntity?): String {
    val area = entity.area
    val lines = buildList {
        add("#${index + 1} ${entity.name}")
        add("at (${area.topLeft.x}, ${area.topLeft.y}) ${area.width}x${area.height}")
        if (entity.direction != Direction.NORTH) {
            add("direction: ${entity.direction.name.lowercase()}")
        }
        addAll(entity.settingLines())
        addAll(placed?.transportFacts(entity).orEmpty())
    }
    return lines.joinToString("\n")
}

/** The optional fields the blueprint itself carries, in the order the wire format writes them. */
private fun BlueprintEntity.settingLines(): List<String> = buildList {
    recipe?.let { add("recipe: $it") }
    type?.let { add("type: ${it.name.lowercase()}") }
    marker?.let { add("signal: ${it.name}") }
    infinity?.let { settings ->
        add(settings.item?.let { "provides: ${it.name}" } ?: "voids everything")
    }
    infinityPipe?.let { pipe ->
        add("fluid: ${pipe.fluid.name}, ${pipe.temperature}°, ${pipe.percentage * 100}%")
    }
    requestFilter?.let { filter ->
        add("request: ${filter.name} x${filter.count}")
        if (filter.trashNotRequested) add("trash unrequested")
    }
    bar?.let { add("bar: $it") }
    circuitCondition?.let { add("circuit: ${it.name} ${it.comparator} ${it.constant}") }
    if (mirror) add("mirrored")
    if (neighbours.isNotEmpty()) add("wired to: ${neighbours.joinToString { "#$it" }}")
}

/**
 * The facts only the placed entity knows. An infinity pipe's fluid is already on its settings line,
 * and a mirror is a blueprint field, so both are left out here.
 */
private fun LayoutEntity.transportFacts(entity: BlueprintEntity): List<String> =
    when (this) {
        is BeltEntity ->
            listOf("lanes left=${load.left?.name ?: "-"} right=${load.right?.name ?: "-"}")
        is InserterEntity ->
            buildList {
                add("material: ${material.name}")
                if (long) add("long-handed")
            }
        is Plumbing ->
            if (entity.infinityPipe == null) listOf("fluid: ${material.name}") else emptyList()
        is TransportEntity -> listOf("material: ${material.name}")
        is MachineEntity,
        is MarkerEntity,
        is PoleEntity,
        is ChestLikeEntity -> emptyList()
    }
