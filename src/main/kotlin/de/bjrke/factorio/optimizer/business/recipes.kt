package de.bjrke.factorio.optimizer.business

import de.bjrke.factorio.optimizer.model.*

@JsModule("recipe.json") @JsNonModule private external val defaultRecipesJson: dynamic

private val jsObject = js("Object")

private fun string(name: dynamic) = name as String

private fun double(number: dynamic) = number as Double

fun parseRecipes(json: dynamic = defaultRecipesJson): ParsedRecipe {

    val materialsBuilder = mutableMapOf<String, Material>()
    val recipesBuilder = mutableListOf<Recipe>()
    val subgroups = mutableMapOf<String, Subgroup>()

    fun material(name: String) = materialsBuilder.getOrPut(name) { Material.of(name) }
    fun jsMaterial(name: dynamic): Material = material(string(name))
    fun subgroup(name: String): Subgroup = subgroups.getOrPut(name) { Subgroup(name) }
    fun jsSubgroup(name: dynamic): Subgroup = subgroup(string(name))

    val filteredOut = setOf(subgroup("fill-barrel"), subgroup("empty-barrel"))
    val noSubGroup = Subgroup()

    for (jsRecipe in jsObject.values(json)) {
        val subgroupName = jsRecipe.subgroup?.name
        val subgroup = if (subgroupName != null) jsSubgroup(subgroupName) else noSubGroup
        if (filteredOut.contains(subgroup)) continue

        val result = mutableMapOf<Material, Double>()
        for (jsProduct in jsRecipe.products) {
            result[jsMaterial(jsProduct.name)] =
                double(jsProduct.amount) * double(jsProduct.probability)
        }

        val ingredients = mutableMapOf<Material, Double>()
        for (jsIngredient in jsRecipe.ingredients) {
            ingredients[jsMaterial(jsIngredient.name)] = double(jsIngredient.amount)
        }

        recipesBuilder +=
            Recipe(
                name = string(jsRecipe.name),
                ingredients = ingredients.toMap(),
                result = result.toMap(),
                time = double(jsRecipe.energy),
                subgroup = subgroup,
                category = category(string(jsRecipe.category))!!
            )
    }

    recipesBuilder +=
        Recipe(
            name = "space-science-pack",
            ingredients = mapOf(material("satellite") to 1.0, material("rocket-part") to 100.0),
            result = mapOf(material("space-science-pack") to 1000.0),
            time = 1.0,
            category = Category.ROCKET_BUILDING
        )
    recipesBuilder +=
        Recipe(
            name = "all-science-packs",
            ingredients =
                mapOf(
                    material("automation-science-pack") to 1.0,
                    material("chemical-science-pack") to 1.0,
                    material("logistic-science-pack") to 1.0,
                    material("military-science-pack") to 1.0,
                    material("production-science-pack") to 1.0,
                    material("space-science-pack") to 1.0,
                    material("utility-science-pack") to 1.0
                ),
            result = mapOf(material("all-science-packs") to 1.0),
            time = 1.0,
            category = Category.SCIENCE_LAB
        )

    return ParsedRecipe(
        materials = materialsBuilder.values.sortedBy { it.name }.toList(),
        recipes = recipesBuilder.toList()
    )
}
