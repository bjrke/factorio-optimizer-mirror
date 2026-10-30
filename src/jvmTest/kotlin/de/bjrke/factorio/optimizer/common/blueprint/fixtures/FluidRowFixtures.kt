package de.bjrke.factorio.optimizer.common.blueprint.fixtures

import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.service.FactoryProduction

/**
 * A water-only chemistry recipe: the whole row is fluid, so nothing but a pipe can reach a port.
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
    val boil = waterRecipe(fluidInputs)
    val planned = FactoryProduction(gear, boil, factory, amount = 1.0, level = 0)
    return BlueprintPlan(
        target = gear,
        rows = listOf(PlannedRow(List(machines.toInt().coerceAtLeast(1)) { planned })),
        sources = (listOf(water) + fluidInputs.keys).map { PlannedSource(it, 1.0, fluid = true) },
        scale = 1.0,
        label = "water-boil",
    )
}
