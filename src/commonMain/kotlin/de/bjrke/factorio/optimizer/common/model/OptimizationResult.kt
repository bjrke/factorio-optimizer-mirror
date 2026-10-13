package de.bjrke.factorio.optimizer.common.model

import kotlin.math.max

data class FactoryProduction(
    val recipe: Recipe,
    val factory: Factory,
    val amount: Double = 0.0,
    val level: Int,
    // the stats the optimizer actually sized the machine with, derived from the prototype dump
    // when available; the curated [Factory] values are only the fallback
    val speed: Double = factory.speed,
    val productivity: Double = factory.productivity,
) {
    operator fun plus(amount: Double) = copy(amount = this.amount + amount)

    fun production(material: Material) =
        speed * (1.0 + productivity) * amount * recipe.result[material]!! / recipe.time

    fun consumption(material: Material) =
        speed * amount * recipe.ingredients[material]!! / recipe.time
}

data class OptimizationResult(
    val target: Material,
    val recipe: Recipe,
    // TODO key should be a recipe
    val factories: Map<Material, FactoryProduction>,
)

val factoryProductionComparator =
    compareBy<Map.Entry<Material, FactoryProduction>>(
        { max(it.value.recipe.ingredients.size, it.value.recipe.result.size) },
        { it.value.level },
        { it.value.factory },
        { it.value.recipe.name },
    )
