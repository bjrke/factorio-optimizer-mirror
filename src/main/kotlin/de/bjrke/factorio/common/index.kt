package de.bjrke.factorio.common

import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.common.model.Recipe

@JsModule("recipe.json")
@JsNonModule
private external val json: dynamic

private val materialsBuilder = mutableMapOf<String, Material>()

private fun string(name: dynamic) = name as String

private fun material(name: dynamic): Material {
    val s = string(name)
    return materialsBuilder.getOrPut(s) { Material(s) }
}

private fun double(number: dynamic) = number as Double

val recipes = run {
    val recipesBuilder = mutableListOf<Recipe>()

    for (jsRecipe in js("Object").values(json)) {
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

    recipesBuilder.toList()
}

val materials = materialsBuilder.values.sortedBy { it.name }.toList()
