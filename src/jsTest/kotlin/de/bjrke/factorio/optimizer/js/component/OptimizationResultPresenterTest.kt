package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.test.ComponentTest
import de.bjrke.test.fireEvent
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import react.create

private val ore = Material.of("iron-ore")
private val plate = Material.of("iron-plate")
private val gear = Material.of("iron-gear-wheel")

private val miningRecipe =
    Recipe(
        name = "iron-ore",
        ingredients = emptyMap(),
        result = mapOf(ore to 1.0),
        time = 1.0,
        categories = setOf(Category.MINING_BASIC_SOLID),
    )

private val craftingRecipe =
    Recipe(
        name = "iron-gear-wheel",
        ingredients = mapOf(plate to 2.0),
        result = mapOf(gear to 1.0),
        time = 0.5,
        categories = setOf(Category.CRAFTING),
    )

private fun optimizationResult(recipe: Recipe, target: Material, factory: Factory) =
    OptimizationResult(
        target = target,
        recipe = recipe,
        factories =
            mapOf(
                recipe to
                    FactoryProduction(
                        material = target,
                        recipe = recipe,
                        factory = factory,
                        amount = 1.0,
                        level = 0,
                    )
            ),
    )

class OptimizationResultPresenterTest : ComponentTest() {
    private val normalizedTo = mutableListOf<Double>()

    private fun renderResult(result: OptimizationResult) =
        render(
            optimizationResult.create {
                optimizationResult = result
                normalizeTarget = 1.0
                changeMaterial = {}
                changeNormalizeTo = { normalizedTo += it }
            }
        )

    @Test
    fun recipeWithoutIngredientsRendersOnlyTheOutputAmountInput() {
        val result = optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL)

        val rendered = renderResult(result)

        // the factory amount input plus the single output amount input
        assertEquals(2, rendered.container.querySelectorAll("input[type=number]").length)
    }

    @Test
    fun recipeWithIngredientsRendersIngredientAndOutputAmountInputs() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        val rendered = renderResult(result)

        // the factory amount input, one ingredient input and one output input
        assertEquals(3, rendered.container.querySelectorAll("input[type=number]").length)
    }

    @Test
    fun clickingTheFactoryIconNormalizesToTheFactoryAmount() {
        val result = optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL)
        renderResult(result)

        fireEvent.click(screen.getByTitle(Factory.ELECTRIC_MINING_DRILL.identifier))

        assertEquals(listOf(1.0), normalizedTo)
    }
}
