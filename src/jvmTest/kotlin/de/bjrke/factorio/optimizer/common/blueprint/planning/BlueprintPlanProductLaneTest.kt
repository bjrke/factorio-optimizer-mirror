package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.testBlueprintPlan
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BlueprintPlanProductLaneTest : BaseGameCtxTest() {

    private val logistics = calculator()

    @Test
    fun aSolidTargetShrinksToTheProductLane() {
        // copper cable: 2 per 0.5 s at assembling machine 3's 1.25 speed, so 0.2 machines per 1/s
        val cable = production(copperCableRecipe, amount = 0.2)

        val planned = testBlueprintPlan(optimizationResult(cable), logistics, 20.0)

        assertAll(
            // one product lane carries 15/s, so the requested 20/s is drawn at 15/s
            { assertEquals(15.0, planned.scale, TOLERANCE) },
            { assertEquals(3, planned.rows.single().machines.size) },
            { assertEquals("copper-cable (75%, place 2x for full rate)", planned.label) },
            { assertContains(planned.sources.map { it.material }, copperPlate) },
        )
    }

    @Test
    fun aNonStackingInserterCannotUseTheBeltStackForItsProductLane() {
        val cable = production(copperCableRecipe, amount = 0.2)
        val result = optimizationResult(cable)

        // a stack inserter fills the lane with four items per slot, so 20/s fits
        val stackInserter =
            calculator(
                LogisticsCalculatorSelection(inserter = InserterTier.STACK_INSERTER, beltStack = 4),
            )
        // a fast inserter places single items, so the same lane still carries only 15/s
        val fastInserter = calculator(LogisticsCalculatorSelection(beltStack = 4))

        assertAll(
            { assertEquals(20.0, testBlueprintPlan(result, stackInserter, 20.0).scale, TOLERANCE) },
            { assertEquals(15.0, testBlueprintPlan(result, fastInserter, 20.0).scale, TOLERANCE) },
        )
    }

    @Test
    fun aDirectOutputMachineUsesItsOwnBeltStackingNotTheInserters() {
        val highOutputMining =
            Recipe(
                name = "high-output-mining",
                ingredients = emptyMap(),
                result = mapOf(ironOre to 10.0),
                time = 1.0,
                categories = setOf(Category.MINING_BASIC_SOLID),
            )
        // 5/s per electric mining drill, so 0.2 machines per 1/s
        val electric = production(highOutputMining, Factory.ELECTRIC_MINING_DRILL, amount = 0.2)
        // 25/s per big mining drill, so 0.04 machines per 1/s
        val big = production(highOutputMining, Factory.BIG_MINING_DRILL, amount = 0.04)
        val stackInserter =
            calculator(
                LogisticsCalculatorSelection(inserter = InserterTier.STACK_INSERTER, beltStack = 4),
            )

        assertAll(
            // the electric drill places single items, so the inserter's stack does not raise its
            // lane
            {
                assertEquals(
                    15.0,
                    testBlueprintPlan(optimizationResult(electric), stackInserter, 20.0).scale,
                    TOLERANCE,
                )
            },
            // the big mining drill stacks its own output, so the requested belt stack applies
            {
                assertEquals(
                    20.0,
                    testBlueprintPlan(optimizationResult(big), stackInserter, 20.0).scale,
                    TOLERANCE,
                )
            },
        )
    }

    @Test
    fun aTargetBelowTheProductLaneKeepsTheRequestedScale() {
        val cable = production(copperCableRecipe, amount = 0.2)

        val planned =
            testBlueprintPlan(
                optimizationResult(cable),
                logistics,
                7.5,
            )

        assertAll(
            { assertEquals(7.5, planned.scale, TOLERANCE) },
            { assertEquals(2, planned.rows.single().machines.size) },
            { assertEquals("copper-cable", planned.label) },
        )
    }

    /**
     * The lane is judged by the rate the plan must deliver, not by whole-machine output: one
     * machine making more than a lane is throttled by its belt, so the requested rate still fits.
     */
    @Test
    fun aSingleMachineOverOneLaneDoesNotShrinkBelowItsDemand() {
        // synthetic: 10 gear per craft is 12.5/s per assembling machine 3, over a yellow lane
        val highOutput =
            Recipe(
                name = "high-output",
                ingredients = emptyMap(),
                result = mapOf(gear to 10.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            )
        // 12.5/s per machine, so 0.08 machines per 1/s
        val craft = production(highOutput, amount = 0.08)
        val slowBelt = calculator(LogisticsCalculatorSelection(belt = BeltTier.TRANSPORT_BELT))

        val planned = testBlueprintPlan(optimizationResult(craft), slowBelt, 5.0)

        assertAll(
            // 5/s fits the 7.5/s yellow lane even though the one machine could make 12.5/s
            { assertEquals(5.0, planned.scale, TOLERANCE) },
            { assertEquals(1, planned.rows.single().machines.size) },
            { assertEquals("iron-gear-wheel", planned.label) },
        )
    }

    @Test
    fun anOverCapacityIntermediateIsDirectFedInsteadOfShrinking() {
        // at 6/s the circuit row needs 18/s of cable, over one 15/s lane, but a band can insert it
        val circuit = production(electronicCircuitRecipe, amount = 0.4)
        val cable = production(copperCableRecipe, amount = 1.5)

        val planned =
            testBlueprintPlan(
                optimizationResult(circuit, cable),
                logistics,
                6.0,
            )

        assertAll(
            { assertEquals(6.0, planned.scale, TOLERANCE) },
            { assertEquals("electronic-circuit", planned.label) },
            { assertContainsNot(planned.beltMaterials, copperCable) },
            { assertContains(planned.rows.flatMap { it.links }.map { it.material }, copperCable) },
        )
    }

    /**
     * A pair whose band needs more than the machine budget must shrink the plan, not fall back onto
     * a belt for its intermediate: that belt would then shrink the plan from a different baseline.
     */
    @Test
    fun aBandOverTheMachineBudgetShrinksInsteadOfBeltingItsInput() {
        val circuit = production(electronicCircuitRecipe, amount = 0.4)
        val cable = production(copperCableRecipe, amount = 1.5)

        val planned = testBlueprintPlan(optimizationResult(circuit, cable), logistics, 12.0)

        assertAll(
            // the propagated need is 5 circuit + 8 cable machines, over the 10-machine budget, so
            // one exact step draws the pair at 10/13 of the requested scale
            { assertEquals(120.0 / 13.0, planned.scale, TOLERANCE) },
            { assertEquals("electronic-circuit (77%, place 2x for full rate)", planned.label) },
            { assertContainsNot(planned.beltMaterials, copperCable) },
            { assertContains(planned.rows.flatMap { it.links }.map { it.material }, copperCable) },
            { assertEquals(10, planned.rows.single().machines.size) },
        )
    }
}
