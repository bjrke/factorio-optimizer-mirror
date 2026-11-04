package de.bjrke.factorio.optimizer.common.band

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
import de.bjrke.factorio.optimizer.common.fixtures.flyingRobotFrame
import de.bjrke.factorio.optimizer.common.fixtures.flyingRobotFrameRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.kovarexEnrichmentProcessRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcidRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.uranium235
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun aPairInASmallMachineRatioIsBuiltAsOneBand() {
        val feed =
            directFeed(
                electronicCircuitRecipe,
                listOf(
                    production(electronicCircuitRecipe, electronicCircuit),
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
        val feed =
            directFeed(
                betaUnitRecipe,
                listOf(
                    production(betaUnitRecipe, product),
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
        val feed =
            directFeed(
                ironPlateRecipe,
                listOf(
                    production(ironPlateRecipe, ironPlate),
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
    fun aMaterialWithTwoProducersIsNotDirectFed() {
        // two recipes make the same plate: direct feeding would have to pick one, so it stays
        // belted
        val firstSmelterRecipe =
            recipe("first-smelter", mapOf(ironPlate to 1.0), mapOf(ironOre to 1.0))
        val secondSmelterRecipe =
            recipe("second-smelter", mapOf(ironPlate to 1.0), mapOf(ironOre to 1.0))
        val feed =
            directFeed(
                ironGearWheelRecipe,
                listOf(
                    production(ironGearWheelRecipe, gear),
                    production(firstSmelterRecipe, ironPlate),
                    production(secondSmelterRecipe, ironPlate),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        assertEquals(emptyList(), feed.bands)
    }

    @Test
    fun aMaterialWithASecondConsumerGetsOneMoreProduction() {
        val feed =
            directFeed(
                advancedCircuitRecipe,
                listOf(
                    production(advancedCircuitRecipe, advancedCircuit),
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
        val feed =
            directFeed(
                targetRecipe,
                listOf(
                    production(targetRecipe, product),
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
        val feed =
            directFeed(
                betaUnitRecipe,
                listOf(
                    production(betaUnitRecipe, product, amount = 9.0),
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
        val feed =
            directFeed(
                electricEngineUnitRecipe,
                listOf(
                    production(electricEngineUnitRecipe, electricEngineUnit),
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
        val feed =
            directFeed(
                flyingRobotFrameRecipe,
                listOf(
                    production(flyingRobotFrameRecipe, flyingRobotFrame),
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
        val feed =
            directFeed(
                thirdRecipe,
                listOf(
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
                    mapOf("first" to 1, "second" to 1, "third" to 1),
                    band.machines.groupingBy { it.recipe.name }.eachCount(),
                )
            },
            { assertEquals(setOf(alpha, beta), band.links.map { it.material }.toSet()) },
            { assertEquals(3, band.machines.size) },
        )
    }

    @Test
    fun aBandMayOutputAFluidAsItsFinalOutput() {
        val feed =
            directFeed(
                sulfuricAcidRecipe,
                listOf(
                    production(sulfuricAcidRecipe, sulfuricAcid),
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
        val feed =
            directFeed(
                batteryRecipe,
                listOf(
                    production(batteryRecipe, battery),
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
        val feed =
            directFeed(
                kovarexEnrichmentProcessRecipe,
                listOf(production(kovarexEnrichmentProcessRecipe, uranium235)),
                scale = 1.0,
                logistics = logistics,
            )

        assertEquals(emptyList(), feed.bands)
    }

    @Test
    fun aTwoRecipeCycleStaysBelted() {
        // no simple vanilla two-recipe solid cycle exists; the rate fits, so only the cycle guard
        // can keep this pair belted
        val toBetaRecipe = recipe("to-beta", mapOf(beta to 1.0), mapOf(alpha to 1.0))
        val toAlphaRecipe = recipe("to-alpha", mapOf(alpha to 1.0), mapOf(beta to 1.0))
        val feed =
            directFeed(
                toAlphaRecipe,
                listOf(production(toAlphaRecipe, alpha), production(toBetaRecipe, beta)),
                scale = 1.0,
                logistics = logistics,
            )

        assertEquals(emptyList(), feed.bands)
    }

    @Test
    fun aCyclicIngredientIsPrunedAndMarksTheLink() {
        // to-beta needs alpha, and alpha is the material of its parent link: the walk must not
        // enter the cycle again, and the link must be dropped from the band candidates
        val toBetaRecipe = recipe("to-beta", mapOf(beta to 1.0), mapOf(alpha to 1.0))
        val toAlphaRecipe = recipe("to-alpha", mapOf(alpha to 1.0), mapOf(beta to 1.0))
        val alphaProduction = production(toAlphaRecipe, alpha)
        val betaProduction = production(toBetaRecipe, beta)
        val context =
            DirectFeedContext(
                alphaProduction,
                listOf(alphaProduction, betaProduction),
                1.0,
                logistics,
            )
        val root = FeedLink(context)
        val betaLink = FeedLink(context, betaProduction, root)

        assertAll(
            // the ingredient is pruned instead of entering the cycle again
            { assertEquals(emptyList(), betaLink.ingredients.toList()) },
            // the link is marked so it cannot be banded with an input it can never receive
            { assertTrue(betaLink.hasCyclicIngredient) },
        )
    }
}
