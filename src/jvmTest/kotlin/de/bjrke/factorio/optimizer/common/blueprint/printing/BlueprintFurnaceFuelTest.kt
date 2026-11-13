package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.copperOre
import de.bjrke.factorio.optimizer.common.fixtures.rocketFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A burner furnace needs a second solid input, and the plan pays for it: the fuel becomes a
 * material the layout feeds, whether the surface imports it (coal on Nauvis) or builds it (solid
 * fuel from ammonia on Aquilo, rocket fuel from jelly on Gleba).
 */
class BlueprintFurnaceFuelTest {

    private val stoneFurnace: Set<Factory> =
        FactoryConfig().let { config -> config.add(config.defaults, Factory.STONE_FURNACE) }

    /**
     * Early-game choices: a stone furnace and assembling machine 1, so the blueprint uses small
     * poles.
     */
    private val earlyFurnace: Set<Factory> =
        FactoryConfig().let { config ->
            config.add(
                config.add(config.defaults, Factory.STONE_FURNACE),
                Factory.ASSEMBLING_MACHINE_1,
            )
        }

    /** The solid materials the layout's belts carry, mixed lanes included. */
    private fun Layout.belted(): List<Material> = beltLoads.values.flatMap { it.materials }

    private fun Layout.furnaceIngredients(): Set<Material> =
        plan.rows.flatMap { row -> row.machines }.flatMap { it.recipe.ingredients.keys }.toSet()

    @Test
    fun aStoneFurnaceRowIsFedWithImportedCoal() {
        val layout = Layout("iron-plate", factories = stoneFurnace)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertContains(layout.plan.sources.map { it.material }, coal) },
            { assertContains(layout.belted(), coal) },
            { assertContains(layout.furnaceIngredients(), coal) },
        )
    }

    /**
     * Fuel and ore share one belt, one per lane, so the furnace row keeps a column for its pole.
     */
    @Test
    fun anEarlyStoneFurnaceSharesTheOreBeltWithItsFuel() {
        val layout = Layout("copper-plate", scale = 7.5, factories = earlyFurnace)
        val mixed =
            layout.entities.filterIsInstance<BeltEntity>().filter {
                it.load.materials == setOf(copperOre, coal)
            }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertTrue(mixed.isNotEmpty(), "fuel and ore do not share a belt") },
        )
    }

    @Test
    fun aquiloBeltsTheSolidFuelItsFurnaceBurns() {
        val layout = Layout("iron-plate", surface = Surface.AQUILO, factories = stoneFurnace)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // the cryogenic plant refines the solid fuel from ammonia, so the plan belts it
            { assertContains(layout.plan.beltMaterials, solidFuel) },
            { assertContains(layout.belted(), solidFuel) },
            { assertEquals(emptyList(), layout.plan.sources.filter { it.material == solidFuel }) },
        )
    }

    @Test
    fun glebaBeltsTheRocketFuelItsFurnaceBurns() {
        val layout = Layout("iron-plate", surface = Surface.GLEBA, factories = stoneFurnace)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertContains(layout.plan.beltMaterials, rocketFuel) },
            { assertContains(layout.belted(), rocketFuel) },
        )
    }
}
