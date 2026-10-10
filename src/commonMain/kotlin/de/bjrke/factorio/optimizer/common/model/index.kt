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
)

data class Subgroup(val name: String = "no subgroup")

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
