package de.bjrke.factorio.optimizer.common.model

data class Material(val name: String) {
    companion object {
        private val materialsBuilder = mutableMapOf<String, Material>()

        fun of(name: String) = materialsBuilder.getOrPut(name) { Material(name) }
    }
}

data class ParsedRecipe(val materials: List<Material>, val recipes: List<Recipe>)

data class Recipe(
    val name: String,
    val ingredients: Map<Material, Double>,
    val result: Map<Material, Double>,
    val time: Double,
    val category: Category,
    val subgroup: Subgroup = Subgroup(),
    val surfaceConditions: List<SurfaceCondition> = emptyList(),
)

data class Subgroup(val name: String = "no subgroup")

enum class SurfaceProperty(val identifier: String) {
    PRESSURE("pressure"),
    GRAVITY("gravity"),
    MAGNETIC_FIELD("magnetic-field"),
}

private val surfacePropertyMap = SurfaceProperty.entries.associateBy { it.identifier }

fun surfaceProperty(identifier: String) = surfacePropertyMap[identifier]

data class SurfaceCondition(
    val property: SurfaceProperty,
    val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) {
    fun matches(value: Int?) = value != null && value in range
}

/**
 * A surface (planet or space platform) a factory can be built on. Recipe availability is expressed
 * as [SurfaceCondition]s against the physical properties listed in [properties].
 */
enum class Surface(
    val code: Char,
    val identifier: String,
    val properties: Map<SurfaceProperty, Int>,
) {
    NAUVIS('n', "nauvis", mapOf(SurfaceProperty.PRESSURE to 1000, SurfaceProperty.GRAVITY to 10)),
    VULCANUS(
        'v',
        "vulcanus",
        mapOf(SurfaceProperty.PRESSURE to 4000, SurfaceProperty.GRAVITY to 40),
    ),
    GLEBA('g', "gleba", mapOf(SurfaceProperty.PRESSURE to 2000, SurfaceProperty.GRAVITY to 20)),
    FULGORA(
        'f',
        "fulgora",
        mapOf(
            SurfaceProperty.PRESSURE to 1000,
            SurfaceProperty.GRAVITY to 15,
            SurfaceProperty.MAGNETIC_FIELD to 99,
        ),
    ),
    AQUILO('a', "aquilo", mapOf(SurfaceProperty.PRESSURE to 300, SurfaceProperty.GRAVITY to 10)),
    SPACE(
        's',
        "space-platform-surface",
        mapOf(SurfaceProperty.PRESSURE to 0, SurfaceProperty.GRAVITY to 0),
    );

    fun accepts(conditions: List<SurfaceCondition>) = conditions.all {
        it.matches(properties[it.property])
    }

    fun accepts(subgroup: Subgroup) = subgroupSurface(subgroup.name)?.let { it == this } ?: true

    /** Asteroid crushing only happens in space, its recipes carry no surface conditions. */
    fun accepts(category: Category) =
        when (category) {
            Category.CRUSHING -> this == SPACE
            else -> true
        }
}

/**
 * Several recipes are not gated by [SurfaceCondition]s but by a planet-labelled subgroup (their
 * recipe prototype only exists on that surface in-game).
 */
private val subgroupSurfaces =
    mapOf(
        "vulcanus-processes" to Surface.VULCANUS,
        "fulgora-processes" to Surface.FULGORA,
        "aquilo-processes" to Surface.AQUILO,
        "agriculture-processes" to Surface.GLEBA,
        "agriculture-products" to Surface.GLEBA,
        "nauvis-agriculture" to Surface.NAUVIS,
        "space-processing" to Surface.SPACE,
    )

fun subgroupSurface(subgroup: String) = subgroupSurfaces[subgroup]

private val surfacesByCode = Surface.entries.associateBy { it.code }

fun surfaceFromUrl(s: String) = s.firstOrNull()?.let { surfacesByCode[it] }

fun Surface.toUrlParam() = code.toString()

fun ParsedRecipe.forSurface(surface: Surface) =
    copy(
        recipes =
            recipes.filter {
                surface.accepts(it.surfaceConditions) &&
                    surface.accepts(it.subgroup) &&
                    surface.accepts(it.category)
            }
    )

enum class Category(val identifier: String) {
    ADVANCED_CRAFTING("advanced-crafting"),
    CENTRIFUGING("centrifuging"),
    CRAFTING("crafting"),
    CRAFTING_WITH_FLUID("crafting-with-fluid"),
    ROCKET_BUILDING("rocket-building"),
    OIL_PROCESSING("oil-processing"),
    CHEMISTRY("chemistry"),
    SMELTING("smelting"),
    ORGANIC("organic"),
    METALLURGY("metallurgy"),
    CRYOGENICS("cryogenics"),
    ELECTROMAGNETICS("electromagnetics"),
    CRUSHING("crushing"),
    RECYCLING("recycling"),
    CAPTIVE_SPAWNER_PROCESS("captive-spawner-process"),
    HAND_CRAFTING("hand-crafting"),
    NO_CATEGORY("no-category"),
    /** custom category for dummy recipe to consume all science-packs */
    SCIENCE_LAB("science-lab"),
}

private val categoryMap = Category.entries.associateBy { it.identifier }

fun category(identifier: String) = categoryMap[identifier]
