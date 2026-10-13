package de.bjrke.factorio.optimizer.common.model

data class Material(val name: String) {
    companion object {
        private val materialsBuilder = mutableMapOf<String, Material>()

        fun of(name: String) = materialsBuilder.getOrPut(name) { Material(name) }
    }
}

data class ParsedRecipe(
    val materials: List<Material>,
    val recipes: List<Recipe>,
    /**
     * Crafting categories, speed and built-in productivity of the machines, derived from the
     * `data.raw` dump and keyed by the machine prototype name. [Factory] carries curated fallbacks
     * for synthetic tests that build a [ParsedRecipe] without a dump.
     */
    val machineStats: Map<String, MachineStats> = emptyMap(),
)

/** The crafting-relevant stats of a machine prototype. */
data class MachineStats(
    val categories: Set<Category>,
    val speed: Double,
    val productivity: Double,
)

data class Recipe(
    val name: String,
    val ingredients: Map<Material, Double>,
    val result: Map<Material, Double>,
    val time: Double,
    val categories: Set<Category>,
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
        "yumako" to setOf(Surface.GLEBA),
        "jellynut" to setOf(Surface.GLEBA),
    )

private val surfacesByCode = Surface.entries.associateBy { it.code }

fun surfaceFromUrl(s: String) = s.firstOrNull()?.let { surfacesByCode[it] }

fun Surface.toUrlParam() = code.toString()

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
            }
    )
}

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
    ASTEROID_COLLECTION("asteroid-collection"),
    RECYCLING("recycling"),
    CAPTIVE_SPAWNER_PROCESS("captive-spawner-process"),
    HAND_CRAFTING("hand-crafting"),
    NO_CATEGORY("no-category"),
    // custom category for dummy recipe to consume all science-packs
    SCIENCE_LAB("science-lab"),
    // custom categories for extraction recipes (resources, offshore pump fluids)
    MINING_BASIC_SOLID("mining-basic-solid"),
    MINING_HARD_SOLID("mining-hard-solid"),
    MINING_FLUID("mining-fluid"),
    OFFSHORE_PUMPING("offshore-pumping"),
    HARVESTING("harvesting"),
    // custom category for spoilable items spoiling into another item
    SPOILING("spoiling"),
}

/**
 * Recipes representing a raw resource extracted from the map. They have no ingredients and are
 * preferred over processing recipes when the responsible machine is selected.
 */
val extractionCategories =
    setOf(
        Category.MINING_BASIC_SOLID,
        Category.MINING_HARD_SOLID,
        Category.MINING_FLUID,
        Category.OFFSHORE_PUMPING,
        Category.HARVESTING,
        Category.ASTEROID_COLLECTION,
    )

private val categoryMap = Category.entries.associateBy { it.identifier }

fun category(identifier: String) = categoryMap[identifier]
