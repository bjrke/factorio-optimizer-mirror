package de.bjrke.factorio.optimizer.common.service.fixtures

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import de.bjrke.factorio.optimizer.common.service.OptimizationResult
import de.bjrke.factorio.optimizer.common.service.Optimizer

/** A single-category recipe that makes [result] from [ingredients] in [time] seconds. */
internal fun testRecipe(
    name: String,
    category: Category,
    result: Map<Material, Double>,
    ingredients: Map<Material, Double> = emptyMap(),
    time: Double = 1.0,
) =
    Recipe(
        name = name,
        ingredients = ingredients,
        result = result,
        time = time,
        categories = setOf(category),
    )

/** The factory the optimizer picks for [target] from [recipes], restricted to [factories]. */
internal fun factoryOf(target: Material, recipes: List<Recipe>, factories: Set<Factory>) =
    Optimizer(ParsedRecipe(emptyList(), recipes), factories)[target].production(target).factory

// The OptimizationResult read-back the optimizer suites assert on. Production only ever reads a
// single FactoryProduction (FactoryProduction.production), so these live in the test source set.

/** Every recipe entry the optimizer labeled with [material]. */
internal fun OptimizationResult.producers(material: Material): List<FactoryProduction> =
    factories.values.filter { it.material == material }

/** The recipe entry supplying the most of [material]. */
internal fun OptimizationResult.production(material: Material): FactoryProduction =
    producers(material).maxByOrNull { it.amount } ?: error("no producer for ${material.name}")
