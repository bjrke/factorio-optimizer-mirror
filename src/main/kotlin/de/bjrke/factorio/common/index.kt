package de.bjrke.factorio.common

import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.common.model.ParsedRecipe
import de.bjrke.factorio.common.model.Recipe

@JsModule("recipe.json")
@JsNonModule
private external val defaultRecipesJson: dynamic

private val jsObject = js("Object")

private fun string(name: dynamic) = name as String
private fun double(number: dynamic) = number as Double

fun parseRecipes(json: dynamic = defaultRecipesJson): ParsedRecipe {

    val materialsBuilder = mutableMapOf<String, Material>()
    val recipesBuilder = mutableListOf<Recipe>()

    fun material(name: dynamic): Material {
        val s = string(name)
        return materialsBuilder.getOrPut(s) { Material(s) }
    }

    for (jsRecipe in jsObject.values(json)) {
        val subgroup = jsRecipe.subgroup?.name
        if (subgroup == "fill-barrel" || subgroup == "empty-barrel") continue

        val result = mutableMapOf<Material, Double>()
        for (jsProduct in jsRecipe.products) {
            result[material(jsProduct.name)] = double(jsProduct.amount) * double(jsProduct.probability)
        }

        val ingredients = mutableMapOf<Material, Double>()
        for (jsIngredient in jsRecipe.ingredients) {
            ingredients[material(jsIngredient.name)] = double(jsIngredient.amount)
        }

        recipesBuilder += Recipe(
            name = string(jsRecipe.name),
            ingredients = ingredients.toMap(),
            result = result.toMap(),
            time = double(jsRecipe.energy)
        )
    }

    return ParsedRecipe(
        materials = materialsBuilder.values.sortedBy { it.name }.toList(),
        recipes = recipesBuilder.toList()
    )
}
