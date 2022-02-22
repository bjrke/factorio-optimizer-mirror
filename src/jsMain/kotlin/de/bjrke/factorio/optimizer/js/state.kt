package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.business.optimize
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.js.business.parseRecipes

val defaultParsedRecipes = parseRecipes()
private val defaultMaterial = defaultParsedRecipes.materials.first()
private val defaultOptimizationResult =
    optimize(defaultMaterial, defaultParsedRecipes, defaultFactories)

data class OptimizerAppState(
    val target: Material = defaultMaterial,
    val normalizeTarget: Double = 1.0,
    val parsedRecipes: ParsedRecipe = defaultParsedRecipes,
    val optimizationResult: OptimizationResult = defaultOptimizationResult,
    val factories: Set<Factory> = defaultFactories,
)
