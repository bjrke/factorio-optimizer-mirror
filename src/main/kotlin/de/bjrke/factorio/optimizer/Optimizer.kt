package de.bjrke.factorio.optimizer

import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.common.model.ParsedRecipe
import de.bjrke.factorio.common.model.Recipe
import de.bjrke.factorio.optimizer.model.FactoryProduction
import de.bjrke.factorio.optimizer.model.OptimizationResult

fun optimize(
    target: Material,
    parsedRecipe: ParsedRecipe,
    cache: MutableMap<Material, OptimizationResult> = mutableMapOf(),
    visited: MutableSet<Material> = mutableSetOf()
): OptimizationResult =
    cache.getOrPut(target) {
        val recipe = parsedRecipe.recipes.firstOrNull { it.result.containsKey(target) } ?: Recipe(
            name = target.name,
            ingredients = mapOf(),
            result = mapOf(target to 1.0),
            time = 1.0
        )
        val factories = mutableMapOf<Material, FactoryProduction>()
        val production = mutableMapOf<Material, Double>()

        fun addFactory(material: Material, recipe: Recipe, amount: Double) {
            factories[material] = (factories[material] ?: FactoryProduction(recipe)) + amount
        }

        fun addProduction(material: Material, amount: Double) {
            production[material] = (production[material] ?: 0.0) + amount
        }

        val add = visited.add(target)

        val outputAmount = recipe.result[target]!!
        val outputTime = recipe.time

        if (add) { // cycle protection
            recipe.ingredients.forEach { (material, amount) ->

                val requirement = amount / outputAmount
                val nested = optimize(material, parsedRecipe, cache, visited)

                nested.factories.forEach { (nestedMaterial, nestedFactories) ->
                    addFactory(nestedMaterial, nestedFactories.recipe, requirement * nestedFactories.amount)
                }

            }
            visited.remove(target)
        }

        addFactory(target, recipe, outputTime / outputAmount)
        addProduction(target, 1.0)

        val re = OptimizationResult(
            target = target,
            factories = factories.toMap(),
            recipe = recipe,
        )

        console.log("result", re)
        return re
    }

