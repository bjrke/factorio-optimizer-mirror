package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.battery
import de.bjrke.factorio.optimizer.common.fixtures.batteryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnit
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.processingUnit
import de.bjrke.factorio.optimizer.common.fixtures.processingUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.splitter
import de.bjrke.factorio.optimizer.common.fixtures.splitterRecipe
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcidRecipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBelt
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.assertAll
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

class DirectFeedFluidTest : BaseGameCtxTest() {

    private val logistics = calculator()

    private fun factories(vararg productions: FactoryProduction) = productions.associateBy {
        it.recipe
    }

    @Test
    fun aRecipeThatTakesAFluidStillJoinsABand() {
        val target = production(processingUnitRecipe, processingUnit)
        val feed =
            directFeed(
                target,
                factories(target, production(advancedCircuitRecipe, advancedCircuit)),
                logistics = calculator(),
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
                factories(advanced, production(plasticBarRecipe, plasticBar)),
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
            { assertEquals(setOf(plasticBarRecipe, advancedCircuitRecipe), feed.recipes.toSet()) },
        )
    }

    @Test
    fun aBandWithFiveBeltMaterialsUsesAMixedBelt() {
        val electric = production(electricEngineUnitRecipe, electricEngineUnit)
        val feed =
            directFeed(
                electric,
                factories(electric, production(engineUnitRecipe, engineUnit)),
                logistics = calculator(),
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
                factories(sulfuricAcidProduction, production(ironPlateRecipe, ironPlate)),
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
            { assertEquals(setOf(ironPlateRecipe, sulfuricAcidRecipe), feed.recipes.toSet()) },
        )
    }

    @Test
    fun aFluidMaterialIsNeverDirectFed() {
        val batteryProduction = production(batteryRecipe, battery)
        val feed =
            directFeed(
                batteryProduction,
                factories(batteryProduction, production(sulfuricAcidRecipe, sulfuricAcid)),
                logistics = calculator(),
            )

        // a fluid link would need a pipe, so the acid stays a fluid source and never a link
        assertEquals(emptyList(), feed.bands)
    }
}
