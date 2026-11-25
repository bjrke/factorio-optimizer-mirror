package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.batteryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.splitterRecipe
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcidRecipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.test.assertAll
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

class DirectFeedFluidTest : BaseGameCtxTest() {

    private val logistics = calculator()

    @Test
    fun aRecipeThatTakesAFluidStillJoinsABand() {
        val target = production(processingUnitRecipe)
        val feed =
            directFeed(
                optimizationResult(target, production(advancedCircuitRecipe)),
                logistics = calculator(),
            )

        // the linked material is the solid advanced circuit, so the band is built even though the
        // consumer also takes sulfuric acid; the fluid lane serves its port on the row's outside
        // edge
        assertEquals(
            setOf(advancedCircuitRecipe, processingUnitRecipe),
            feed.bands.single().machines.map { it.recipe.recipe }.toSet(),
        )
    }

    @Test
    fun aProducerThatTakesAFluidStillFeedsItsBand() {
        val advanced = production(advancedCircuitRecipe)
        val feed =
            directFeed(
                optimizationResult(advanced, production(plasticBarRecipe)),
                logistics = calculator(),
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
                    setOf(
                        plasticBarRecipe,
                        advancedCircuitRecipe,
                    ),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aBandWithFiveBeltMaterialsUsesAMixedBelt() {
        val electric = production(electricEngineUnitRecipe)
        val feed =
            directFeed(
                optimizationResult(electric, production(engineUnitRecipe)),
                logistics = calculator(),
            )

        val band = feed.bands.single()
        // steel, gear, pipe, circuit and the product are five materials; one input shares a belt
        // and the band still fits the row's four belt rows
        assertEquals(
            setOf(electricEngineUnitRecipe, engineUnitRecipe),
            band.machines.map { it.recipe.recipe }.toSet(),
        )
    }

    @Test
    fun aRealGearAndBeltChainFeedsTheBeltRecipe() {
        val thirdProduction = production(transportBeltRecipe)
        val feed =
            directFeed(
                optimizationResult(
                    thirdProduction,
                    production(ironGearWheelRecipe),
                    production(ironPlateSmeltingRecipe),
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
    fun aRealBeltAndSplitterChainFormsABand() {
        val fourthProduction = production(splitterRecipe)
        val feed =
            directFeed(
                optimizationResult(
                    fourthProduction,
                    production(transportBeltRecipe),
                    production(ironGearWheelRecipe),
                    production(ironPlateSmeltingRecipe),
                ),
                logistics = logistics,
            )

        val band = feed.bands.single()
        assertAll(
            {
                assertNotNull(band.machines.firstOrNull { it.recipe.recipe == transportBeltRecipe })
            },
            { assertNotNull(band.machines.firstOrNull { it.recipe.recipe == splitterRecipe }) },
            { assertNotNull(band.links.firstOrNull { it.material == transportBelt }) },
            { assertContains(feed.recipes, ironGearWheelRecipe) },
            { assertTrue(feed.rows.isNotEmpty()) },
        )
    }

    @Test
    fun aBandMayOutputAFluidAsItsFinalOutput() {
        val sulfuricAcidProduction = production(sulfuricAcidRecipe)
        val feed =
            directFeed(
                optimizationResult(sulfuricAcidProduction, production(ironPlateRecipe)),
                logistics = calculator(),
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
            {
                assertEquals(
                    setOf(ironPlateRecipe, sulfuricAcidRecipe),
                    feed.recipes.toSet(),
                )
            },
        )
    }

    @Test
    fun aFluidMaterialIsNeverDirectFed() {
        val batteryProduction = production(batteryRecipe)
        val feed =
            directFeed(
                optimizationResult(batteryProduction, production(sulfuricAcidRecipe)),
                logistics = calculator(),
            )

        // a fluid link would need a pipe, so the acid stays a fluid source and never a link
        assertEquals(emptyList(), feed.bands)
    }
}
