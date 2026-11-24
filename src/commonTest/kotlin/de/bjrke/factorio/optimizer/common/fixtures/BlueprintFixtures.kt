package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Logistics
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.OptimizationResult

/**
 * Every planned production in plan order; a production may appear once per row. A test-only view:
 * production code reads `BlueprintPlan.rows` instead, so the plan owns no derived recipe list.
 */
internal val BlueprintPlan.recipes: List<FactoryProduction>
    get() = rows.flatMap { it.productions }

fun FactoryProduction.entry() = this.recipe to this

@Suppress("LongParameterList")
fun production(
    recipe: Recipe,
    factory: Factory = Factory.ASSEMBLING_MACHINE_3,
    material: Material = recipe.result.keys.single(),
    amount: Double = 1.0,
    level: Int = 0,
    speed: Double = factory.speed,
    productivity: Double = factory.productivity,
) =
    FactoryProduction(
        material,
        recipe = recipe.indexedForTest(),
        factory,
        amount,
        level,
        speed,
        productivity,
    )

fun factories(vararg productions: FactoryProduction) = productions.associateBy {
    it.recipe
}

internal fun optimizationResult(recipe: Recipe, factory: Factory) =
    optimizationResult(production(recipe, factory))

internal fun optimizationResult(
    vararg productions: FactoryProduction,
    logistics: Logistics = Logistics(),
) =
    OptimizationResult(
        target = productions.first().material,
        recipe = productions.first().recipe,
        factories = productions.associateBy { it.recipe },
        logistics = logistics,
    )
