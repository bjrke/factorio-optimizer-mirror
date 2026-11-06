package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/**
 * The factory a plan starts from: the one producing the target [recipe]. Null means none does. The
 * target is always buildable as such, so this is not narrowed by [buildable].
 */
internal fun planTarget(
    factories: Collection<FactoryProduction>,
    recipe: Recipe,
): FactoryProduction? = factories.firstOrNull { it.recipe.name == recipe.name }

/**
 * The external inputs the [included] recipes consume but none of them produces, with the rate the
 * imports have to supply at [scale]: one lane per material, by name. Whether a material moves as a
 * fluid is asked from the [logistics] the plan was optimized with.
 */
internal fun plannedSources(
    included: Map<Recipe, FactoryProduction>,
    scale: Double,
    logistics: LogisticsCalculator,
): List<PlannedSource> {
    val producers = materialsToFactories(included) { recipe.result.keys }
    val consumers = materialsToFactories(included) { recipe.ingredients.keys }
    return consumers
        .filterKeys { it !in producers }
        .map { (material, factories) ->
            PlannedSource(
                material = material,
                rate = factories.sumOf { it.consumption(material) } * scale,
                fluid = logistics.isFluid(material),
            )
        }
        .sortedBy { it.material.name }
}

/**
 * Every material the [included] recipes make (or take, as [materials] selects), with the recipes
 * that make (take) it. In recipe order, and first-seen material order within a recipe.
 */
private fun materialsToFactories(
    included: Map<Recipe, FactoryProduction>,
    materials: FactoryProduction.() -> Set<Material>,
): Map<Material, List<FactoryProduction>> =
    buildMap<Material, MutableList<FactoryProduction>> {
        for (factory in included.values) {
            factory.materials().forEach { getOrPut(it) { mutableListOf() }.add(factory) }
        }
    }
