package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.castingIronRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.rocketFuelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromLightOilRecipe
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every recipe in row order; a recipe may appear once per row. */
private val DirectFeed.recipes
    get() = rows.flatMap { it.productions }.map { it.recipe.recipe }

/** The direct-feed bands among the rows. */
private val DirectFeed.bands
    get() = rows.filter { it.links.isNotEmpty() }

class DirectFeedTest : BaseGameCtxTest() {

    private val logistics = calculator()

    @Test
    fun aPreferredCycleAllowsOverfeedAndPenalizesUnderfeed() =
        assertTable(
            listOf<Pair<Double, Pair<Int, Int>?>>(
                // overfeed is allowed; an underfed pattern loses to an exact or overfed one but is
                // not rejected here. Actual feasibility is decided by the plan-rate window.
                0.8 to (1 to 1),
                1.0 to (1 to 1),
                1.05 to (1 to 1),
                1.1 to (3 to 2),
                1.5 to (3 to 2),
                2.0 to (2 to 1),
                0.5 to (1 to 2),
                20.0 to (5 to 1),
                0.05 to (1 to 5),
                0.66 to (2 to 3),
            ),
        ) { (ratio, expected) ->
            assertEquals(expected, cycleRatio(ratio))
        }

    @Test
    fun anExactRatioHasNoCyclePenalty() =
        assertTable(
            listOf(1.0, 1.5, 2.0, 0.5),
        ) { ratio ->
            assertEquals(0.0, cyclePenalty(ratio), TOLERANCE)
        }

    @Test
    fun perMachineRatesFollowTheRecipeAndTheProductivity() {
        val machine = production(ironPlateRecipe, speed = 2.0, productivity = 0.5)

        assertAll(
            { assertEquals(2.0, machine.inputPerMachine(ironOre), TOLERANCE) },
            // the productivity bonus is what a direct-fed consumer really receives
            { assertEquals(3.0, machine.outputPerMachine(), TOLERANCE) },
        )
    }

    @Test
    fun aPairInASmallMachineRatioIsBuiltAsOneBand() {
        val ecProduction = production(electronicCircuitRecipe, amount = 2.0, speed = 1.0)
        val feed =
            directFeed(
                optimizationResult(
                    ecProduction,
                    production(copperCableRecipe, amount = 3.0, speed = 1.0),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            // a cable machine makes 4/s, a circuit machine takes 6/s: the 3:2 cycle is built
            {
                assertEquals(
                    mapOf("copper-cable" to 3, "electronic-circuit" to 2),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            { assertEquals(List(4) { copperCable }, band.links.map { it.material }) },
            { assertEquals(12.0, band.links.sumOf { it.flow }, TOLERANCE) },
            // the cable only feeds the band, so no belt row of it is built
            {
                assertEquals(
                    setOf(copperCableRecipe, electronicCircuitRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aRealGearAndBeltChainFormsABand() {
        val beltProduction = production(transportBeltRecipe)
        val feed =
            directFeed(
                optimizationResult(
                    beltProduction,
                    production(ironPlateSmeltingRecipe),
                    production(ironGearWheelRecipe),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    setOf(ironGearWheelRecipe, transportBeltRecipe),
                    band.machines.mapTo(mutableSetOf()) { it.recipe.recipe },
                )
            },
            { assertNotNull(band.links.firstOrNull { it.material == gear }) },
        )
    }

    @Test
    fun aRateThatNoSmallCycleFitsKeepsBothRecipesBelted() {
        val ironOreRecipe = ironOreMiningRecipe
        val ironPlateProduction = production(ironPlateSmeltingRecipe)
        val feed =
            directFeed(
                optimizationResult(
                    ironPlateProduction,
                    production(ironOreRecipe, amount = 0.05),
                ),
                logistics = logistics,
            )

        assertAll(
            { assertEquals(emptyList(), feed.bands) },
            { assertEquals(setOf(ironPlateSmeltingRecipe), feed.recipes.toSet()) },
        )
    }

    @Test
    fun aBeltedProductionDoesNotRoundUpFloatingPointNoise() {
        // the foundry and the assembling machine have different footprints, so the plate stays
        // belted and its machine count is rounded from the plan rate
        val plate = production(castingIronRecipe, Factory.FOUNDRY, amount = 3.0)
        val gearBase = production(ironGearWheelRecipe)
        val ratePerMachine = 3.0 + 5e-7
        val gearAmount =
            ratePerMachine * plate.outputPerMachine() / gearBase.inputPerMachine(ironPlate)
        val gearProduction = gearBase.copy(amount = gearAmount)
        val feed =
            directFeed(
                optimizationResult(gearProduction, plate),
                logistics = logistics,
            )

        // the rate is just above three machines, but that is floating-point noise, not a fourth
        // machine
        assertEquals(
            3,
            feed.rows
                .single { it.machines.first().recipe.recipe == castingIronRecipe }
                .machines
                .size,
        )
    }

    @Test
    fun aMaterialWithASecondConsumerStaysInTheBand() {
        val acProduction = production(advancedCircuitRecipe)
        val feed =
            directFeed(
                optimizationResult(
                    acProduction,
                    production(electronicCircuitRecipe),
                    production(copperCableRecipe),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            // the cable feeds both the circuit and the advanced circuit from one band
            {
                assertEquals(
                    setOf(copperCableRecipe, electronicCircuitRecipe, advancedCircuitRecipe),
                    band.machines.map { it.recipe.recipe }.toSet(),
                )
            },
            { assertEquals(4, band.machines.size) },
            {
                assertEquals(
                    2,
                    band.machines.count { it.recipe.name == copperCableRecipe.name },
                )
            },
            {
                assertEquals(
                    setOf(
                        copperCableRecipe,
                        electronicCircuitRecipe,
                        advancedCircuitRecipe,
                    ),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun theGrowthBudgetUsesPropagatedNeedsInsteadOfThePlanMachineCounts() {
        val processing = production(processingUnitRecipe, amount = 2.0)
        // the plan machine counts alone sum to more than a multi-link band may hold; the rates the
        // window actually has to cover are lower once plastic feeds the advanced circuits
        val advanced = production(advancedCircuitRecipe, amount = 4.0)
        val plastic = production(plasticBarRecipe, amount = 4.0)
        val feed =
            directFeed(
                optimizationResult(processing, advanced, plastic),
                logistics = calculator(),
            )
        assertEquals(
            setOf(plasticBarRecipe, advancedCircuitRecipe, processingUnitRecipe),
            feed.bands.single().machines.mapTo(mutableSetOf()) { it.recipe.recipe },
        )
    }

    @Test
    fun aCircuitChainWithSharedConsumersBuildsTheTargetOnce() {
        val targetProduction = production(processingUnitRecipe)
        val feed =
            directFeed(
                optimizationResult(
                    targetProduction,
                    production(advancedCircuitRecipe),
                    production(electronicCircuitRecipe),
                    production(copperCableRecipe),
                ),
                logistics = logistics,
            )

        assertAll(
            { assertContains(feed.recipes, processingUnitRecipe) },
            // the target row is built exactly once even though the chain splits at its shared
            // circuit and cable consumers
            { assertEquals(1, feed.recipes.count { it == processingUnitRecipe }) },
            { assertTrue(feed.rows.any { row -> row.links.any { it.material == copperCable } }) },
        )
    }

    @Test
    fun aRealGearAndBeltChainStaysOneBand() {
        val beltProduction = production(transportBeltRecipe)
        val feed =
            directFeed(
                optimizationResult(
                    beltProduction,
                    production(ironPlateSmeltingRecipe),
                    production(ironGearWheelRecipe),
                ),
                logistics = logistics,
            )

        assertAll(
            {
                assertEquals(
                    setOf(
                        ironGearWheelRecipe,
                        transportBeltRecipe,
                    ),
                    feed.bands.single().machines.mapTo(mutableSetOf()) { it.recipe.recipe },
                )
            },
            // the smelter is planned now, but its ratio does not fit the gear/belt band, so it
            // stays a row of its own
            {
                assertEquals(
                    setOf(ironPlateSmeltingRecipe, ironGearWheelRecipe, transportBeltRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun nodeOfCreatesANodeEvenWhenTheProductionIsNotIncluded() {
        // two recipes make solid fuel, so neither is part of the plan's producer index; nodeOf
        // still resolves them lazily and returns the same node for repeated lookups
        val first = production(solidFuelFromLightOilRecipe)
        val second = production(solidFuelFromPetroleumGasRecipe)
        val targetProduction = production(rocketFuelRecipe)
        val context =
            DirectFeedContext(
                optimizationResult(targetProduction, first, second),
                logistics = logistics,
            )
        val node = context.nodeOf(first)

        assertAll(
            {
                assertContainsNot(
                    context.included.keys.map { it.recipe },
                    solidFuelFromLightOilRecipe,
                )
            },
            { assertEquals(node, context.nodeOf(first)) },
            // the target node is the cached one, not a second node for the same production
            { assertEquals(context.target, context.nodeOf(context.targetProduction)) },
        )
    }

    @Test
    fun theTargetStaysIncludedWhenItsMaterialHasTwoProducers() {
        // the multi-producer solid fuel cannot be included, but the target row must still be
        // planned instead of disappearing from included
        val targetProduction = production(rocketFuelRecipe)
        val context =
            DirectFeedContext(
                optimizationResult(
                    targetProduction,
                    production(solidFuelFromLightOilRecipe),
                    production(solidFuelFromPetroleumGasRecipe),
                ),
                logistics = logistics,
            )

        assertEquals(listOf(rocketFuelRecipe), context.included.values.map { it.recipe.recipe })
    }

    @Test
    fun aProductionWithTwoConsumersIsOneNodeAndIsTraversedOnce() {
        // electronic circuits feed both advanced circuits and processing units, so the circuit
        // producer has two parents. The walk must expand its upstream cable chain and still yield
        // both outgoing links.
        val optimizationResult =
            optimizationResult(
                production(processingUnitRecipe),
                production(advancedCircuitRecipe),
                production(electronicCircuitRecipe),
                production(copperCableRecipe),
            )

        fun findProduction(recipe: Recipe) =
            optimizationResult.factories.values.first { it.recipe.recipe == recipe }

        val context =
            DirectFeedContext(
                optimizationResult,
                logistics = logistics,
            )
        val cableNode = context.nodeOf(findProduction(copperCableRecipe))
        val circuitNode = context.nodeOf(findProduction(electronicCircuitRecipe))
        val links = context.nodeOf(findProduction(processingUnitRecipe)).traverse().toList()

        assertAll(
            {
                assertEquals(
                    setOf(advancedCircuit, processingUnit),
                    circuitNode.consumers.map { it.consumer.production.material }.toSet(),
                )
            },
            // the shared cable producer expands once per consumer link it has (circuit and
            // advanced circuit)
            { assertEquals(2, links.count { it.producer == cableNode }) },
            // but each consumer link is still yielded
            { assertEquals(2, links.count { it.producer == circuitNode }) },
        )
    }

    @Test
    fun linkNamesAndTheCanonicalKeyIgnoreWalkOrder() {
        // the cached arrangement key must canonicalize links by name, never by score or by the
        // order the walk discovered them in: a pair of nodes may be reached from either end.
        val productProduction = production(processingUnitRecipe)
        val context =
            DirectFeedContext(
                optimizationResult(
                    productProduction,
                    production(advancedCircuitRecipe),
                    production(electronicCircuitRecipe),
                    production(copperCableRecipe),
                ),
                logistics = logistics,
            )
        val links = context.candidateLinks()

        assertAll(
            { assertTrue(links.size > 1) },
            {
                assertTrue(
                    links.all {
                        it.name ==
                            "${it.material.name}#${it.producer.recipe.name}#${it.consumer.recipe.name}"
                    },
                )
            },
            // seeded and grown groups look their key up in either direction
            { assertEquals(linkKey(links), linkKey(links.reversed())) },
            {
                assertEquals(
                    linkKey(links).map { it.name }.sorted(),
                    linkKey(links).map { it.name },
                )
            },
            // best first, as the walk's seed and growth order expect
            {
                assertEquals(
                    links.map { it.candidateScore }.sortedDescending(),
                    links.map { it.candidateScore },
                )
            },
        )
    }
}
