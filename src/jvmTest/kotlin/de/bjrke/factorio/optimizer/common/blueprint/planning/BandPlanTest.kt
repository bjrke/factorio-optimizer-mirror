package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.beltRowBudget
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.oneLaneCapacity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowInputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowOutputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlanner
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.testBlueprintPlan
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.indexedForTest
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The band plans: the per-copy machine rate and the finished-row belt check. */
class BandPlanTest : BaseGameCtxTest() {

    private val logistics = calculator()

    /**
     * A band repeats a production to balance its links. Its belts must carry the plan's share of
     * that production, not the full rate of every copy, or a scaled plan sizes them for a
     * throughput it does not have.
     */
    @Test
    fun aRepeatedBandMachineRunsAtThePlanShareOfItsRate() {
        val gearProduction = production(ironGearWheelRecipe, amount = 0.4)
        val repeated =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow(listOf(gearProduction, gearProduction))),
                sources = emptyList(),
                scale = 0.25,
                label = "",
            )
        val single =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow(listOf(gearProduction))),
                sources = emptyList(),
                scale = 4.0,
                label = "",
            )

        assertAll(
            { assertEquals(0.4 * 0.25 / 2, repeated.machineRate(gearProduction), TOLERANCE) },
            { assertEquals(1.0, single.machineRate(gearProduction), TOLERANCE) },
        )
    }

    @Test
    fun aMaterialMadeAndTakenByOneRecipeIsDirectInserted() {
        val belts = production(transportBeltRecipe, level = 2)
        val gears = production(ironGearWheelRecipe, level = 1)
        val result = optimizationResult(belts, gears)
        val planned = testBlueprintPlan(result, logistics)

        val band = planned.rows.single { it.links.isNotEmpty() }
        assertAll(
            {
                assertEquals(
                    setOf(transportBeltRecipe, ironGearWheelRecipe),
                    band.machines.mapTo(mutableSetOf()) { it.recipe.recipe },
                )
            },
            { assertEquals(setOf(gear), band.links.mapTo(mutableSetOf()) { it.material }) },
            { assertEquals(setOf(transportBelt), planned.beltMaterials) },
        )
    }

    /**
     * The band search counts lanes only, so a band with two capacity-safe inputs of the same
     * material rate passes it even when the finished feed sides keep them apart. The final check
     * must re-pack the row with its real directions and report the band.
     */
    @Test
    fun aBandWithOppositeProducedFeedsIsRejected() {
        val band = oppositeFeedBand()
        val plan =
            BlueprintPlan(
                target = band.target,
                rows = band.rows,
                sources = emptyList(),
                label = "",
            )
        val links = band.row.links.mapTo(mutableSetOf()) { it.material }
        val inputs = rowInputs(band.row.machines) - band.row.fullyInternalInputs
        val outputs = rowOutputs(band.row.machines) - links
        val budget =
            beltRowBudget(
                inputs,
                outputs,
                logistics.oneLaneCapacity(),
                allowOverCapacityPairs = false,
            )
        val planner = RowPlanner(plan, logistics)

        assertAll(
            { assertNotEquals(planner.feedDirection(band.east), planner.feedDirection(band.west)) },
            { assertTrue(budget.fits, "the direction-blind budget accepts $inputs + $outputs") },
            {
                assertEquals(
                    setOf(
                        setOf(
                            band.linkProducer.recipe,
                            band.consumerRecipe.indexedForTest(),
                        ),
                    ),
                    failingBandGroups(band.rows, band.target, 1.0, logistics),
                )
            },
        )
    }
}

/** A band whose two capacity-safe produced inputs land on opposite feed sides. */
private data class OppositeFeedBand(
    val target: Material,
    val rows: List<PlannedRow>,
    val row: PlannedRow,
    val linkProducer: FactoryProduction,
    val consumerRecipe: Recipe,
    val east: Material,
    val west: Material,
)

/**
 * The producer row widths order the networks so [OppositeFeedBand.east] feeds east and
 * [OppositeFeedBand.west] west. `x2`/`x3` are above one lane, so they cannot bridge the two, and a
 * multi-recipe band may not use the over-capacity rescue.
 */
private fun BandPlanTest.oppositeFeedBand(): OppositeFeedBand {
    val east = Material("a")
    val west = Material("b")
    val linked = Material("x1")
    val fastA = Material("x2")
    val fastB = Material("x3")
    val target = Material("band-target")

    fun producer(material: Material) =
        production(
            Recipe(
                name = "make-${material.name}",
                ingredients = emptyMap(),
                result = mapOf(material to 1.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            ),
        )

    val linkProducer = producer(linked)
    val consumerRecipe =
        Recipe(
            name = "band-consumer",
            ingredients =
                mapOf(east to 1.0, west to 1.0, linked to 1.0, fastA to 20.0, fastB to 20.0),
            result = mapOf(target to 1.0),
            time = 1.0,
            categories = setOf(Category.CRAFTING),
        )

    val row =
        PlannedRow(
            machines = listOf(linkProducer, production(consumerRecipe)),
            links = listOf(PlannedLink(0, linked, 1.0, producerIsLeft = true)),
        )
    val rows =
        listOf(
            PlannedRow.copies(producer(east), 1),
            PlannedRow.copies(producer(fastA), 2),
            PlannedRow.copies(producer(fastB), 3),
            PlannedRow.copies(producer(west), 4),
            row,
        )
    return OppositeFeedBand(target, rows, row, linkProducer, consumerRecipe, east, west)
}
