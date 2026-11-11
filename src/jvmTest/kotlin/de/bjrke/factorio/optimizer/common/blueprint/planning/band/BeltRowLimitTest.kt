package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** A band still cannot exceed the four belt rows even when its inputs share a lane. */
class BeltRowLimitTest {

    @Test
    fun aBandThatNeedsTooManyBeltRowsIsRejected() {
        val ore = Material("ore")
        val widget = Material("widget")
        val gadget = Material("gadget")
        val widgetRecipe =
            Recipe(
                name = "widget",
                ingredients = mapOf(ore to 1.0),
                result = mapOf(widget to 1.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            )
        val gadgetRecipe =
            Recipe(
                name = "gadget",
                ingredients =
                    mapOf(widget to 1.0) + (1..8).associate { Material("part-$it") to 1.0 },
                result = mapOf(gadget to 1.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            )
        val gadgetProduction =
            FactoryProduction(
                gadget,
                gadgetRecipe,
                Factory.ASSEMBLING_MACHINE_3,
                1.0,
                0,
            )
        val widgetProduction =
            FactoryProduction(
                widget,
                widgetRecipe,
                Factory.ASSEMBLING_MACHINE_3,
                1.0,
                0,
            )
        val feed =
            directFeed(
                gadgetProduction,
                mapOf(gadgetRecipe to gadgetProduction, widgetRecipe to widgetProduction),
                calculator(),
            )

        assertAll(
            // eight imported parts, the producer's ore and the product are ten materials; even
            // with four mixed input belts the row cannot fit four rows
            { assertEquals(emptyList(), feed.rows.filter { it.links.isNotEmpty() }) },
            {
                assertEquals(
                    setOf(gadgetRecipe, widgetRecipe),
                    feed.rows.flatMap { it.productions }.map { it.recipe }.toSet(),
                )
            },
        )
    }
}
