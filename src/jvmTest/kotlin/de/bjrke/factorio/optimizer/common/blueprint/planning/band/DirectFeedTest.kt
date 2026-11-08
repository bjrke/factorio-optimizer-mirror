package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.battery
import de.bjrke.factorio.optimizer.common.fixtures.batteryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnit
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.fishBreedingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.flyingRobotFrame
import de.bjrke.factorio.optimizer.common.fixtures.flyingRobotFrameRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.kovarexEnrichmentProcessRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.nutrients
import de.bjrke.factorio.optimizer.common.fixtures.nutrientsFromFishRecipe
import de.bjrke.factorio.optimizer.common.fixtures.rawFish
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcidRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.uranium235
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

private val alpha = Material("alpha")
private val beta = Material("beta")
private val gamma = Material("gamma")
private val delta = Material("delta")

/** Every recipe in row order; a recipe may appear once per row. */
private val DirectFeed.recipes
    get() = rows.flatMap { it.productions }.map { it.recipe }

/** The direct-feed bands among the rows. */
private val DirectFeed.bands
    get() = rows.filter { it.links.isNotEmpty() }

/** The material a recipe produces, to keep the recipe names and the rates apart. */
private val product = Material("product")

private fun recipe(
    name: String,
    result: Map<Material, Double>,
    ingredients: Map<Material, Double> = emptyMap(),
    time: Double = 1.0,
) = Recipe(name, ingredients, result, time, setOf(Category.CRAFTING))

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
    fun aCycleIsOnlyAcceptedWithinTheRateTolerance() =
        assertTable(
            listOf<Pair<Double, Pair<Int, Int>?>>(
                // a rate below the pattern is fine: the consumer takes what it needs. A rate too
                // far above it is not: the machines would pile up material the consumer cannot
                // take.
                0.8 to (1 to 1),
                1.0 to (1 to 1),
                1.05 to (1 to 1),
                1.1 to null,
                1.5 to (3 to 2),
                2.0 to (2 to 1),
                0.5 to (1 to 2),
                20.0 to null,
                0.05 to null,
                0.66 to (2 to 3),
            ),
        ) { (ratio, expected) ->
            assertEquals(expected, cycleRatio(ratio))
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
        val ecProduction = production(electronicCircuitRecipe, electronicCircuit)
        val feed =
            directFeed(
                ecProduction,
                factories(
                    ecProduction,
                    production(copperCableRecipe, copperCable),
                ),
                scale = 1.0,
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
    fun aWindowIsCutSoThatNoConsumerStarves() {
        // a deliberately small 1:2:1 pattern; the real two-input recipes do not fit `MAX_CYCLE`
        val alphaRodRecipe = recipe("alpha-rod", mapOf(alpha to 1.0))
        val gammaPlateRecipe = recipe("gamma-plate", mapOf(gamma to 2.0))
        val betaUnitRecipe =
            recipe("beta-unit", mapOf(product to 1.0), mapOf(alpha to 1.0, gamma to 1.0))
        val betaUnitProduction = production(betaUnitRecipe, product)
        val feed =
            directFeed(
                betaUnitProduction,
                factories(
                    betaUnitProduction,
                    production(alphaRodRecipe, alpha),
                    production(gammaPlateRecipe, gamma),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            // each unit takes a rod and a plate a second: the 1:2:1 pattern A:B:C:B cut off after
            // two units leaves the second unit without a plate, so the palindrome is built instead
            {
                assertEquals(
                    listOf("alpha-rod", "beta-unit", "gamma-plate", "beta-unit", "alpha-rod"),
                    band.machines.map { it.recipe.name },
                )
            },
            { assertEquals(listOf(alpha, gamma, gamma, alpha), band.links.map { it.material }) },
        )
    }

    @Test
    fun aRateThatNoSmallCycleFitsKeepsBothRecipesBelted() {
        // a deliberately extreme ore rate: no small machine cycle can stand in for 20:1
        val ironOreRecipe = recipe("iron-ore", mapOf(ironOre to 20.0))
        val ironPlateProduction = production(ironPlateRecipe, ironPlate)
        val feed =
            directFeed(
                ironPlateProduction,
                factories(
                    ironPlateProduction,
                    production(ironOreRecipe, ironOre, amount = 0.05),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        assertAll(
            { assertEquals(emptyList(), feed.bands) },
            { assertEquals(setOf(ironOreRecipe, ironPlateRecipe), feed.recipes.toSet()) },
        )
    }

    @Test
    fun aMaterialWithASecondConsumerGetsOneMoreProduction() {
        val acProduction = production(advancedCircuitRecipe, advancedCircuit)
        val feed =
            directFeed(
                acProduction,
                factories(
                    acProduction,
                    production(electronicCircuitRecipe, electronicCircuit),
                    production(copperCableRecipe, copperCable),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            // the circuit takes its cable straight from the band: three cable and two circuits
            {
                assertEquals(
                    setOf(copperCableRecipe, electronicCircuitRecipe),
                    band.machines.map { it.recipe }.toSet(),
                )
            },
            { assertEquals(5, band.machines.size) },
            // the advanced circuit also takes cable over a belt, so one more cable row is built
            { assertEquals(2, feed.recipes.count { it == copperCableRecipe }) },
            {
                assertEquals(
                    setOf(copperCableRecipe, electronicCircuitRecipe, advancedCircuitRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aBeltedProductionCoversThePlansRateNotAWholeConsumerMachine() {
        // the foundry-like consumer takes 20 alpha/s when it runs full, but the plan only ever
        // draws a twentieth of that rate; sizing the belt row by the whole machine would build nine
        // gear machines where a single one is enough
        val gearRecipe = recipe("gear", mapOf(alpha to 1.0), mapOf(gamma to 2.0))
        val inserterRecipe = recipe("inserter", mapOf(beta to 1.0), mapOf(alpha to 1.0))
        val foundryRecipe = recipe("foundry", mapOf(delta to 2.0), mapOf(alpha to 20.0))
        val targetRecipe =
            recipe("target", mapOf(product to 1.0), mapOf(beta to 0.05, delta to 0.05))
        val targetProduction = production(targetRecipe, product)
        val feed =
            directFeed(
                targetProduction,
                factories(
                    targetProduction,
                    production(inserterRecipe, beta, amount = 0.5),
                    production(gearRecipe, alpha, amount = 0.5),
                    production(foundryRecipe, delta, amount = 0.05),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        val machines = feed.rows.flatMap { it.machines }.groupingBy { it.recipe.name }.eachCount()
        assertAll(
            // the gear and the inserter form a 1:1 band, so the gear machine the band builds
            // supplies only that band; the foundry takes one more gear machine over a belt
            { assertEquals(2, feed.bands.single().machines.size) },
            {
                assertEquals(
                    mapOf("gear" to 2, "inserter" to 1, "target" to 1, "foundry" to 1),
                    machines,
                )
            },
            // the band's own inserter output already covers the target, so no second inserter row
            { assertEquals(1, feed.recipes.count { it.name == "inserter" }) },
        )
    }

    @Test
    fun aBandStopsAtTheMachineCapAndTheRestStaysBelted() {
        val alphaRodRecipe = recipe("alpha-rod", mapOf(alpha to 1.0))
        val gammaPlateRecipe = recipe("gamma-plate", mapOf(gamma to 2.0))
        val betaUnitRecipe =
            recipe("beta-unit", mapOf(product to 1.0), mapOf(alpha to 1.0, gamma to 1.0))
        val betaUnitProduction = production(betaUnitRecipe, product, amount = 9.0)
        val feed =
            directFeed(
                betaUnitProduction,
                factories(
                    betaUnitProduction,
                    production(alphaRodRecipe, alpha, amount = 9.0),
                    production(gammaPlateRecipe, gamma, amount = 5.0),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        assertAll(
            // the rod and the unit fill a 1:1 band of the 18 machines a band may hold
            { assertEquals(GROUP_MAX_MACHINES, feed.bands.single().machines.size) },
            {
                assertEquals(listOf(alphaRodRecipe, betaUnitRecipe, gammaPlateRecipe), feed.recipes)
            },
            // the plate does not fit into that band any more, so it is built as a belt row of its
            // own
            { assertEquals(5, feed.rows.last().machines.size) },
        )
    }

    @Test
    fun aRecipeThatTakesAFluidStillJoinsABand() {
        val ecProduction = production(electricEngineUnitRecipe, electricEngineUnit)
        val feed =
            directFeed(
                ecProduction,
                factories(
                    ecProduction,
                    production(engineUnitRecipe, engineUnit),
                ),
                scale = 1.0,
                logistics = fluids(lubricant),
            )

        // the linked material is the solid engine unit, so the band is built even though the
        // consumer also takes lubricant; the fluid lane serves its port on the row's outside edge
        assertEquals(
            setOf(engineUnitRecipe, electricEngineUnitRecipe),
            feed.bands.single().machines.map(FactoryProduction::recipe).toSet(),
        )
    }

    @Test
    fun aProducerThatTakesAFluidStillFeedsItsBand() {
        val flyingRobotFrameProduction = production(flyingRobotFrameRecipe, flyingRobotFrame)
        val feed =
            directFeed(
                flyingRobotFrameProduction,
                factories(
                    flyingRobotFrameProduction,
                    production(electricEngineUnitRecipe, electricEngineUnit),
                ),
                scale = 1.0,
                logistics = fluids(lubricant),
            )

        val band = feed.bands.single()
        assertAll(
            // the electric engine machine takes lubricant and still inserts its product
            {
                assertEquals(
                    mapOf("electric-engine-unit" to 1, "flying-robot-frame" to 2),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            {
                assertEquals(
                    listOf(electricEngineUnit, electricEngineUnit),
                    band.links.map { it.material },
                )
            },
            {
                assertEquals(
                    setOf(electricEngineUnitRecipe, flyingRobotFrameRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aChainOfThreeRecipesIsBuiltAsOneBand() {
        // no real three-recipe chain has one-to-one links all the way; this pins that the graph
        // walk expands ingredients recursively
        val firstRecipe = recipe("first", mapOf(alpha to 1.0))
        val secondRecipe = recipe("second", mapOf(beta to 1.0), mapOf(alpha to 1.0))
        val thirdRecipe = recipe("third", mapOf(product to 1.0), mapOf(beta to 1.0))
        val thirdProduction = production(thirdRecipe, product)
        val feed =
            directFeed(
                thirdProduction,
                factories(
                    thirdProduction,
                    production(secondRecipe, beta),
                    production(firstRecipe, alpha),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    mapOf("first" to 1, "second" to 1, "third" to 1),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            { assertEquals(setOf(alpha, beta), band.links.map { it.material }.toSet()) },
            { assertEquals(3, band.machines.size) },
        )
    }

    @Test
    fun aChainOfFourRecipesIsBuiltAsOneBand() {
        // the third link joins the chain now that the band recipe cap is gone. No real chain has
        // small ratios all the way: the only real four-recipe chain, ore -> plate -> battery ->
        // frame, needs a 14-machine cycle beyond MAX_CYCLE, so this stays synthetic like the
        // three-recipe chain above
        val firstRecipe = recipe("first", mapOf(alpha to 1.0))
        val secondRecipe = recipe("second", mapOf(beta to 1.0), mapOf(alpha to 1.0))
        val thirdRecipe = recipe("third", mapOf(product to 1.0), mapOf(beta to 1.0))
        val fourthRecipe = recipe("fourth", mapOf(delta to 1.0), mapOf(product to 1.0))
        val fourthProduction = production(fourthRecipe, delta)
        val feed =
            directFeed(
                fourthProduction,
                factories(
                    fourthProduction,
                    production(thirdRecipe, product),
                    production(secondRecipe, beta),
                    production(firstRecipe, alpha),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertEquals(
                    mapOf("first" to 1, "second" to 1, "third" to 1, "fourth" to 1),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            { assertEquals(listOf(alpha, beta, product), band.links.map { it.material }) },
            // nothing of the chain is left over: the four recipes fill the one row
            { assertEquals(1, feed.rows.size) },
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
                scale = 1.0,
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
                scale = 1.0,
                logistics = fluids(sulfuricAcid, water),
            )

        // a fluid link would need a pipe, so the acid stays a fluid source and never a link
        assertEquals(emptyList(), feed.bands)
    }

    @Test
    fun aRecipeThatFeedsItselfStaysBelted() {
        // kovarex consumes and produces both uranium isotopes: a link would enter the same recipe
        // again, so the recipe must stay belted
        val kovarexProduction = production(kovarexEnrichmentProcessRecipe, uranium235)
        val feed =
            directFeed(
                kovarexProduction,
                factories(kovarexProduction),
                scale = 1.0,
                logistics = logistics,
            )

        assertEquals(emptyList(), feed.bands)
    }

    @Test
    fun aTwoRecipeCycleStaysBelted() {
        // fish-breeding consumes nutrients and nutrients-from-fish consumes fish: a real
        // two-recipe solid cycle. Both recipes are organic, so they are not buildable and no feed
        // link can enter the cycle.
        val fishProduction = production(fishBreedingRecipe, rawFish)
        val feed =
            directFeed(
                fishProduction,
                factories(
                    fishProduction,
                    production(nutrientsFromFishRecipe, nutrients),
                ),
                scale = 1.0,
                logistics = fluids(water),
            )

        assertEquals(emptyList(), feed.bands)
    }

    @Test
    fun nodeOfCreatesANodeEvenWhenTheProductionIsNotIncluded() {
        // two recipes make alpha, so neither is part of the plan's producer index; nodeOf still
        // resolves them lazily and returns the same node for repeated lookups
        val firstRecipe = recipe("first-alpha", mapOf(alpha to 1.0))
        val secondRecipe = recipe("second-alpha", mapOf(alpha to 1.0))
        val first = production(firstRecipe, alpha)
        val second = production(secondRecipe, alpha)
        val targetRecipe = recipe("target", mapOf(product to 1.0), mapOf(alpha to 1.0))
        val targetProduction = production(targetRecipe, product)
        val context =
            DirectFeedContext(
                targetProduction,
                factories(targetProduction, first, second),
                1.0,
                logistics,
            )
        val node = context.nodeOf(first)

        assertAll(
            { assertContainsNot(context.included, firstRecipe) },
            { assertEquals(node, context.nodeOf(first)) },
            // the target node is the cached one, not a second node for the same production
            { assertEquals(context.target, context.nodeOf(context.targetProduction)) },
        )
    }

    @Test
    fun theTargetStaysIncludedWhenItsMaterialHasTwoProducers() {
        // the multi-producer alpha cannot be included, but the target row must still be planned
        // instead of disappearing from included
        val firstRecipe = recipe("first-alpha", mapOf(alpha to 1.0))
        val secondRecipe = recipe("second-alpha", mapOf(alpha to 1.0))
        val targetRecipe = recipe("target", mapOf(product to 1.0), mapOf(alpha to 1.0))
        val targetProduction = production(targetRecipe, product)
        val context =
            DirectFeedContext(
                targetProduction,
                factories(
                    targetProduction,
                    production(firstRecipe, alpha),
                    production(secondRecipe, alpha),
                ),
                1.0,
                logistics,
            )

        assertEquals(mapOf(targetRecipe to targetProduction), context.included)
    }

    @Test
    fun aProductionWithTwoConsumersIsOneNodeAndIsTraversedOnce() {
        // beta feeds both gamma and the target, so beta has two parents. The walk must expand its
        // upstream chain once and still yield both outgoing links.
        val alphaRodRecipe = recipe("alpha-rod", mapOf(alpha to 1.0))
        val betaUnitRecipe = recipe("beta-unit", mapOf(beta to 1.0), mapOf(alpha to 1.0))
        val gammaPackRecipe = recipe("gamma-pack", mapOf(gamma to 1.0), mapOf(beta to 1.0))
        val productRecipe =
            recipe("product", mapOf(product to 1.0), mapOf(beta to 1.0, gamma to 1.0))
        val productProduction = production(productRecipe, product)
        val context =
            DirectFeedContext(
                productProduction,
                factories(
                    productProduction,
                    production(gammaPackRecipe, gamma),
                    production(betaUnitRecipe, beta),
                    production(alphaRodRecipe, alpha),
                ),
                1.0,
                logistics,
            )
        val alphaNode = context.nodeOf(production(alphaRodRecipe, alpha))
        val betaNode = context.nodeOf(production(betaUnitRecipe, beta))
        val links = context.nodeOf(productProduction).traverse().toList()

        assertAll(
            // both parents resolve to the same node, not to two path-local copies
            { assertEquals(betaNode, context.nodeOf(production(betaUnitRecipe, beta))) },
            {
                assertEquals(
                    setOf(gamma, product),
                    betaNode.consumers.map { it.consumer.production.material }.toSet(),
                )
            },
            // the shared producer's upstream chain is expanded only once
            { assertEquals(1, links.count { it.producer == alphaNode }) },
            // but each consumer link is still yielded
            { assertEquals(2, links.count { it.producer == betaNode }) },
        )
    }
}
