package de.bjrke.factorio.optimizer.common.model

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

/** Resolves a prototype category identifier, or `null` when it is unknown. */
fun category(identifier: String) = categoryMap[identifier]
