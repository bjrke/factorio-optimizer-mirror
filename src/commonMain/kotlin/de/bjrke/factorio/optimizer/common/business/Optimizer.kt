package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories

class Optimizer(
    private val parsedRecipe: ParsedRecipe = parseRecipes(),
    private val factories: Set<Factory> = defaultFactories,
) {
    private val visited = mutableSetOf<Material>()

    private val cache = mutableMapOf<Material, OptimizationResult>()

    private fun findFactory(recipe: Recipe) = factories.find {
        it.categories.contains(recipe.category)
    }

    private fun uncached(target: Material): OptimizationResult {
        val recipe =
            parsedRecipe.recipes
                .filter { findFactory(it) != null }
                .firstOrNull { it.result.containsKey(target) }
                ?: Recipe(
                    name = target.name,
                    ingredients = mapOf(),
                    result = mapOf(target to 1.0),
                    time = 1.0,
                    subgroup = Subgroup(),
                    category = Category.NO_CATEGORY,
                )

        val factory = findFactory(recipe) ?: Factory.NO_FACTORY

        val factoryProductions = mutableMapOf<Material, FactoryProduction>()

        fun addFactory(
            material: Material,
            recipe: Recipe,
            factory: Factory,
            amount: Double,
            level: Int,
        ) {
            val current =
                factoryProductions[material] ?: FactoryProduction(recipe, factory, 0.0, level)
            factoryProductions[material] = current + amount
        }

        val add = visited.add(target)

        val outputAmount = recipe.result[target]!!
        val outputTime = recipe.time / factory.speed

        if (add) { // cycle protection
            recipe.ingredients.forEach { (material, amount) ->
                val requirement = amount / outputAmount
                val nested = this[material]

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

    operator fun get(target: Material) =
        cache.getOrPut(target) {
            uncached(target)
        }
}
