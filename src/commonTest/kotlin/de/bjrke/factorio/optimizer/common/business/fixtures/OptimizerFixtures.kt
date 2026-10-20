package de.bjrke.factorio.optimizer.common.business.fixtures

import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.Recipe

/** A single-category recipe that makes [result] from [ingredients] in [time] seconds. */
internal fun testRecipe(
    name: String,
    category: Category,
    result: Map<Material, Double>,
    ingredients: Map<Material, Double> = emptyMap(),
    time: Double = 1.0,
) =
    Recipe(
        name = name,
        ingredients = ingredients,
        result = result,
        time = time,
        categories = setOf(category),
    )

/** The factory the optimizer picks for [target] from [recipes], restricted to [factories]. */
internal fun factoryOf(target: Material, recipes: List<Recipe>, factories: Set<Factory>) =
    Optimizer(ParsedRecipe(emptyList(), recipes), factories)[target].production(target).factory
