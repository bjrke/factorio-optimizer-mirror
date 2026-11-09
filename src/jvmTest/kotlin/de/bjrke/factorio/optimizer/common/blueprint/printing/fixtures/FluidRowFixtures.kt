package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/**
 * A plan of [factory] running [recipe] (a real fluid-to-solid chemistry recipe). Its fluid
 * ingredients become the row's sources.
 */
fun fluidRowPlan(
    factory: Factory,
    recipe: Recipe = solidFuelFromPetroleumGasRecipe,
    machines: Double = 1.0,
): BlueprintPlan {
    val product = recipe.result.keys.single()
    val planned = FactoryProduction(product, recipe, factory, amount = 1.0, level = 0)
    return BlueprintPlan(
        target = product,
        rows = listOf(PlannedRow(List(machines.toInt().coerceAtLeast(1)) { planned })),
        sources = recipe.ingredients.keys.map { PlannedSource(it, 1.0, fluid = true) },
        label = recipe.name,
    )
}
