package de.bjrke.factorio.optimizer.common.model

import kotlin.math.max

data class FactoryProduction(
    // the demanded material this recipe entry is labeled with; a recipe producing several needed
    // materials (e.g. scrap recycling) is labeled with the one it mostly supplies
    val material: Material,
    val recipe: Recipe,
    val factory: Factory,
    val amount: Double = 0.0,
    val level: Int,
    // the stats the optimizer actually sized the machine with, derived from the prototype dump
    // when available; the curated [Factory] values are only the fallback
    val speed: Double = factory.speed,
    val productivity: Double = factory.productivity,
) {
    fun production(material: Material) =
        speed * (1.0 + productivity) * amount * recipe.result[material]!! / recipe.time

    fun consumption(material: Material) =
        speed * amount * recipe.ingredients[material]!! / recipe.time
}

data class OptimizationResult(
    val target: Material,
    val recipe: Recipe,
    // keyed by recipe: several recipes can produce the same material and run in parallel
    val factories: Map<Recipe, FactoryProduction>,
    // the logistics prototypes the calculator uses to turn rates into belts/inserters/pumps
    val logistics: Logistics = Logistics(),
)

val factoryProductionComparator =
    compareBy<FactoryProduction>(
        { max(it.recipe.ingredients.size, it.recipe.result.size) },
        { it.level },
        { it.factory },
        { it.recipe.name },
    )
