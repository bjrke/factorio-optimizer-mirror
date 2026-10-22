package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.layout.ChestEntity
import de.bjrke.factorio.optimizer.common.layout.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.layout.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.layout.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.MarkerEntity

/**
 * The legend that decodes the board's codes: the built recipes, the materials, the signals of the
 * constant combinators, the two infinity entities and the chest types, each in code order.
 */
internal fun asciiLegend(entities: List<LayoutEntity>): String {
    val codes = AsciiCodes(entities)
    val machines = entities.filterIsInstance<MachineEntity>()
    val legend = StringBuilder()
    for (recipe in codes.recipes()) {
        val machine = machines.first { it.recipe == recipe }
        legend.append("  ${codes.recipeCode(recipe)} = ${machine.name}: $recipe\n")
    }
    for (material in codes.materials()) {
        legend.append("  ${codes.materialCode(material)} = ${material.name}\n")
    }
    return legend
        .append(markerLegend(entities))
        .append(infinityLegend(entities))
        .append(chestLegend(entities))
        .toString()
}

/** One line per signal a constant combinator carries. */
private fun markerLegend(entities: List<LayoutEntity>): String =
    entities
        .filterIsInstance<MarkerEntity>()
        .map { it.signal.material }
        .distinct()
        .sortedBy { it.name }
        .joinToString("") { "  C = constant-combinator: ${it.name}\n" }

/** The fixed infinity entity codes, listed when the layout holds one of them. */
private fun infinityLegend(entities: List<LayoutEntity>): String = buildString {
    if (entities.any { it is InfinityChestEntity }) append("  # = infinity-chest\n")
    if (entities.any { it is InfinityPipeEntity }) append("  * = infinity-pipe\n")
}

/** One line per chest type, under the letter [chestGlyph] gives it. */
private fun chestLegend(entities: List<LayoutEntity>): String =
    entities
        .filterIsInstance<ChestEntity>()
        .map { it.name }
        .distinct()
        .sorted()
        .joinToString("") { "  ${chestGlyph(it)} = $it\n" }
