package de.bjrke.factorio.optimizer.common.prototype

/** A physical property a [SurfaceCondition] can compare. */
enum class SurfaceProperty(val identifier: String) {
    PRESSURE("pressure"),
    GRAVITY("gravity"),
    MAGNETIC_FIELD("magnetic-field"),
}

private val surfacePropertyMap = SurfaceProperty.entries.associateBy { it.identifier }

/** Resolves a prototype surface-property identifier, or `null` when it is unknown. */
fun surfaceProperty(identifier: String) = surfacePropertyMap[identifier]

/** The range a recipe needs a [SurfaceProperty] to fall into. */
data class SurfaceCondition(
    val property: SurfaceProperty,
    val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) {
    fun matches(value: Int?) = value != null && value in range
}

/**
 * Which surface(s) an extraction recipe (= its resource) can be built on. Keyed by recipe name so
 * resources like `sulfuric-acid-geyser` or `fluorine-vent` don't collide with the chemical recipes
 * producing the same fluid. Unknown names are available on every surface.
 */
private val resourceSurfaces =
    mapOf(
        "iron-ore" to setOf(Surface.NAUVIS),
        "copper-ore" to setOf(Surface.NAUVIS),
        "coal" to setOf(Surface.NAUVIS, Surface.VULCANUS),
        "stone" to setOf(Surface.NAUVIS, Surface.GLEBA),
        "uranium-ore" to setOf(Surface.NAUVIS),
        "crude-oil" to setOf(Surface.NAUVIS, Surface.AQUILO),
        "calcite" to setOf(Surface.VULCANUS),
        "tungsten-ore" to setOf(Surface.VULCANUS),
        "sulfuric-acid-geyser" to setOf(Surface.VULCANUS),
        "scrap" to setOf(Surface.FULGORA),
        "lithium-brine" to setOf(Surface.AQUILO),
        "fluorine-vent" to setOf(Surface.AQUILO),
        "water" to setOf(Surface.NAUVIS, Surface.GLEBA),
        "heavy-oil" to setOf(Surface.FULGORA),
        "lava" to setOf(Surface.VULCANUS),
        "ammoniacal-solution" to setOf(Surface.AQUILO),
        "wood" to setOf(Surface.NAUVIS),
        "yumako" to setOf(Surface.GLEBA),
        "jellynut" to setOf(Surface.GLEBA),
    )

/**
 * Whether [this] surface builds a recipe of a planet-labelled [Subgroup]: some recipes are not
 * gated by [SurfaceCondition]s but by the surface their prototype exists on in-game.
 */
fun Surface.accepts(subgroup: Subgroup) = subgroupSurface(subgroup.name)?.let { it == this } ?: true

/**
 * Some recipe categories only run on one surface: asteroid collection and crushing happen in space,
 * and the captive biter spawner (biter eggs) can only be built on Nauvis, so its product is
 * imported everywhere else.
 */
fun Surface.accepts(category: Category) =
    when (category) {
        Category.CRUSHING -> this == Surface.SPACE
        Category.ASTEROID_COLLECTION -> this == Surface.SPACE
        Category.CAPTIVE_SPAWNER_PROCESS -> this == Surface.NAUVIS
        else -> true
    }

/** Extraction recipes (mining, pumping) only exist where their resource spawns. */
fun Surface.acceptsResource(name: String) = resourceSurfaces[name]?.contains(this) ?: true

fun Surface.accepts(conditions: List<SurfaceCondition>) = conditions.all {
    it.matches(properties[it.property])
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

/** The surface a planet-labelled subgroup belongs to, or `null` when it is not surface-gated. */
fun subgroupSurface(subgroup: String) = subgroupSurfaces[subgroup]

/**
 * A surface (planet or space platform) a factory can be built on. Recipe availability is expressed
 * as [SurfaceCondition]s against the physical properties listed in [properties].
 */
enum class Surface(
    val code: Char?,
    val identifier: String,
    val properties: Map<SurfaceProperty, Int>,
) {
    // Nauvis is the default surface, so it carries no code
    NAUVIS(null, "nauvis", mapOf(SurfaceProperty.PRESSURE to 1000, SurfaceProperty.GRAVITY to 10)),
    VULCANUS(
        'v',
        "vulcanus",
        mapOf(SurfaceProperty.PRESSURE to 4000, SurfaceProperty.GRAVITY to 40),
    ),
    GLEBA('g', "gleba", mapOf(SurfaceProperty.PRESSURE to 2000, SurfaceProperty.GRAVITY to 20)),
    FULGORA(
        'o',
        "fulgora",
        mapOf(
            SurfaceProperty.PRESSURE to 1000,
            SurfaceProperty.GRAVITY to 15,
            SurfaceProperty.MAGNETIC_FIELD to 99,
        ),
    ),
    AQUILO('a', "aquilo", mapOf(SurfaceProperty.PRESSURE to 300, SurfaceProperty.GRAVITY to 10)),
    SPACE(
        'p',
        "space-platform-surface",
        mapOf(SurfaceProperty.PRESSURE to 0, SurfaceProperty.GRAVITY to 0),
    ),
}
