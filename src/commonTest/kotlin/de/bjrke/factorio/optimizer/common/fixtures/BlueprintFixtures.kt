package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.FullProductionMap
import de.bjrke.factorio.optimizer.common.model.IndexedRecipe
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.RecipeIndex

/**
 * Every planned production in plan order; a production may appear once per row. A test-only view:
 * production code reads `BlueprintPlan.rows` instead, so the plan owns no derived recipe list.
 */
internal val BlueprintPlan.recipes: List<FactoryProduction>
    get() = rows.flatMap { it.productions }

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

internal fun optimizationResult(recipe: Recipe, factory: Factory) =
    optimizationResult(production(recipe, factory))

internal fun optimizationResult(vararg productions: FactoryProduction): OptimizationResult {
    val indexedProductions =
        Array(productions.size) { index ->
            val production = productions[index]
            production.copy(
                recipe = IndexedRecipe.of(RecipeIndex(index), production.recipe.recipe),
            )
        }

    return OptimizationResult(
        targetProduction = indexedProductions.first(),
        factories = FullProductionMap(indexedProductions),
    )
}

internal fun OptimizationResult.findProductionByRecipe(recipe: Recipe): FactoryProduction =
    factories.values.first { it.recipe.recipe == recipe }
