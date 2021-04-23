package de.bjrke.factorio.optimizer.model

import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.common.model.Recipe

data class OptimizationResult(
    val target: Material,
    val recipe: Recipe,
    //TODO key should be a recipe
    val factories: Map<Material, Double>,
    val production: Map<Material, Double>
)
