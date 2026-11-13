package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.carbon
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.rocketFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.MachineStats
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The burning fuel a furnace adds to the recipe it runs: `energy_usage × time / speed` joules per
 * craft, divided by the fuel's energy. The fixtures carry no machine stats, so the curated
 * fallbacks (90 kW for both furnaces) size the cases that do not set one.
 */
class OptimizerFuelTest {

    /** Every fuel the surface table can pick, at the game's own values. */
    private val fuels =
        mapOf(
            coal to 4_000_000.0,
            solidFuel to 12_000_000.0,
            rocketFuel to 100_000_000.0,
            carbon to 2_000_000.0,
        )

    /** The recipe the optimizer runs for one iron plate per second, as the plan holds it. */
    private fun smelted(
        surface: Surface = Surface.NAUVIS,
        factories: Set<Factory> = setOf(Factory.STONE_FURNACE, Factory.NO_FACTORY),
        machineStats: Map<String, MachineStats> = emptyMap(),
        fuelValues: Map<Material, Double> = fuels,
    ): Recipe =
        Optimizer(
                ParsedRecipe(
                    materials = listOf(ironPlate),
                    recipes = listOf(ironPlateSmeltingRecipe),
                    machineStats = machineStats,
                    fuelValues = fuelValues,
                    surface = surface,
                ),
                factories,
            )[ironPlate]
            .production(ironPlate)
            .recipe

    @Test
    fun aStoneFurnaceBurnsCoalForTheEnergyOfOneCraft() =
        assertEquals(0.072, smelted().ingredients.getValue(coal), TOLERANCE)

    @Test
    fun aSteelFurnaceBurnsHalfTheFuelAtTwiceTheSpeed() =
        assertEquals(
            0.036,
            smelted(factories = setOf(Factory.STEEL_FURNACE, Factory.NO_FACTORY))
                .ingredients
                .getValue(coal),
            TOLERANCE,
        )

    @Test
    fun anElectricFurnaceBurnsNoFuel() =
        assertNull(
            smelted(factories = setOf(Factory.ELECTRIC_FURNACE, Factory.NO_FACTORY))
                .ingredients[coal],
        )

    @Test
    fun theDumpedEnergyUsageWinsOverTheCuratedFallback() =
        assertEquals(
            0.144,
            smelted(
                    machineStats =
                        mapOf(
                            "stone-furnace" to
                                MachineStats(
                                    categories = setOf(Category.SMELTING),
                                    speed = 1.0,
                                    productivity = 0.0,
                                    energyUsage = 180_000.0,
                                ),
                        ),
                )
                .ingredients
                .getValue(coal),
            TOLERANCE,
        )

    @Test
    fun aSurfaceWhoseFuelHasNoKnownFuelValueBurnsNothing() =
        assertNull(smelted(fuelValues = emptyMap()).ingredients[coal])

    @Test
    fun everySurfaceBurnsItsOwnFuel() =
        assertTableOf(
            Triple(Surface.NAUVIS, coal, 0.072),
            Triple(Surface.VULCANUS, coal, 0.072),
            Triple(Surface.FULGORA, solidFuel, 0.024),
            Triple(Surface.AQUILO, solidFuel, 0.024),
            Triple(Surface.GLEBA, rocketFuel, 0.00288),
            Triple(Surface.SPACE, carbon, 0.144),
        ) { (surface, fuel, amount) ->
            assertEquals(amount, smelted(surface = surface).ingredients.getValue(fuel), TOLERANCE)
        }

    @Test
    fun theFuelIsAMaterialThePlanHasToSupply() {
        val result =
            Optimizer(
                ParsedRecipe(
                    materials = listOf(ironPlate),
                    recipes = listOf(ironPlateSmeltingRecipe),
                    fuelValues = fuels,
                ),
                setOf(Factory.STONE_FURNACE, Factory.NO_FACTORY),
            )[ironPlate]

        assertAll(
            { assertContains(result.factories.values.map { it.material }, coal) },
            // nothing mines coal in this fixture, so the plan has to import the fuel
            { assertEquals(setOf(Category.IMPORT), result.production(coal).recipe.categories) },
        )
    }
}
