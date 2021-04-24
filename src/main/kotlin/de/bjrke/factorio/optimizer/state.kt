package de.bjrke.factorio.optimizer

import de.bjrke.factorio.optimizer.business.optimize
import de.bjrke.factorio.optimizer.business.parseRecipes
import de.bjrke.factorio.optimizer.model.Material
import de.bjrke.factorio.optimizer.model.ParsedRecipe
import de.bjrke.factorio.optimizer.model.OptimizationResult

private val defaultParsedRecipes = parseRecipes()
private val defaultMaterial = defaultParsedRecipes.materials.first()
private val defaultOptimizationResult = optimize(defaultMaterial, defaultParsedRecipes)

data class OptimizerAppState(
    val target: Material = defaultMaterial,
    val normalizeTarget: Double = 1.0,
    val parsedRecipes: ParsedRecipe = defaultParsedRecipes,
    val optimizationResult: OptimizationResult = defaultOptimizationResult
)
