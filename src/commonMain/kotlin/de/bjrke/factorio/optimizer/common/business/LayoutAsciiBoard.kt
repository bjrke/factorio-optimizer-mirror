package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/** Codes for the materials on belts and in pipes; cycled if a blueprint ever uses more. */
internal const val MATERIAL_CODES = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Columns per ruler digit in the layout header. */
private const val RULER_STEP = 10

/** The size line and the ruler-columned tile grid of a layout, ready to be printed. */
internal class AsciiBoard(val header: String, val grid: String)

/** Draws the tiles of [entities] below a size line and an x ruler, so a failure can be located. */
internal fun asciiBoard(entities: List<LayoutEntity>): AsciiBoard {
    val minX = entities.minOf { it.area.topLeft.x }
    val minY = entities.minOf { it.area.topLeft.y }
    val maxX = entities.maxOf { it.area.bottomRight.x }
    val maxY = entities.maxOf { it.area.bottomRight.y }
    val width = maxX - minX + 1
    val height = maxY - minY + 1

    val codes = AsciiCodes(entities)
    val grid = Array(height) { CharArray(width) { ' ' } }
    for (entity in entities) {
        for (tile in entity.area) grid[tile.y - minY][tile.x - minX] = codes.of(entity)
    }

    val labelWidth = maxY.toString().length
    val prefix = " ".repeat(labelWidth + 1)
    val drawn = buildString {
        append(prefix)
        for (x in minX..maxX) append(
            if (x % RULER_STEP == 0) (x / RULER_STEP) % RULER_STEP else ' ',
        )
        append('\n')
        for (row in 0 until height) {
            append((minY + row).toString().padStart(labelWidth))
            append(' ')
            append(grid[row])
            append('\n')
        }
    }
    return AsciiBoard("layout ${width}x$height\n", drawn)
}

/**
 * The character for every entity: machines count up in digits, belts show their material's code,
 * and inserters and loaders point the way their material goes.
 */
internal class AsciiCodes internal constructor(entities: List<LayoutEntity>) {
    private val listedMaterials = materials(entities)
    private val materialCodes =
        listedMaterials.withIndex().associate { (index, material) ->
            material to MATERIAL_CODES[index % MATERIAL_CODES.length]
        }

    private val listedRecipes =
        entities.filterIsInstance<MachineEntity>().map { it.recipe }.distinct()
    private val recipeCodes =
        listedRecipes.withIndex().associate { (index, recipe) -> recipe to '1' + index }

    /** The materials in code order, i.e. the order the legend lists them. */
    fun materials(): List<Material> = listedMaterials

    fun materialCode(material: Material): Char = materialCodes.getValue(material)

    /** The recipes in code order, i.e. the order the legend lists their machines. */
    fun recipes(): List<String> = listedRecipes

    fun recipeCode(recipe: String): Char = recipeCodes.getValue(recipe)

    fun of(entity: LayoutEntity): Char =
        when (entity) {
            is MachineEntity -> recipeCodes.getValue(entity.recipe)
            is InserterEntity -> arrow(entity.direction)
            is BeltEntity -> materialCodes.getValue(entity.material)
            is UndergroundBeltEntity -> if (entity.io == Io.INPUT) '(' else ')'
            is SplitterEntity -> '='
            is LoaderEntity -> arrow(entity.direction)
            is ChestLikeEntity -> chestCode(entity)
            is MarkerEntity -> 'C'
            is Plumbing -> plumbingCode(entity)
            is PoleEntity -> '+'
        }

    /** Chests and the infinity chest, one letter each. */
    private fun chestCode(chest: ChestLikeEntity): Char =
        when (chest) {
            is InfinityChestEntity -> '#'
            is ChestEntity -> chestGlyph(chest.name)
        }

    /** Pipes, pipe-to-ground ends and the infinity pipe, one letter each. */
    private fun plumbingCode(plumbing: Plumbing): Char =
        when (plumbing) {
            is PipeEntity -> '-'
            is InfinityPipeEntity -> '*'
            is PipeToGroundEntity -> 'o'
        }
}

/** The materials a layout moves, in name order: the order the legend codes follow. */
private fun materials(entities: List<LayoutEntity>): List<Material> =
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

/** Direction arrows for inserters and loaders, which point their material instead of naming it. */
private fun arrow(direction: Direction) =
    when (direction) {
        Direction.NORTH -> '^'
        Direction.SOUTH -> 'v'
        Direction.EAST -> '>'
        Direction.WEST -> '<'
    }

/** The glyph of a chest marker, one letter per chest type. */
internal fun chestGlyph(name: String) =
    when {
        name.startsWith("buffer") -> 'B'
        name.startsWith("storage") -> 'S'
        name.startsWith("wooden") -> 'W'
        else -> 'T'
    }
