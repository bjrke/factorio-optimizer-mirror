package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io

/** A built layout plus the feed routes that could not be placed, for the CLI's text rendering. */
internal data class LayoutDump(val entities: List<LayoutEntity>, val failures: List<String>)

/** Codes for the materials on belts and in pipes; cycled if a blueprint ever uses more. */
private const val MATERIAL_CODES = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Direction arrows for inserters and loaders, which point their material instead of naming it. */
private fun arrow(direction: Direction) =
    when (direction) {
        Direction.NORTH -> '^'
        Direction.SOUTH -> 'v'
        Direction.EAST -> '>'
        Direction.WEST -> '<'
    }

/**
 * Renders a [dump] as one character per tile so a layout can be eyeballed in a terminal: machines
 * are digits, belts show the code of the material they carry, and every other entity has its own
 * glyph. A legend decodes the codes and the failures are listed below the grid.
 */
internal fun layoutAscii(dump: LayoutDump): String {
    val entities = dump.entities
    if (entities.isEmpty()) return "layout: empty\n"

    val minX = entities.minOf { it.area.topLeft.x }
    val minY = entities.minOf { it.area.topLeft.y }
    val maxX = entities.maxOf { it.area.bottomRight.x }
    val maxY = entities.maxOf { it.area.bottomRight.y }
    val width = maxX - minX + 1
    val height = maxY - minY + 1

    val materials =
        entities
            .flatMap { entity ->
                when (entity) {
                    is TransportEntity -> listOf(entity.material)
                    is MarkerEntity -> listOf(entity.signal.material)
                    else -> emptyList()
                }
            }
            .distinct()
            .sortedBy { it.name }
    val materialCode =
        materials.withIndex().associate { (index, material) ->
            material to MATERIAL_CODES[index % MATERIAL_CODES.length]
        }

    val machines = entities.filterIsInstance<MachineEntity>()
    val recipes = machines.map { it.recipe }.distinct()
    val recipeCode = recipes.withIndex().associate { (index, recipe) -> recipe to ('1' + index) }

    fun glyph(entity: LayoutEntity) =
        when (entity) {
            is MachineEntity -> recipeCode.getValue(entity.recipe)
            is InserterEntity -> arrow(entity.direction)
            is BeltEntity -> materialCode.getValue(entity.material)
            is UndergroundBeltEntity -> if (entity.io == Io.INPUT) '(' else ')'
            is SplitterEntity -> '='
            is LoaderEntity -> arrow(entity.direction)
            is InfinityChestEntity -> '#'
            is MarkerEntity -> 'C'
            is PipeEntity -> '-'
            is PipeToGroundEntity -> 'o'
            is PoleEntity -> '+'
        }

    val grid = Array(height) { CharArray(width) { ' ' } }
    for (entity in entities) {
        for (tile in entity.area) grid[tile.y - minY][tile.x - minX] = glyph(entity)
    }

    val labelWidth = maxY.toString().length
    val prefix = " ".repeat(labelWidth + 1)
    return buildString {
        append("layout ${width}x$height\n")
        // x ruler: the tens digit at every tenth column, so a failure's (x, y) can be located
        append(prefix)
        for (x in minX..maxX) append(if (x % 10 == 0) (x / 10) % 10 else ' ')
        append('\n')
        for (row in 0 until height) {
            append((minY + row).toString().padStart(labelWidth))
            append(' ')
            append(grid[row])
            append('\n')
        }

        append("\nlegend\n")
        for ((recipe, code) in recipeCode) {
            val machine = machines.first { it.recipe == recipe }
            append("  $code = ${machine.name}: $recipe\n")
        }
        for ((material, code) in materialCode) append("  $code = ${material.name}\n")
        entities
            .filterIsInstance<MarkerEntity>()
            .map { it.signal.material }
            .distinct()
            .sortedBy { it.name }
            .forEach { append("  C = constant-combinator: ${it.name}\n") }
        if (entities.any { it is InfinityChestEntity }) append("  # = infinity-chest\n")

        if (dump.failures.isNotEmpty()) {
            append("\nfailures (${dump.failures.size})\n")
            dump.failures.forEach { append("  $it\n") }
        }
    }
}
