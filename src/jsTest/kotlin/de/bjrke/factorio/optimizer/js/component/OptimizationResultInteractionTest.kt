package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.js.component.fixtures.*
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
        val result = optimizationResult(miningRecipe, ore, Factory.ELECTRIC_MINING_DRILL)
        renderResult(result)

        fireEvent.click(screen.getByTitle(Factory.ELECTRIC_MINING_DRILL.identifier))

        assertEquals(listOf(1.0), normalizedTo)
    }

    @Test
    fun middleClickingAnIngredientIconSelectsThatMaterial() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.mouseDown(screen.getByTitle(plate.name), middleButton())

        assertEquals(listOf(plate), changedMaterials)
    }

    @Test
    fun middleClickingTheRecipeIconSelectsTheProducedMaterial() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        // the recipe icon comes before the output icon, which shares its title
        fireEvent.mouseDown(screen.getAllByTitle(gear.name)[0], middleButton())

        assertEquals(listOf(gear), changedMaterials)
    }

    @Test
    fun middleClickingTheFactoryIconSelectsNothing() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.mouseDown(
            screen.getByTitle(Factory.ASSEMBLING_MACHINE_1.identifier),
            middleButton(),
        )

        assertEquals(emptyList<Material>(), changedMaterials)
    }

    @Test
    fun leftClickingAMaterialIconOnlyNormalizesTheAmount() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        fireEvent.click(screen.getByTitle(plate.name))

        assertAll(
            { assertEquals(emptyList<Material>(), changedMaterials) },
            // the ingredient's consumption, exactly like clicking the amount icon
            { assertEquals(listOf(2.0), normalizedTo) },
        )
    }

    @Test
    fun logisticsCountsScaleWithTheNormalizeTarget() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)

        val rendered = renderResult(result, normalizeTarget = 1.0)
        val atOne = firstBeltInput().value.toDouble()

        rendered.rerender(resultElement(result, normalizeTarget = 2.0))
        val atTwo = firstBeltInput().value.toDouble()

        assertEquals(atOne * 2, atTwo, 0.01)
    }

    @Test
    fun editingTheBeltCountPinsIt() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        renderResult(result)

        val count = firstBeltInput().value.toDouble()
        fireEvent.change(firstBeltInput(), changeTo("1"))

        // changeNormalizeTo(count) makes the displayed count one
        assertEquals(count, normalizedTo.single(), 0.01)
    }

    @Test
    fun clickingTheBeltIconPinsItToOne() {
        val result = optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1)
        renderResult(result)

        fireEvent.click(screen.getAllByTitle(BeltTier.EXPRESS_TRANSPORT_BELT.identifier).first())

        assertEquals(firstBeltInput().value.toDouble(), normalizedTo.single(), 0.01)
    }
}
