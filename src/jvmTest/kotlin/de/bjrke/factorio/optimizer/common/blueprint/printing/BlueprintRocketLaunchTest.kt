package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.lowDensityStructure
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.rocketFuel
import de.bjrke.factorio.optimizer.common.fixtures.rocketPart
import de.bjrke.factorio.optimizer.common.fixtures.satellite
import de.bjrke.factorio.optimizer.common.fixtures.spaceScience
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.optimize.Optimizer
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** The 9x9 rocket silo in both games: its ingredients in, its product out. */
class BlueprintRocketLaunchTest : BaseGameCtxTest() {

    /**
     * The 9x9 silo imports the three ingredients and puts the rocket parts on its product belt. The
     * 0.2 scale draws one silo. The belt only removes the silo's cargo, so a player launching the
     * parts can delete it.
     */
    @Test
    fun theRocketSiloFeedsItsThreeIngredientsAndExportsTheParts() {
        val ingredients = setOf(processingUnit, lowDensityStructure, rocketFuel)
        val result =
            Optimizer(
                forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
                MAX_INTERMEDIATE_LIMIT,
                ingredients,
            )[rocketPart]
        val layout = Layout(result = result, scale = 0.2, beltEnd = BeltEnd.CONSTANT_COMBINATOR)
        val silo = layout.machine("rocket-silo")
        val targetRow = layout.plan.rows.single { rocketPart in it.beltMaterials }

        assertAll(
            { assertEquals(9, silo.area.width) },
            { assertEquals(9, silo.area.height) },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(ingredients, layout.plan.sources.map { it.material }.toSet()) },
            { assertEquals(ingredients, targetRow.machines.first().recipe.ingredients.keys) },
            { assertEquals(ingredients + rocketPart, layout.beltMaterials.values.toSet()) },
            {
                assertEquals(
                    emptyList(),
                    layout.entities.filterIsInstance<InfinityChestEntity>().filter {
                        it.settings.item == rocketPart
                    },
                )
            },
        )
    }

    /**
     * The base game's space science is one silo cycle: the 9x9 silo imports the three part
     * ingredients and the satellite and puts the 1000 packs on its product belt. The 0.2 scale
     * draws one silo, and no second silo makes rocket parts.
     */
    @Test
    fun theBaseGameSiloTakesThePartIngredientsAndTheSatellite() {
        val ingredients = setOf(processingUnit, lowDensityStructure, rocketFuel)
        val imports = ingredients + satellite
        val result =
            Optimizer(
                forSurface(Surface.NAUVIS, spaceAge = false),
                FactoryConfig(spaceAge = false).defaults,
                MAX_INTERMEDIATE_LIMIT,
                imports,
            )[spaceScience]
        val layout = Layout(result = result, scale = 0.2, beltEnd = BeltEnd.CONSTANT_COMBINATOR)
        val silo = layout.machine("rocket-silo")
        val targetRow = layout.plan.rows.single { spaceScience in it.beltMaterials }

        assertAll(
            { assertEquals(9, silo.area.width) },
            { assertEquals(9, silo.area.height) },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(imports, layout.plan.sources.map { it.material }.toSet()) },
            { assertEquals(imports, targetRow.machines.first().recipe.ingredients.keys) },
            { assertEquals(imports + spaceScience, layout.beltMaterials.values.toSet()) },
            { assertContainsNot(layout.plan.beltMaterials, rocketPart) },
        )
    }

    /** The base game keeps the dumped one-part recipe, so its silo layout is unchanged. */
    @Test
    fun theBaseGameSiloKeepsTheDumpedPartRecipe() {
        val result =
            Optimizer(
                forSurface(Surface.NAUVIS, spaceAge = false),
                FactoryConfig(spaceAge = false).defaults,
                MAX_INTERMEDIATE_LIMIT,
                setOf(processingUnit, lowDensityStructure, rocketFuel),
            )[rocketPart]
        val layout = Layout(result = result, scale = 0.2, beltEnd = BeltEnd.CONSTANT_COMBINATOR)

        assertAll(
            { assertEquals(1.0, result.production(rocketPart).recipe.result.getValue(rocketPart)) },
            { assertContains(layout.plan.beltMaterials, rocketPart) },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }
}
