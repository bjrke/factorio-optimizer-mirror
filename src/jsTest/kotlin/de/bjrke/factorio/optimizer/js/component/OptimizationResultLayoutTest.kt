package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.GameCtxTestMixin
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromLightOilRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier
import de.bjrke.factorio.optimizer.js.component.fixtures.OptimizationResultTestBase
import de.bjrke.test.assertAll
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How the result block measures up: widths, overflow, rotations and mirrored icons. */
class OptimizationResultLayoutTest :
    OptimizationResultTestBase(), GameCtxTestMixin by BaseGameCtxTest() {

    @Test
    fun ingredientAndOutputBeltsAreRotatedOppositely() {
        renderResult(optimizationResult(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1))

        val belts = screen.getAllByTitle(BeltTier.FAST_TRANSPORT_BELT.identifier)

        assertAll(
            { assertEquals("matrix(0, 1, -1, 0, 0, 0)", computedStyle(belts[0], "transform")) },
            { assertEquals("matrix(0, -1, 1, 0, 0, 0)", computedStyle(belts[1], "transform")) },
        )
    }

    @Test
    fun onlyTheOutputInserterIsMirroredHorizontally() {
        renderResult(optimizationResult(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1))

        val inserters = screen.getAllByTitle(InserterTier.FAST_INSERTER.identifier)

        assertAll(
            { assertEquals("none", computedStyle(inserters[0], "transform")) },
            { assertEquals("matrix(-1, 0, 0, 1, 0, 0)", computedStyle(inserters[1], "transform")) },
        )
    }

    @Test
    fun beltAndInserterIconsAreAsLargeAsTheMaterialIcons() {
        renderResult(optimizationResult(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1))

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
            renderResult(
                optimizationResult(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1),
            )

        val inputs = rendered.container.querySelectorAll("input[type=number]")
        val widths = (0 until inputs.length).map { computedStyle(inputs.item(it)!!, "width") }

        // factory, ingredient and output amounts plus their belt/inserter counts
        assertEquals(7, widths.size)
        assertEquals(listOf("64px"), widths.distinct())
    }

    @Test
    fun materialRowsDoNotOverflowTheirColumn() {
        val rendered =
            renderResult(
                optimizationResult(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1),
            )

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
    fun factoryBlocksStackInOneColumn() {
        val ores = production(ironOreMiningRecipe, Factory.ELECTRIC_MINING_DRILL)
        val gears = production(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1)
        val rendered = renderResult(optimizationResult(gears, ores))
        // wide enough to fit both blocks side by side if they were still inline
        js("(function (el) { el.style.width = '2000px'; })")(rendered.container)

        val blocks = rendered.container.querySelectorAll(":scope > div")
        val tops = (0 until blocks.length).map { blocks.item(it)!!.getBoundingClientRect().top }

        assertEquals(2, tops.size)
        assertTrue(tops[1] > tops[0], "factory blocks should stack in one column")
    }

    @Test
    fun recipesWithAndWithoutIngredientsHaveTheSameWidth() {
        val ores = production(ironOreMiningRecipe, Factory.ELECTRIC_MINING_DRILL)
        val gears = production(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1)
        val rendered = renderResult(optimizationResult(gears, ores))

        val widths = blockWidths(rendered.container)
        assertEquals(2, widths.size)
        assertEquals(widths[0], widths[1], 0.5)
    }

    @Test
    fun solidAndFluidRowsHaveTheSameWidth() {
        val calculator = LogisticsCalculator(testLogistics)

        val gears = production(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_1)
        val solidFuel = production(solidFuelFromLightOilRecipe, Factory.CHEMICAL_PLANT)
        val rendered = renderResult(optimizationResult(solidFuel, gears), calculator)

        val widths = blockWidths(rendered.container)
        assertEquals(2, widths.size)
        assertEquals(widths[0], widths[1], 0.5)
    }
}
