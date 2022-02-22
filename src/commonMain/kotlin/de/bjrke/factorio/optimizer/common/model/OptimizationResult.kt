package de.bjrke.factorio.optimizer.common.model

data class FactoryProduction(
    val recipe: Recipe,
    val factory: Factory,
    val amount: Double = 0.0,
    val level: Int
) {
    operator fun plus(amount: Double) =
        FactoryProduction(recipe, factory, this.amount + amount, this.level)

    fun production(material: Material) =
        factory.speed * amount * recipe.result[material]!! / recipe.time

    fun consumption(material: Material) =
        factory.speed * amount * recipe.ingredients[material]!! / recipe.time
}

data class OptimizationResult(
    val target: Material,
    val recipe: Recipe,
    // TODO key should be a recipe
    val factories: Map<Material, FactoryProduction>
)
