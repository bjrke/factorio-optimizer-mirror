package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.IconLayer
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.iconLayers
import de.bjrke.factorio.optimizer.common.fixtures.bioflux
import de.bjrke.factorio.optimizer.common.fixtures.nutrients
import de.bjrke.factorio.optimizer.common.fixtures.producers
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.biochamberFuel
import de.bjrke.factorio.optimizer.common.game.prototype.forSurface
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The biochamber's fuel: nutrients, booked as an imported pseudo material to break the cycle. */
class OptimizerBiochamberFuelTest {

    private val gleba by lazy {
        Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))
    }

    @Test
    fun aBiochamberConsumesQuarterNutrientsPerSecond() {
        val result = gleba[bioflux]
        val production = result.production(bioflux)
        val fuel = result.producers(biochamberFuel)

        assertAll(
            { assertEquals(Factory.BIOCHAMBER, production.factory) },
            // 500 kW / 2 MJ = 0.25 nutrients per second per machine, independent of the recipe
            { assertEquals(0.25, production.inputPerMachine(biochamberFuel), TOLERANCE) },
            { assertTrue(fuel.isNotEmpty(), "the pseudo fuel does not appear in the plan") },
            { assertEquals(setOf(Factory.NO_FACTORY), fuel.map { it.factory }.toSet()) },
            { assertEquals("nutrients", biochamberFuel.prototypeName) },
            { assertEquals(listOf(IconLayer("nutrients")), iconLayers(biochamberFuel.name)) },
        )
    }

    @Test
    fun glebaNutrientAndBiofluxPlansStayCycleFree() =
        assertTableOf(nutrients, bioflux) { target ->
            val result = gleba[target]
            assertAll(
                { assertEquals(emptySet(), result.productionCycles(), "$target: $result") },
                {
                    assertTrue(
                        result.factories.values.any { it.material == biochamberFuel },
                        "$target does not report the biochamber's nutrient fuel",
                    )
                },
            )
        }
}

/**
 * The materials in a production cycle among the plan's built recipes. The pseudo fuel has no
 * producer, so it is an ingredient edge that never closes a cycle.
 */
private fun OptimizationResult.productionCycles(): Set<Material> {
    val recipes = factories.keys.filter { Category.IMPORT !in it.categories }
    val produced = recipes.flatMapTo(mutableSetOf()) { it.result.keys }
    val dependency =
        buildMap<Material, MutableSet<Material>> {
            for (recipe in recipes) {
                for (result in recipe.result.keys) {
                    getOrPut(result) { mutableSetOf() }
                        .addAll(recipe.ingredients.keys.filter { it in produced })
                }
            }
        }
    val cycles = mutableSetOf<Material>()
    for (start in produced) {
        val seen = mutableSetOf(start)
        val stack = ArrayDeque(dependency[start].orEmpty())
        while (stack.isNotEmpty()) {
            val next = stack.removeLast()
            if (next == start) {
                cycles += start
                break
            }
            if (seen.add(next)) stack.addAll(dependency[next].orEmpty())
        }
    }
    return cycles
}
