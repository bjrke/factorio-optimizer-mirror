package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.fixtures.GameCtxTestMixin
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe

/**
 * A plan of [factory] running [recipe] (a real fluid-to-solid chemistry recipe). Its fluid
 * ingredients become the row's sources.
 */
fun GameCtxTestMixin.fluidRowPlan(
    factory: Factory,
    recipe: Recipe = solidFuelFromPetroleumGasRecipe,
    machines: Double = 1.0,
): BlueprintPlan {
    val planned = production(recipe, factory)
    return BlueprintPlan(
        target = planned.material,
        rows = listOf(PlannedRow(List(machines.toInt().coerceAtLeast(1)) { planned })),
        sources = recipe.ingredients.keys.map { PlannedSource(it, fluid = true) },
        label = recipe.name,
    )
}
