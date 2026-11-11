package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.battery
import de.bjrke.factorio.optimizer.common.fixtures.batteryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.castingIronRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnit
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.rocketFuel
import de.bjrke.factorio.optimizer.common.fixtures.rocketFuelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromLightOilRecipe
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.fixtures.splitter
import de.bjrke.factorio.optimizer.common.fixtures.splitterRecipe
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcidRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
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
    get() = rows.flatMap { it.productions }.map { it.recipe }

/** The direct-feed bands among the rows. */
private val DirectFeed.bands
    get() = rows.filter { it.links.isNotEmpty() }

private fun production(
    recipe: Recipe,
    material: Material,
    amount: Double = 1.0,
    speed: Double = 1.0,
    productivity: Double = 0.0,
) =
    FactoryProduction(
        material,
        recipe,
        Factory.ASSEMBLING_MACHINE_3,
        amount,
        0,
        speed,
        productivity,
    )

private fun fluids(vararg materials: Material) =
    LogisticsCalculator(testLogistics.copy(fluids = materials.toSet()))

class DirectFeedTest {

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
        val machine = production(ironPlateRecipe, ironPlate, speed = 2.0, productivity = 0.5)

        assertAll(
            { assertEquals(2.0, machine.inputPerMachine(ironOre), TOLERANCE) },
            // the productivity bonus is what a direct-fed consumer really receives
            { assertEquals(3.0, machine.outputPerMachine(), TOLERANCE) },
        )
    }

    private fun factories(vararg productions: FactoryProduction) = productions.associateBy {
        it.recipe
    }

    @Test
    fun aPairInASmallMachineRatioIsBuiltAsOneBand() {
        val ecProduction = production(electronicCircuitRecipe, electronicCircuit, amount = 2.0)
        val feed =
            directFeed(
                ecProduction,
                factories(
                    ecProduction,
                    production(copperCableRecipe, copperCable, amount = 3.0),
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
        val beltProduction = production(transportBeltRecipe, transportBelt)
        val feed =
            directFeed(
                beltProduction,
                factories(
                    beltProduction,
                    production(ironPlateSmeltingRecipe, ironPlate),
                    production(ironGearWheelRecipe, gear),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    setOf(ironGearWheelRecipe, transportBeltRecipe),
                    band.machines.mapTo(mutableSetOf()) { it.recipe },
                )
            },
            { assertNotNull(band.links.firstOrNull { it.material == gear }) },
        )
    }

    @Test
    fun aRateThatNoSmallCycleFitsKeepsBothRecipesBelted() {
        val ironOreRecipe = ironOreMiningRecipe
        val ironPlateProduction = production(ironPlateSmeltingRecipe, ironPlate)
        val feed =
            directFeed(
                ironPlateProduction,
                factories(
                    ironPlateProduction,
                    production(ironOreRecipe, ironOre, amount = 0.05),
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
        val plate =
            FactoryProduction(
                ironPlate,
                castingIronRecipe,
                Factory.FOUNDRY,
                amount = 3.0,
                level = 0,
            )
        val gearBase = production(ironGearWheelRecipe, gear)
        val ratePerMachine = 3.0 + 5e-7
        val gearAmount =
            ratePerMachine * plate.outputPerMachine() / gearBase.inputPerMachine(ironPlate)
        val gearProduction = gearBase.copy(amount = gearAmount)
        val feed =
            directFeed(
                gearProduction,
                factories(gearProduction, plate),
                logistics = logistics,
            )

        // the rate is just above three machines, but that is floating-point noise, not a fourth
        // machine
        assertEquals(
            3,
            feed.rows.single { it.machines.first().recipe == castingIronRecipe }.machines.size,
        )
    }

    @Test
    fun aMaterialWithASecondConsumerStaysInTheBand() {
        val acProduction = production(advancedCircuitRecipe, advancedCircuit)
        val feed =
            directFeed(
                acProduction,
                factories(
                    acProduction,
                    production(electronicCircuitRecipe, electronicCircuit),
                    production(copperCableRecipe, copperCable),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            // the cable feeds both the circuit and the advanced circuit from one band
            {
                assertEquals(
                    setOf(copperCableRecipe, electronicCircuitRecipe, advancedCircuitRecipe),
                    band.machines.map { it.recipe }.toSet(),
                )
            },
            { assertEquals(4, band.machines.size) },
            { assertEquals(2, band.machines.count { it.recipe == copperCableRecipe }) },
            {
                assertEquals(
                    setOf(copperCableRecipe, electronicCircuitRecipe, advancedCircuitRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aCircuitChainGroupsTheSharedProducerAndTheTarget() {
        val targetProduction = production(processingUnitRecipe, processingUnit)
        val feed =
            directFeed(
                targetProduction,
                factories(
                    targetProduction,
                    production(advancedCircuitRecipe, advancedCircuit),
                    production(electronicCircuitRecipe, electronicCircuit),
                    production(copperCableRecipe, copperCable),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    setOf(
                        copperCableRecipe,
                        electronicCircuitRecipe,
                        advancedCircuitRecipe,
                        processingUnitRecipe,
                    ),
                    band.machines.map { it.recipe }.toSet(),
                )
            },
            // the target's four solid inputs plus its product fit four rows with one mixed belt
            { assertContains(feed.recipes, processingUnitRecipe) },
            // the target row is built exactly once
            { assertEquals(1, feed.recipes.count { it == processingUnitRecipe }) },
        )
    }

    @Test
    fun aRealGearAndBeltChainStaysOneBand() {
        val beltProduction = production(transportBeltRecipe, transportBelt)
        val feed =
            directFeed(
                beltProduction,
                factories(
                    beltProduction,
                    production(ironPlateSmeltingRecipe, ironPlate),
                    production(ironGearWheelRecipe, gear),
                ),
                logistics = logistics,
            )

        assertAll(
            {
                assertEquals(
                    setOf(ironGearWheelRecipe, transportBeltRecipe),
                    feed.bands.single().machines.mapTo(mutableSetOf()) { it.recipe },
                )
            },
            // the smelter does not fit into that band, so the band is just gear and belt
            {
                assertEquals(
                    setOf(ironGearWheelRecipe, transportBeltRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aRecipeThatTakesAFluidStillJoinsABand() {
        val target = production(processingUnitRecipe, processingUnit)
        val feed =
            directFeed(
                target,
                factories(
                    target,
                    production(advancedCircuitRecipe, advancedCircuit),
                ),
                logistics = fluids(sulfuricAcid),
            )

        // the linked material is the solid advanced circuit, so the band is built even though the
        // consumer also takes sulfuric acid; the fluid lane serves its port on the row's outside
        // edge
        assertEquals(
            setOf(advancedCircuitRecipe, processingUnitRecipe),
            feed.bands.single().machines.map(FactoryProduction::recipe).toSet(),
        )
    }

    @Test
    fun aProducerThatTakesAFluidStillFeedsItsBand() {
        val advanced = production(advancedCircuitRecipe, advancedCircuit)
        val feed =
            directFeed(
                advanced,
                factories(
                    advanced,
                    production(plasticBarRecipe, plasticBar),
                ),
                logistics = fluids(petroleum),
            )

        val band = feed.bands.single()
        assertAll(
            // the plastic machine takes petroleum and still inserts its product; the fractional
            // circuit need makes this a one-machine-each band
            {
                assertEquals(
                    mapOf("plastic-bar" to 1, "advanced-circuit" to 1),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            { assertEquals(listOf(plasticBar), band.links.map { it.material }) },
            {
                assertEquals(
                    setOf(plasticBarRecipe, advancedCircuitRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aBandWithFiveBeltMaterialsUsesAMixedBelt() {
        val electric = production(electricEngineUnitRecipe, electricEngineUnit)
        val feed =
            directFeed(
                electric,
                factories(
                    electric,
                    production(engineUnitRecipe, engineUnit),
                ),
                logistics = fluids(lubricant),
            )

        val band = feed.bands.single()
        // steel, gear, pipe, circuit and the product are five materials; one input shares a belt
        // and the band still fits the row's four belt rows
        assertEquals(
            setOf(electricEngineUnitRecipe, engineUnitRecipe),
            band.machines.map { it.recipe }.toSet(),
        )
    }

    @Test
    fun aRealGearAndBeltChainFeedsTheBeltRecipe() {
        val thirdProduction = production(transportBeltRecipe, transportBelt)
        val feed =
            directFeed(
                thirdProduction,
                factories(
                    thirdProduction,
                    production(ironGearWheelRecipe, gear),
                    production(ironPlateSmeltingRecipe, ironPlate),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    setOf(ironGearWheelRecipe, transportBeltRecipe),
                    band.machines.mapTo(mutableSetOf()) { it.recipe },
                )
            },
            { assertNotNull(band.links.firstOrNull { it.material == gear }) },
        )
    }

    @Test
    fun aRealBeltAndSplitterChainFormsABand() {
        val fourthProduction = production(splitterRecipe, splitter)
        val feed =
            directFeed(
                fourthProduction,
                factories(
                    fourthProduction,
                    production(transportBeltRecipe, transportBelt),
                    production(ironGearWheelRecipe, gear),
                    production(ironPlateSmeltingRecipe, ironPlate),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            { assertNotNull(band.machines.firstOrNull { it.recipe == transportBeltRecipe }) },
            { assertNotNull(band.machines.firstOrNull { it.recipe == splitterRecipe }) },
            { assertNotNull(band.links.firstOrNull { it.material == transportBelt }) },
            { assertContains(feed.recipes, ironGearWheelRecipe) },
            { assertTrue(feed.rows.isNotEmpty()) },
        )
    }

    @Test
    fun aBandMayOutputAFluidAsItsFinalOutput() {
        val sulfuricAcidProduction = production(sulfuricAcidRecipe, sulfuricAcid)
        val feed =
            directFeed(
                sulfuricAcidProduction,
                factories(
                    sulfuricAcidProduction,
                    production(ironPlateRecipe, ironPlate),
                ),
                logistics = fluids(water, sulfuricAcid),
            )

        val band = feed.bands.single()
        assertAll(
            // iron plate is the solid link; the sulfuric acid leaves the band as a fluid output
            {
                assertEquals(
                    mapOf("iron-plate" to 1, "sulfuric-acid" to 1),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            { assertEquals(listOf(ironPlate), band.links.map { it.material }) },
            { assertEquals(setOf(ironPlateRecipe, sulfuricAcidRecipe), feed.recipes.toSet()) },
        )
    }

    @Test
    fun aFluidMaterialIsNeverDirectFed() {
        val batteryProduction = production(batteryRecipe, battery)
        val feed =
            directFeed(
                batteryProduction,
                factories(
                    batteryProduction,
                    production(sulfuricAcidRecipe, sulfuricAcid),
                ),
                logistics = fluids(sulfuricAcid, water),
            )

        // a fluid link would need a pipe, so the acid stays a fluid source and never a link
        assertEquals(emptyList(), feed.bands)
    }

    @Test
    fun nodeOfCreatesANodeEvenWhenTheProductionIsNotIncluded() {
        // two recipes make solid fuel, so neither is part of the plan's producer index; nodeOf
        // still resolves them lazily and returns the same node for repeated lookups
        val first = production(solidFuelFromLightOilRecipe, solidFuel)
        val second = production(solidFuelFromPetroleumGasRecipe, solidFuel)
        val targetProduction = production(rocketFuelRecipe, rocketFuel)
        val context =
            DirectFeedContext(
                targetProduction,
                factories(targetProduction, first, second),
                logistics,
            )
        val node = context.nodeOf(first)

        assertAll(
            { assertContainsNot(context.included, solidFuelFromLightOilRecipe) },
            { assertEquals(node, context.nodeOf(first)) },
            // the target node is the cached one, not a second node for the same production
            { assertEquals(context.target, context.nodeOf(context.targetProduction)) },
        )
    }

    @Test
    fun theTargetStaysIncludedWhenItsMaterialHasTwoProducers() {
        // the multi-producer solid fuel cannot be included, but the target row must still be
        // planned instead of disappearing from included
        val targetProduction = production(rocketFuelRecipe, rocketFuel)
        val context =
            DirectFeedContext(
                targetProduction,
                factories(
                    targetProduction,
                    production(solidFuelFromLightOilRecipe, solidFuel),
                    production(solidFuelFromPetroleumGasRecipe, solidFuel),
                ),
                logistics,
            )

        assertEquals(mapOf(rocketFuelRecipe to targetProduction), context.included)
    }

    @Test
    fun aProductionWithTwoConsumersIsOneNodeAndIsTraversedOnce() {
        // electronic circuits feed both advanced circuits and processing units, so the circuit
        // producer has two parents. The walk must expand its upstream cable chain and still yield
        // both outgoing links.
        val productProduction = production(processingUnitRecipe, processingUnit)
        val context =
            DirectFeedContext(
                productProduction,
                factories(
                    productProduction,
                    production(advancedCircuitRecipe, advancedCircuit),
                    production(electronicCircuitRecipe, electronicCircuit),
                    production(copperCableRecipe, copperCable),
                ),
                logistics,
            )
        val cableNode = context.nodeOf(production(copperCableRecipe, copperCable))
        val circuitNode = context.nodeOf(production(electronicCircuitRecipe, electronicCircuit))
        val links = context.nodeOf(productProduction).traverse().toList()

        assertAll(
            // both parents resolve to the same node, not to two path-local copies
            {
                assertEquals(
                    circuitNode,
                    context.nodeOf(production(electronicCircuitRecipe, electronicCircuit)),
                )
            },
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
        val productProduction = production(processingUnitRecipe, processingUnit)
        val context =
            DirectFeedContext(
                productProduction,
                factories(
                    productProduction,
                    production(advancedCircuitRecipe, advancedCircuit),
                    production(electronicCircuitRecipe, electronicCircuit),
                    production(copperCableRecipe, copperCable),
                ),
                logistics,
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
