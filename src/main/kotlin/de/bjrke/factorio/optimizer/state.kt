package de.bjrke.factorio.optimizer

import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.common.model.ParsedRecipe
import de.bjrke.factorio.common.model.Recipe
import de.bjrke.factorio.common.parseRecipes
import de.bjrke.factorio.optimizer.model.OptimizationResult

private val defaultMaterial = Material("foo")

private val defaultRecipe = Recipe(
    name = defaultMaterial.name,
    ingredients = mapOf(),
    result = mapOf(defaultMaterial to 1.0),
    time = 1.0
)

private val defaultOptimizationResult = OptimizationResult(
    target = defaultMaterial,
    recipe = defaultRecipe,
    factories = mapOf(defaultMaterial to 1.0),
    production = mapOf(defaultMaterial to 1.0)
)

data class OptimizerAppState(
    val target: Material = defaultMaterial,
    val normalizeTarget: Double = 1.0,
    val parsedRecipe: ParsedRecipe = parseRecipes(),
    val optimizationResult: OptimizationResult = defaultOptimizationResult
)
