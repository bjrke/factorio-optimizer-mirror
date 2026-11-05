package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/**
 * A minimal fluid-to-solid chemistry recipe: one fluid input, one solid output. It is a test
 * double, not a game recipe; a real one adds inputs and rates that move the exact lane tiles the
 * characterization suites pin.
 */
fun waterRecipe(also: Map<Material, Double> = emptyMap()) =
    recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0) + also)

/**
 * A plan of [factory] boiling water into a gear; [fluidInputs] join the water as further inputs.
 */
fun fluidRowPlan(
    factory: Factory,
    machines: Double = 1.0,
    fluidInputs: Map<Material, Double> = emptyMap(),
): BlueprintPlan {
    val waterBoilRecipe = waterRecipe(fluidInputs)
    val planned = FactoryProduction(gear, waterBoilRecipe, factory, amount = 1.0, level = 0)
    return BlueprintPlan(
        target = gear,
        rows = listOf(PlannedRow(List(machines.toInt().coerceAtLeast(1)) { planned })),
        sources = (listOf(water) + fluidInputs.keys).map { PlannedSource(it, 1.0, fluid = true) },
        scale = 1.0,
        label = "water-boil",
    )
}
