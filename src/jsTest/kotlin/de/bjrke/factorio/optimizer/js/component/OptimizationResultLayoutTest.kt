package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.js.component.fixtures.OptimizationResultTestBase
import de.bjrke.factorio.optimizer.js.component.fixtures.craftingRecipe
import de.bjrke.factorio.optimizer.js.component.fixtures.miningRecipe
import de.bjrke.factorio.optimizer.js.component.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.js.component.fixtures.twoProductions
import de.bjrke.test.assertAll
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How the result block measures up: widths, overflow, rotations and mirrored icons. */
class OptimizationResultLayoutTest : OptimizationResultTestBase() {

    @Test
    fun ingredientAndOutputBeltsAreRotatedOppositely() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val belts = screen.getAllByTitle(BeltTier.FAST_TRANSPORT_BELT.identifier)

        assertAll(
            { assertEquals("matrix(0, 1, -1, 0, 0, 0)", computedStyle(belts[0], "transform")) },
            { assertEquals("matrix(0, -1, 1, 0, 0, 0)", computedStyle(belts[1], "transform")) },
        )
    }

    @Test
    fun onlyTheOutputInserterIsMirroredHorizontally() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val inserters = screen.getAllByTitle(InserterTier.FAST_INSERTER.identifier)

        assertAll(
            { assertEquals("none", computedStyle(inserters[0], "transform")) },
            { assertEquals("matrix(-1, 0, 0, 1, 0, 0)", computedStyle(inserters[1], "transform")) },
        )
    }

    @Test
    fun beltAndInserterIconsAreAsLargeAsTheMaterialIcons() {
        renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val materialSize = computedStyle(screen.getByTitle(ironPlate.name), "width")

        assertAll(
            {
                assertEquals(
                    materialSize,
                    computedStyle(
                        screen.getAllByTitle(BeltTier.FAST_TRANSPORT_BELT.identifier)[0],
                        "width",
                    ),
                )
            },
            {
                assertEquals(
                    materialSize,
                    computedStyle(
                        screen.getAllByTitle(InserterTier.FAST_INSERTER.identifier)[0],
                        "width",
                    ),
                )
            },
        )
    }

    @Test
    fun everyNumberInputHasTheSameWidth() {
        val rendered =
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val inputs = rendered.container.querySelectorAll("input[type=number]")
        val widths = (0 until inputs.length).map { computedStyle(inputs.item(it)!!, "width") }

        // factory, ingredient and output amounts plus their belt/inserter counts
        assertEquals(7, widths.size)
        assertEquals(listOf("64px"), widths.distinct())
    }

    @Test
    fun materialRowsDoNotOverflowTheirColumn() {
        val rendered =
            renderResult(optimizationResult(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1))

        val columns = rendered.container.querySelectorAll(":scope > div > div")
        assertTrue(columns.length >= 2)
        for (index in 0 until columns.length) {
            val column = columns.item(index)!!
            assertTrue(
                column.scrollWidth <= column.clientWidth + 1,
                "column $index overflows (${column.scrollWidth} > ${column.clientWidth})",
            )
        }
    }

    @Test
    fun recipesWithAndWithoutIngredientsHaveTheSameWidth() {
        val rendered =
            renderResult(
                twoProductions(
                    Triple(miningRecipe, ironOre, Factory.ELECTRIC_MINING_DRILL),
                    Triple(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                ),
            )

        val widths = blockWidths(rendered.container)
        assertEquals(2, widths.size)
        assertEquals(widths[0], widths[1], 0.5)
    }

    @Test
    fun solidAndFluidRowsHaveTheSameWidth() {
        val fluidRecipe =
            Recipe(
                name = "heavy-oil",
                ingredients = mapOf(water to 10.0),
                result = mapOf(gear to 1.0),
                time = 1.0,
                categories = setOf(Category.CHEMISTRY),
            )
        val calculator = LogisticsCalculator(testLogistics.copy(fluids = setOf(water)))

        val rendered =
            renderResult(
                twoProductions(
                    Triple(craftingRecipe, gear, Factory.ASSEMBLING_MACHINE_1),
                    Triple(fluidRecipe, gear, Factory.CHEMICAL_PLANT),
                ),
                calculator,
            )

        val widths = blockWidths(rendered.container)
        assertEquals(2, widths.size)
        assertEquals(widths[0], widths[1], 0.5)
    }
}
