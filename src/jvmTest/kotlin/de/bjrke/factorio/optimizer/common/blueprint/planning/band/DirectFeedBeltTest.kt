package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.fluoroketoneCold
import de.bjrke.factorio.optimizer.common.fixtures.fluoroketoneHot
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.quantumProcessorRecipe
import de.bjrke.factorio.optimizer.common.fixtures.railgun
import de.bjrke.factorio.optimizer.common.fixtures.railgunRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import kotlin.test.Test
import kotlin.test.assertEquals

private fun production(
    recipe: Recipe,
    material: Material,
    amount: Double = 1.0,
    factory: Factory = Factory.ASSEMBLING_MACHINE_3,
) =
    FactoryProduction(
        material,
        recipe,
        factory,
        amount,
        0,
        1.0,
        0.0,
    )

private fun fluids(vararg materials: Material) =
    LogisticsCalculator(testLogistics.copy(fluids = materials.toSet()))

private fun factories(vararg productions: FactoryProduction) = productions.associateBy {
    it.recipe
}

/** The belted rows beside a band: what the plan still has to build on a belt, and at what rate. */
class DirectFeedBeltTest {

    @Test
    fun aFluidLabelledRecipeSizesItsBeltRowByTheSolidProduct() {
        // a cryogenic plant and an assembling machine share no row, so the quantum processor stays
        // belted; the plan labels its production with the fluoroketone it also makes
        val rail =
            production(railgunRecipe, railgun, amount = 0.15, factory = Factory.CRYOGENIC_PLANT)
        val quantum = production(quantumProcessorRecipe, fluoroketoneHot)
        val feed =
            directFeed(
                rail,
                factories(rail, quantum),
                logistics = fluids(fluoroketoneHot, fluoroketoneCold),
            )

        // 0.3 quantum processors/s at one per machine craft needs nine machines, not the two the
        // five-times-larger fluoroketone result would suggest
        assertEquals(
            9,
            feed.rows.single { it.machines.first().recipe == quantumProcessorRecipe }.machines.size,
        )
    }

    @Test
    fun aTargetThatAnotherIncludedRecipeConsumesGetsOneRow() {
        val gearProduction = production(ironGearWheelRecipe, gear)
        val beltProduction = production(transportBeltRecipe, transportBelt)
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
            feed.rows.count { row -> row.machines.any { it.recipe == ironGearWheelRecipe } },
        )
    }
}
