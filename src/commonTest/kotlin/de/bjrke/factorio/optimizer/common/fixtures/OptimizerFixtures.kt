package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.optimize.Optimizer

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
