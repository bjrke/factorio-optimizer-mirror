package de.bjrke.factorio.optimizer.common.model

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
    );

    fun accepts(conditions: List<SurfaceCondition>) = conditions.all {
        it.matches(properties[it.property])
    }

    fun accepts(subgroup: Subgroup) = subgroupSurface(subgroup.name)?.let { it == this } ?: true

    /**
     * Some recipe categories only run on one surface: asteroid collection and crushing happen in
     * space, and the captive biter spawner (biter eggs) can only be built on Nauvis, so its product
     * is imported everywhere else.
     */
    fun accepts(category: Category) =
        when (category) {
            Category.CRUSHING -> this == SPACE
            Category.ASTEROID_COLLECTION -> this == SPACE
            Category.CAPTIVE_SPAWNER_PROCESS -> this == NAUVIS
            else -> true
        }

    /** Extraction recipes (mining, pumping) only exist where their resource spawns. */
    fun acceptsResource(name: String) = resourceSurfaces[name]?.contains(this) ?: true
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

private val surfacesByCode = Surface.entries.filter { it.code != null }.associateBy { it.code }

/** Scans the `selection` URL parameter for a surface's single code; the default is Nauvis. */
fun surfaceFromUrl(s: String?) = s?.firstNotNullOfOrNull { surfacesByCode[it] }

/** Resolves the CLI's `--planet=` value: an exact [Surface.identifier] or a single code. */
fun surfaceFromPlanet(s: String?) =
    Surface.entries.firstOrNull { it.identifier == s } ?: surfaceFromUrl(s)

/** Drops every recipe the [surface] cannot run and every spoiling recipe whose input it lacks. */
fun ParsedRecipe.forSurface(surface: Surface): ParsedRecipe {
    val kept = recipes.filter {
        surface.accepts(it.surfaceConditions) &&
            // the planet-labeled subgroup is only a fallback for recipes without explicit
            // surface conditions: some of those fit several surfaces, e.g. the
            // quantum-processor (low pressure) is also assembled on space platforms
            (it.surfaceConditions.isNotEmpty() || surface.accepts(it.subgroup)) &&
            it.categories.any(surface::accepts) &&
            (it.categories.none(extractionCategories::contains) ||
                surface.acceptsResource(it.name)) &&
            // the auto-generated recycling recipes only exist where scrap is collected
            (Category.RECYCLING !in it.categories || surface == Surface.FULGORA)
    }

    // A spoiling recipe only makes sense where the spoiling item is actually produced. This keeps
    // bacteria (a free chest import on Fulgora) from being "imported only to be spoiled" into ore.
    val produced = buildSet {
        kept.forEach {
            if (Category.SPOILING !in it.categories) addAll(it.result.keys)
        }
    }

    return copy(
        recipes =
            kept.filter { recipe ->
                Category.SPOILING !in recipe.categories ||
                    produced.containsAll(recipe.ingredients.keys)
            },
    )
}
