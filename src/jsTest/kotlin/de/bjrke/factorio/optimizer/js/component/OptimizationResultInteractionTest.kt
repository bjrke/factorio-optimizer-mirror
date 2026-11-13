package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.js.component.fixtures.OptimizationResultTestBase
import de.bjrke.factorio.optimizer.js.component.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.js.component.fixtures.production
import de.bjrke.factorio.optimizer.js.component.fixtures.twoProductions
import de.bjrke.test.assertAll
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a click does: normalize an amount, select a material, or pin a count. */
class OptimizationResultInteractionTest : OptimizationResultTestBase() {
    @Test
    fun clickingTheFactoryIconNormalizesToTheFactoryAmount() {
        val result = optimizationResult(ironOreMiningRecipe, ironOre, Factory.ELECTRIC_MINING_DRILL)
        renderResult(result)

        fireEvent.click(screen.getByTitle(Factory.ELECTRIC_MINING_DRILL.identifier))

        assertEquals(listOf(1.0), normalizedTo)
    }

    @Test
    fun middleClickingAnIngredientIconSelectsThatMaterial() {
        renderResult(optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.mouseDown(screen.getByTitle(ironPlate.name), middleButton())

        assertEquals(listOf(ironPlate), changedMaterials)
    }

    @Test
    fun middleClickingTheRecipeIconSelectsTheProducedMaterial() {
        renderResult(optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        // the recipe icon comes before the output icon, which shares its title
        fireEvent.mouseDown(screen.getAllByTitle(gear.name)[0], middleButton())

        assertEquals(listOf(gear), changedMaterials)
    }

    @Test
    fun middleClickingTheFactoryIconSelectsNothing() {
        renderResult(optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.mouseDown(
            screen.getByTitle(Factory.ASSEMBLING_MACHINE_1.identifier),
            middleButton(),
        )

        assertEquals(emptyList(), changedMaterials)
    }

    @Test
    fun leftClickingAMaterialIconOnlyNormalizesTheAmount() {
        renderResult(optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.click(screen.getByTitle(ironPlate.name))

        assertAll(
            { assertEquals(emptyList(), changedMaterials) },
            // the ingredient's consumption, exactly like clicking the amount icon
            { assertEquals(listOf(2.0), normalizedTo) },
        )
    }

    @Test
    fun logisticsCountsScaleWithTheNormalizeTarget() {
        val result = optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        val rendered = renderResult(result, normalizeTarget = 1.0)
        val atOne = firstBeltInput().value.toDouble()

        rendered.rerender(resultElement(result, normalizeTarget = 2.0))
        val atTwo = firstBeltInput().value.toDouble()

        assertEquals(atOne * 2, atTwo, 0.01)
    }

    @Test
    fun editingTheBeltCountPinsIt() {
        val result = optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        renderResult(result)

        val count = firstBeltInput().value.toDouble()
        fireEvent.change(firstBeltInput(), changeTo("1"))

        // changeNormalizeTo(count) makes the displayed count one
        assertEquals(count, normalizedTo.single(), 0.01)
    }

    @Test
    fun clickingTheBeltIconPinsItToOne() {
        val result = optimizationResult(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        renderResult(result)

        fireEvent.click(screen.getAllByTitle(BeltTier.FAST_TRANSPORT_BELT.identifier).first())

        assertEquals(firstBeltInput().value.toDouble(), normalizedTo.single(), 0.01)
    }

    private val importRecipe =
        Recipe(
            name = ironPlate.name,
            ingredients = emptyMap(),
            result = mapOf(ironPlate to 1.0),
            time = 1.0,
            categories = setOf(Category.IMPORT),
        )

    private fun resultWithImport() =
        OptimizationResult(
            target = gear,
            recipe = ironGearWheelRecipe,
            factories =
                mapOf(
                    ironGearWheelRecipe to
                        production(
                            Triple(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                        ),
                    importRecipe to production(Triple(importRecipe, ironPlate, Factory.NO_FACTORY)),
                ),
        )

    @Test
    fun checkingTheExclusionBoxReportsTheMaterial() {
        renderResult(
            twoProductions(
                Triple(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                Triple(ironOreMiningRecipe, ironOre, Factory.ELECTRIC_MINING_DRILL),
            ),
        )

        fireEvent.click(exclusionCheckbox("Exclude ${ironOre.name} from the blueprint"))

        assertEquals(listOf(ironOre to true), changedExclusions)
    }

    @Test
    fun uncheckingAnExcludedImportReportsTheMaterial() {
        renderResult(resultWithImport(), excludedMaterials = setOf(ironPlate))

        fireEvent.click(exclusionCheckbox("Include ${ironPlate.name} in the blueprint"))

        assertEquals(listOf(ironPlate to false), changedExclusions)
    }
}
