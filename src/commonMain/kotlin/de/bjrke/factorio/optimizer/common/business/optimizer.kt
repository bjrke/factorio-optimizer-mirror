package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup

fun optimize(
    target: Material,
    parsedRecipe: ParsedRecipe = parseRecipes(),
    factories: Set<Factory> = defaultFactories,
    cache: MutableMap<Material, OptimizationResult> = mutableMapOf(),
    visited: MutableSet<Material> = mutableSetOf(),
): OptimizationResult =
    cache.getOrPut(target) {
        val recipe =
            parsedRecipe.recipes
                .filter { findFactory(factories, it) != null }
                .firstOrNull { it.result.containsKey(target) }
                ?: Recipe(
                    name = target.name,
                    ingredients = mapOf(),
                    result = mapOf(target to 1.0),
                    time = 1.0,
                    subgroup = Subgroup(),
                    category = Category.NO_CATEGORY,
                )

        val factory = findFactory(factories, recipe) ?: Factory.NO_FACTORY

        val factoryProductions = mutableMapOf<Material, FactoryProduction>()
        fun addFactory(
            material: Material,
            recipe: Recipe,
            factory: Factory,
            amount: Double,
            level: Int,
        ) {
            factoryProductions[material] =
                (factoryProductions[material]
                    ?: FactoryProduction(recipe = recipe, factory = factory, level = level)) +
                    amount
        }

        val add = visited.add(target)

        val outputAmount = recipe.result[target]!!
        val outputTime = recipe.time / factory.speed

        if (add) { // cycle protection
            recipe.ingredients.forEach { (material, amount) ->
                val requirement = amount / outputAmount
                val nested = optimize(material, parsedRecipe, factories, cache, visited)

                nested.factories.forEach { (nestedMaterial, nestedFactories) ->
                    addFactory(
                        nestedMaterial,
                        nestedFactories.recipe,
                        nestedFactories.factory,
                        requirement * nestedFactories.amount,
                        nestedFactories.level,
                    )
                }
            }
            visited.remove(target)
        }

        addFactory(
            target,
            recipe,
            factory,
            outputTime / outputAmount,
            1 + (factoryProductions.values.maxOfOrNull(FactoryProduction::level) ?: 0),
        )

        return OptimizationResult(
            target = target,
            factories = factoryProductions.toMap(),
            recipe = recipe,
        )
    }

private fun findFactory(factories: Set<Factory>, recipe: Recipe) = factories.find {
    it.categories.contains(recipe.category)
}
