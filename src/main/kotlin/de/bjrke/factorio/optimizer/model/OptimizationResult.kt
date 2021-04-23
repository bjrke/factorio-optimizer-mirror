package de.bjrke.factorio.optimizer.model

import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.common.model.Recipe

data class FactoryProduction(
    val recipe: Recipe,
    val amount: Double = 0.0
) {
    operator fun plus(amount: Double) = FactoryProduction(recipe, this.amount + amount)

    fun production(material: Material) = amount * recipe.result[material]!! / recipe.time
}

data class OptimizationResult(
    val target: Material,
    val recipe: Recipe,
    //TODO key should be a recipe
    val factories: Map<Material, FactoryProduction>
)
