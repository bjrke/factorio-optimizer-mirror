package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.burnerFuel

/**
 * The machine choice and rates for one parsed prototype set: which selected factory runs a recipe,
 * what stats that machine has and how much one machine consumes or produces per second. Keeping
 * these together leaves the [Optimizer] to the material balance.
 */
internal class MachineCatalog(
    private val parsedRecipe: ParsedRecipe,
    private val factories: Set<Factory>,
) {
    /** The surface's one burning fuel and the energy it releases per item. */
    private val fuel = burnerFuel(parsedRecipe.surface)
    private val fuelValue = parsedRecipe.fuelValues[fuel] ?: 0.0
    private val fuelledRecipes = mutableMapOf<Recipe, Recipe>()

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
     * [recipe] as the machine the optimizer picks for it runs it: a burner furnace burns [fuel] on
     * top of the recipe's own ingredients, so the plan pays for the fuel and the blueprint feeds
     * it. The amount is the machine's energy per craft (`energyUsage × time / speed`) divided by
     * the fuel's energy. It is fractional: a furnace burns a whole item and buffers the rest, so
     * the average is what its belt has to deliver. An electric machine, or a surface whose fuel has
     * no known fuel value, keeps the recipe untouched.
     */
    fun fuelled(recipe: Recipe): Recipe {
        val factory = findFactory(recipe) ?: return recipe
        if (!factory.burner || fuelValue <= 0.0) return recipe
        return fuelledRecipes.getOrPut(recipe) {
            val energyPerCraft = energyUsageOf(factory) * recipe.time / speedOfFactory(factory)
            if (energyPerCraft <= 0.0) recipe
            else
                recipe.copy(ingredients = recipe.ingredients + (fuel to energyPerCraft / fuelValue))
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
