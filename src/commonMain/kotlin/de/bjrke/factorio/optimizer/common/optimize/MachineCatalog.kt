package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryFuel
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.biochamberFuel
import de.bjrke.factorio.optimizer.common.game.prototype.burnerFuel
import de.bjrke.factorio.optimizer.common.game.prototype.nutrients

/**
 * The machine choice and rates for one parsed prototype set: which selected factory runs a recipe,
 * what stats that machine has and how much one machine consumes or produces per second. Keeping
 * these together leaves the [Optimizer] to the material balance.
 */
internal class MachineCatalog(
    private val parsedRecipe: ParsedRecipe,
    private val factories: Set<Factory>,
) {
    /** One fuel ingredient and the energy the item it stands for releases. */
    private class Fuel(val ingredient: Material, val value: Double)

    private val fuelledRecipes = mutableMapOf<Recipe, Recipe>()

    /**
     * The fuel [factory] needs: the surface's burning fuel for a furnace, the pseudo nutrient fuel
     * for the biochamber. The pseudo material has no fuel value of its own, so [nutrients] supplies
     * the energy. Null when the machine is not fuelled or the fuel has no known value.
     */
    private fun fuelOf(factory: Factory): Fuel? {
        val (ingredient, burned) =
            when (factory.fuel) {
                FactoryFuel.BURNING ->
                    parsedRecipe.gameCtx.materialRegistry.burnerFuel(parsedRecipe.surface).let {
                        it to it
                    }
                FactoryFuel.NUTRIENTS ->
                    parsedRecipe.gameCtx.materialRegistry.biochamberFuel() to
                        parsedRecipe.gameCtx.materialRegistry.nutrients()
                null -> return null
            }
        val value = parsedRecipe.fuelValues[burned] ?: 0.0
        return if (value <= 0.0) null else Fuel(ingredient, value)
    }

    /** The machine's categories, from the dump when present, otherwise the curated fallback. */
    fun categoriesOf(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.categories ?: factory.categories

    fun speedOfFactory(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.speed ?: factory.speed

    fun productivityOfFactory(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.productivity ?: factory.productivity

    /** The machine's energy use in watts, from the dump when present, else the curated fallback. */
    fun energyUsageOf(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.energyUsage ?: factory.energyUsage

    /**
     * The machine that runs [recipe]: of all selected machines that accept any of the recipe's
     * categories, prefer the Space Age speciality machines, then the one with the highest
     * productivity, then speed. The identifier keeps the choice deterministic.
     */
    fun findFactory(recipe: Recipe) =
        factories
            .filter { factory -> categoriesOf(factory).any { it in recipe.categories } }
            .maxWithOrNull(
                compareBy(
                    { it.preferred },
                    { productivityOfFactory(it) },
                    { speedOfFactory(it) },
                    { it },
                ),
            )

    fun speedOf(recipe: Recipe) = findFactory(recipe)?.let(::speedOfFactory) ?: 1.0

    fun productivityOf(recipe: Recipe) = findFactory(recipe)?.let(::productivityOfFactory) ?: 0.0

    /**
     * [recipe] as the machine the optimizer picks for it runs it: a fuelled machine consumes its
     * fuel on top of the recipe's own ingredients, so the plan pays for it and the blueprint feeds
     * it. The amount is the machine's energy per craft (`energyUsage × time / speed`) divided by
     * the fuel's energy. It is fractional: a machine consumes a whole item and buffers the rest, so
     * the average is what its belt has to deliver. A biochamber's pseudo nutrient fuel is imported
     * (see [biochamberFuel]); an electric machine, or a fuel with no known value, keeps the recipe
     * untouched.
     */
    fun fuelled(recipe: Recipe): Recipe {
        val factory = findFactory(recipe) ?: return recipe
        val fuel = fuelOf(factory) ?: return recipe
        return fuelledRecipes.getOrPut(recipe) {
            val energyPerCraft = energyUsageOf(factory) * recipe.time / speedOfFactory(factory)
            if (energyPerCraft <= 0.0) recipe
            else
                recipe.copy(
                    ingredients =
                        recipe.ingredients + (fuel.ingredient to energyPerCraft / fuel.value),
                )
        }
    }

    /** Units of [amount] consumed per second by one machine running [recipe]. */
    fun inputRate(amount: Double, recipe: Recipe) = speedOf(recipe) * amount / recipe.time

    /**
     * Units of [amount] produced per second by one machine running [recipe]; the machine's built-in
     * productivity bonus increases the output without increasing the inputs.
     */
    fun outputRate(amount: Double, recipe: Recipe) =
        speedOf(recipe) * (1.0 + productivityOf(recipe)) * amount / recipe.time
}
