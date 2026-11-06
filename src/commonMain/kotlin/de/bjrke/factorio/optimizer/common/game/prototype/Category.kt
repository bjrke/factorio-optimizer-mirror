package de.bjrke.factorio.optimizer.common.game.prototype

/** The crafting category of a recipe (the machine category it needs). */
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
    // used to mark imports
    IMPORT("import"),
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
 * Recipes that enter a blueprint from outside instead of being built as a row: raw resources
 * extracted from the map and the synthetic [Category.IMPORT] pseudo recipes that stand in for
 * materials produced elsewhere. The blueprint's `buildable` filter treats both the same way.
 * Extraction recipes have no ingredients and are preferred over processing recipes when the
 * responsible machine is selected.
 */
val extractionCategories =
    setOf(
        // imports are no more built than a resource: both enter from outside the blueprint
        Category.IMPORT,
        Category.MINING_BASIC_SOLID,
        Category.MINING_HARD_SOLID,
        Category.MINING_FLUID,
        Category.OFFSHORE_PUMPING,
        Category.HARVESTING,
        Category.ASTEROID_COLLECTION,
    )

private val categoryMap = Category.entries.associateBy { it.identifier }

/** Resolves a prototype category identifier, or `null` when it is unknown. */
fun category(identifier: String) = categoryMap[identifier]
