package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.factories
import de.bjrke.factorio.optimizer.common.fixtures.fluoroketoneHot
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.quantumProcessorRecipe
import de.bjrke.factorio.optimizer.common.fixtures.railgunRecipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import kotlin.test.Test
import kotlin.test.assertEquals

/** The belted rows beside a band: what the plan still has to build on a belt, and at what rate. */
class DirectFeedBeltTest : BaseGameCtxTest() {

    @Test
    fun aFluidLabelledRecipeSizesItsBeltRowByTheSolidProduct() {
        // a cryogenic plant and an assembling machine share no row, so the quantum processor stays
        // belted; the plan labels its production with the fluoroketone it also makes
        val rail = production(railgunRecipe, Factory.CRYOGENIC_PLANT, amount = 0.15)
        val quantum = production(quantumProcessorRecipe, material = fluoroketoneHot)
        val feed =
            directFeed(
                rail,
                factories(rail, quantum),
                logistics = calculator(),
            )

        // 0.3 quantum processors/s at one per machine craft needs nine machines, not the two the
        // five-times-larger fluoroketone result would suggest
        assertEquals(
            9,
            feed.rows
                .single { it.machines.first().recipe.recipe == quantumProcessorRecipe }
                .machines
                .size,
        )
    }

    @Test
    fun aTargetThatAnotherIncludedRecipeConsumesGetsOneRow() {
        val gearProduction = production(ironGearWheelRecipe)
        val beltProduction = production(transportBeltRecipe)
        val feed =
            directFeed(
                gearProduction,
                factories(gearProduction, beltProduction),
                logistics = calculator(),
            )

        // the belt recipe consumes gear, but the target row is the only gear row: the belted pass
        // must not add a second one
        assertEquals(
            1,
            feed.rows.count { row ->
                row.machines.any { it.recipe.recipe == ironGearWheelRecipe }
            },
        )
    }
}
