package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

private val alpha = Material("alpha")
private val beta = Material("beta")
private val gamma = Material("gamma")
private val delta = Material("delta")
private val acid = Material("sulfuric-acid")

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

/** The recipes of a band in machine order; its machines index into [DirectFeed.recipes]. */
private fun DirectFeed.sequence(band: PlannedBand) = band.machines.map { recipes[it].recipe.name }

class DirectFeedTest {

    private val logistics =
        LogisticsCalculator(
            testLogistics,
            BeltTier.EXPRESS_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )

    @Test
    fun aCycleIsOnlyAcceptedWithinTheRateTolerance() =
        assertTable(
            listOf<Pair<Double, Pair<Int, Int>?>>(
                // a rate below the pattern is fine: the consumer takes what it needs. A rate too
                // far
                // above it is not: the machines would pile up material the consumer cannot take.
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
        val smelt = recipe("smelt", mapOf(product to 1.0), mapOf(alpha to 1.0), time = 2.0)
        val machine = production(smelt, product, speed = 2.0, productivity = 0.5)

        assertAll(
            { assertEquals(1.0, inputPerMachine(machine, alpha), TOLERANCE) },
            // the productivity bonus is what a direct-fed consumer really receives
            { assertEquals(1.5, outputPerMachine(machine, product), TOLERANCE) },
        )
    }

    @Test
    fun aPairInASmallMachineRatioIsBuiltAsOneBand() {
        val cable = recipe("copper-cable", mapOf(alpha to 1.0))
        val circuit = recipe("electronic-circuit", mapOf(product to 1.0), mapOf(alpha to 1.5))
        val feed =
            directFeed(
                circuit,
                listOf(production(circuit, product), production(cable, alpha)),
                scale = 1.0,
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            // a cable machine makes 1/s, a circuit machine takes 1.5/s: the 3:2 cycle is built
            {
                assertEquals(
                    listOf(
                        "copper-cable",
                        "electronic-circuit",
                        "copper-cable",
                        "electronic-circuit",
                        "copper-cable",
                    ),
                    feed.sequence(band),
                )
            },
            { assertEquals(listOf(alpha, alpha, alpha, alpha), band.links.map { it.material }) },
            { assertEquals(3.0, band.links.sumOf { it.flow }, TOLERANCE) },
            // the cable only feeds the band, so no belt row of it is built
            {
                assertEquals(
                    listOf("copper-cable", "electronic-circuit"),
                    feed.recipes.map { it.recipe.name },
                )
            },
        )
    }

    @Test
    fun aWindowIsCutSoThatNoConsumerStarves() {
        val rod = recipe("alpha-rod", mapOf(alpha to 1.0))
        val plateMill = recipe("gamma-plate", mapOf(gamma to 2.0))
        val unit = recipe("beta-unit", mapOf(product to 1.0), mapOf(alpha to 1.0, gamma to 1.0))
        val feed =
            directFeed(
                unit,
                listOf(
                    production(unit, product),
                    production(rod, alpha),
                    production(plateMill, gamma),
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
                    feed.sequence(band),
                )
            },
            { assertEquals(listOf(alpha, gamma, gamma, alpha), band.links.map { it.material }) },
        )
    }

    @Test
    fun aRateThatNoSmallCycleFitsKeepsBothRecipesBelted() {
        val mine = recipe("mine", mapOf(alpha to 20.0))
        val smelt = recipe("smelt", mapOf(product to 1.0), mapOf(alpha to 1.0))
        val feed =
            directFeed(
                smelt,
                listOf(production(smelt, product), production(mine, alpha, amount = 0.05)),
                scale = 1.0,
                logistics = logistics,
            )

        assertAll(
            { assertEquals(emptyList(), feed.bands) },
            {
                assertEquals(
                    setOf("mine", "smelt"),
                    feed.recipes.mapTo(mutableSetOf()) { it.recipe.name },
                )
            },
        )
    }

    @Test
    fun aMaterialWithASecondConsumerGetsOneMoreAllocation() {
        val cable = recipe("copper-cable", mapOf(alpha to 1.0))
        val circuit = recipe("electronic-circuit", mapOf(gamma to 1.0), mapOf(alpha to 1.0))
        val module = recipe("module", mapOf(product to 1.0), mapOf(alpha to 0.05))
        val feed =
            directFeed(
                module,
                listOf(
                    production(module, product),
                    production(circuit, gamma, amount = 2.0),
                    production(cable, alpha, amount = 0.1),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            // the circuit takes its cable straight from the band: two cable and two circuits
            {
                assertEquals(
                    listOf("copper-cable", "electronic-circuit"),
                    feed.sequence(band).distinct(),
                )
            },
            { assertEquals(4, band.machines.size) },
            // the module still needs its cable over a belt, so one more cable machine is built
            {
                assertEquals(
                    listOf("copper-cable", "electronic-circuit", "module", "copper-cable"),
                    feed.recipes.map { it.recipe.name },
                )
            },
            { assertEquals(listOf(2.0, 2.0, 1.0, 1.0), feed.recipes.map { it.machines }) },
        )
    }

    @Test
    fun aBeltedAllocationCoversThePlansRateNotAWholeConsumerMachine() {
        // the foundry-like consumer takes 20 alpha/s when it runs full, but the plan only ever
        // draws a twentieth of that rate; sizing the belt row by the whole machine would build nine
        // gear machines where a single one is enough
        val gear = recipe("gear", mapOf(alpha to 1.0), mapOf(gamma to 2.0))
        val inserter = recipe("inserter", mapOf(beta to 1.0), mapOf(alpha to 1.0))
        val foundry = recipe("foundry", mapOf(delta to 2.0), mapOf(alpha to 20.0))
        val target = recipe("target", mapOf(product to 1.0), mapOf(beta to 0.05, delta to 0.05))
        val feed =
            directFeed(
                target,
                listOf(
                    production(target, product),
                    production(inserter, beta, amount = 0.5),
                    production(gear, alpha, amount = 0.5),
                    production(foundry, delta, amount = 0.05),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        val machines =
            feed.recipes
                .groupingBy { it.recipe.name }
                .fold(0) { sum, allocation -> sum + allocation.machines.toInt() }
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
            { assertEquals(1, feed.recipes.count { it.recipe.name == "inserter" }) },
        )
    }

    @Test
    fun aBandStopsAtTheMachineCapAndTheRestStaysBelted() {
        val rod = recipe("alpha-rod", mapOf(alpha to 1.0))
        val plateMill = recipe("gamma-plate", mapOf(gamma to 2.0))
        val unit = recipe("beta-unit", mapOf(product to 1.0), mapOf(alpha to 1.0, gamma to 1.0))
        val feed =
            directFeed(
                unit,
                listOf(
                    production(unit, product, amount = 9.0),
                    production(rod, alpha, amount = 9.0),
                    production(plateMill, gamma, amount = 5.0),
                ),
                scale = 1.0,
                logistics = logistics,
            )

        assertAll(
            // the rod and the unit fill a 1:1 band of the 18 machines a band may hold
            { assertEquals(GROUP_MAX_MACHINES, feed.bands.single().machines.size) },
            {
                assertEquals(
                    listOf("alpha-rod", "beta-unit", "gamma-plate"),
                    feed.recipes.map { it.recipe.name },
                )
            },
            // the plate does not fit into that band any more, so it is built as a belt row of its
            // own
            { assertEquals(5.0, feed.recipes.last().machines) },
        )
    }

    @Test
    fun aRecipeThatTakesAFluidStaysBelted() {
        val mine = recipe("mine", mapOf(alpha to 1.0))
        val refine = recipe("refine", mapOf(product to 1.0), mapOf(alpha to 1.0, acid to 5.0))
        val fluids =
            LogisticsCalculator(
                // the test logistics carry no fluid by default, so name the acid as one
                testLogistics.copy(fluids = setOf(acid)),
                BeltTier.EXPRESS_TRANSPORT_BELT,
                InserterTier.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )
        val feed =
            directFeed(
                refine,
                listOf(production(refine, product), production(mine, alpha)),
                scale = 1.0,
                logistics = fluids,
            )

        // the band would put the refinery's fluid port between two machines, where the fluid feed
        // cannot reach it yet, so the rate fits but the recipe still keeps its belt
        assertEquals(emptyList(), feed.bands)
    }
}
