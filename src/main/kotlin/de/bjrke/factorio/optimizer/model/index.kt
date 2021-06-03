package de.bjrke.factorio.optimizer.model

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
    val subgroup: Subgroup,
    val category: Category,
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

    NO_CATEGORY("no-category")

    ;

}

private val categoryMap = Category.values().associateBy { it.identifier }
fun category(identifier: String) = categoryMap[identifier]
