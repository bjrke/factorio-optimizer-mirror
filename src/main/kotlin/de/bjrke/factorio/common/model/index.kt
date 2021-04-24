package de.bjrke.factorio.common.model

data class Material(val name: String)
data class ParsedRecipe(val materials: List<Material>, val recipes: List<Recipe>)
data class Recipe(
    val name: String,
    val ingredients: Map<Material, Double>,
    val result: Map<Material, Double>,
    val time: Double,
    val subgroup: Subgroup,
)
data class Subgroup(val name: String = "no subgroup")
