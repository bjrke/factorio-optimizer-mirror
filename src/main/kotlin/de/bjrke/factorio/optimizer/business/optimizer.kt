package de.bjrke.factorio.optimizer.business

import de.bjrke.factorio.optimizer.model.*

fun optimize(
    target: Material,
    parsedRecipe: ParsedRecipe,
    factories: Set<Factory>,
    cache: MutableMap<Material, OptimizationResult> = mutableMapOf(),
    visited: MutableSet<Material> = mutableSetOf(),
): OptimizationResult =
    cache.getOrPut(target) {
        val recipe = parsedRecipe.recipes
            .filter { findFactory(factories, it) != null }
            .firstOrNull { it.result.containsKey(target) } ?: Recipe(
            name = target.name,
            ingredients = mapOf(),
            result = mapOf(target to 1.0),
            time = 1.0,
            subgroup = Subgroup(),
            category = Category.NO_CATEGORY
        )

        val factory = findFactory(factories, recipe) ?: Factory.NO_FACTORY

        val factoryProductions = mutableMapOf<Material, FactoryProduction>()
        fun addFactory(material: Material, recipe: Recipe, factory: Factory, amount: Double) {
            factoryProductions[material] = (factoryProductions[material] ?: FactoryProduction(recipe, factory)) + amount
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
                        requirement * nestedFactories.amount
                    )
                }

            }
            visited.remove(target)
        }

        addFactory(target, recipe, factory, outputTime / outputAmount)

        return OptimizationResult(
            target = target,
            factories = factoryProductions.toMap(),
            recipe = recipe,
        )
    }

private fun findFactory(factories: Set<Factory>, recipe: Recipe) =
    factories.find { it.categories.contains(recipe.category) }

