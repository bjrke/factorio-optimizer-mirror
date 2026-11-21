package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestLikeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.TransportEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.facingDirection
import de.bjrke.factorio.optimizer.common.core.model.Direction

/**
 * The hover text of one blueprint entity: what the blueprint says about it, plus the layout facts
 * the blueprint cannot carry (belt lanes, the material an inserter moves, the fluid in a pipe).
 *
 * [index] is the entity's position in the blueprint, which is also its one-based `entity_number`
 * and the number wires and neighbors refer to. The position is the area's top-left, the same
 * coordinate the ASCII layout dump uses, so a tooltip can be matched against `--layout` output.
 */
internal fun blueprintTitle(index: Int, entity: LayoutEntity): String {
    val area = entity.area
    val lines = buildList {
        add("#${index + 1} ${entity.name}")
        add("at (${area.x}, ${area.y}) ${area.width}x${area.height}")
        entity.facingDirection
            ?.takeIf { it != Direction.NORTH }
            ?.let {
                add("direction: ${it.name.lowercase()}")
            }
        addAll(entity.settingLines())
        addAll(entity.transportFacts())
    }
    return lines.joinToString("\n")
}

/** The optional fields the blueprint itself carries, in the order the wire format writes them. */
private fun LayoutEntity.settingLines(): List<String> = buildList {
    listOfNotNull(machineLine(), typeLine(), markerLine(), infinityChestLine(), infinityPipeLine())
        .forEach(::add)
    addAll(chestLines())
    addAll(inserterLines())
    poleLine()?.let(::add)
}

private fun LayoutEntity.machineLine(): String? =
    (this as? MachineEntity)?.let { "recipe: ${it.recipe}" }

/** An underground belt and a loader write an input/output type; a pipe-to-ground has none. */
private fun LayoutEntity.typeLine(): String? =
    when (this) {
        is UndergroundBeltEntity -> io.name.lowercase()
        is LoaderEntity -> io.name.lowercase()
        else -> null
    }?.let { "type: $it" }

private fun LayoutEntity.markerLine(): String? =
    (this as? MarkerEntity)?.let { "signal: ${it.signal.material.name}" }

private fun LayoutEntity.infinityChestLine(): String? =
    (this as? InfinityChestEntity)?.let {
        it.settings.item?.let { item -> "provides: ${item.name}" } ?: "voids everything"
    }

private fun LayoutEntity.infinityPipeLine(): String? =
    (this as? InfinityPipeEntity)?.settings?.let {
        "fluid: ${it.fluid.name}, ${it.temperature}°, ${it.percentage * 100}%"
    }

private fun LayoutEntity.chestLines(): List<String> =
    (this as? ChestEntity)
        ?.let { chest ->
            buildList {
                chest.requestFilter?.let { filter ->
                    add("request: ${filter.name} x${filter.count}")
                    if (filter.trashNotRequested) add("trash unrequested")
                }
                chest.bar?.let { add("bar: $it") }
            }
        }
        .orEmpty()

private fun LayoutEntity.inserterLines(): List<String> =
    (this as? InserterEntity)
        ?.let { inserter ->
            buildList {
                inserter.circuitCondition?.let {
                    add("circuit: ${it.name} ${it.comparator} ${it.constant}")
                }
                if (inserter.mirror) add("mirrored")
            }
        }
        .orEmpty()

private fun LayoutEntity.poleLine(): String? =
    (this as? PoleEntity)
        ?.takeIf { it.neighbors.isNotEmpty() }
        ?.let { "wired to: ${it.neighbors.joinToString { "#$it" }}" }

/**
 * The facts only the placed entity knows. An infinity pipe's fluid is already on its settings line,
 * and a mirror is a blueprint field, so both are left out here.
 */
private fun LayoutEntity.transportFacts(): List<String> =
    when (this) {
        is BeltEntity ->
            listOf("lanes left=${load.left?.name ?: "-"} right=${load.right?.name ?: "-"}")
        is InserterEntity ->
            buildList {
                add("material: ${material.name}")
                if (long) add("long-handed")
            }
        is Plumbing ->
            if (this is InfinityPipeEntity && settings != null) emptyList()
            else listOf("fluid: ${material.name}")
        is TransportEntity -> listOf("material: ${material.name}")
        is MachineEntity,
        is MarkerEntity,
        is PoleEntity,
        is ChestLikeEntity -> emptyList()
    }
