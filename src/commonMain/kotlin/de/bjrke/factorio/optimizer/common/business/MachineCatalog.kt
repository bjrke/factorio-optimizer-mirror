package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.Recipe

/**
 * The machine choice and rates for one parsed prototype set: which selected factory runs a recipe,
 * what stats that machine has and how much one machine consumes or produces per second. Keeping
 * these together leaves the [Optimizer] to the material balance.
 */
internal class MachineCatalog(
    private val parsedRecipe: ParsedRecipe,
    private val factories: Set<Factory>,
) {
    /** The machine's categories, from the dump when present, otherwise the curated fallback. */
    fun categoriesOf(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.categories ?: factory.categories

    fun speedOfFactory(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.speed ?: factory.speed

    fun productivityOfFactory(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.productivity ?: factory.productivity

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
                    { it.identifier },
                ),
            )

    fun speedOf(recipe: Recipe) = findFactory(recipe)?.let(::speedOfFactory) ?: 1.0

    fun productivityOf(recipe: Recipe) = findFactory(recipe)?.let(::productivityOfFactory) ?: 0.0

    /** Units of [amount] consumed per second by one machine running [recipe]. */
    fun inputRate(amount: Double, recipe: Recipe) = speedOf(recipe) * amount / recipe.time

    /**
     * Units of [amount] produced per second by one machine running [recipe]; the machine's built-in
     * productivity bonus increases the output without increasing the inputs.
     */
    fun outputRate(amount: Double, recipe: Recipe) =
        speedOf(recipe) * (1.0 + productivityOf(recipe)) * amount / recipe.time
}
