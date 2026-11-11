package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.beltRowBudget
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.oneLaneCapacity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowInputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowOutputs
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compaction bounds and the capacity limits of wide rows. */
class BlueprintLayoutCompactionTest {

    @Test
    fun feedRoutesStayEastOfTheWestBound() {
        val layout = Layout(basePlan)
        val entities = layout.entities
        // compaction may shift the whole layout, so the import extension is the reference, not x=2
        val westBound = entities.filterIsInstance<MarkerEntity>().minOf { it.area.topLeft.x } - 1
        val belts = entities.filter {
            it.name.contains("transport-belt") || it.name.contains("splitter")
        }
        val west = belts.map { it.area.topLeft }.filter { it.x < westBound }
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), west, "belts west of the import column") },
        )
    }

    @Test
    fun compactionRemovesMostOfTheEmptySpace() {
        val layout = Layout(basePlan)
        val minX = layout.entities.minOf { it.area.topLeft.x }
        val maxX = layout.entities.maxOf { it.area.bottomRight.x }
        val minY = layout.entities.minOf { it.area.topLeft.y }
        val maxY = layout.entities.maxOf { it.area.bottomRight.y }
        val width = maxX - minX + 1
        val height = maxY - minY + 1

        assertAll(
            { assertTrue(width < 10, "layout is still $width tiles wide") },
            { assertTrue(height < 20, "layout is still $height tiles tall") },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun aSixInputRowMixesThreeInputPairs() {
        val inputs = (1..6).map { Material("input-$it") }
        val output = Material("output")
        val recipe =
            Recipe(
                name = "six-inputs",
                ingredients = inputs.associateWith { 1.0 },
                result = mapOf(output to 1.0),
                time = 10.0,
                categories = setOf(Category.CRAFTING),
            )
        val production = planned(recipe, Factory.ASSEMBLING_MACHINE_3)
        val plan =
            BlueprintPlan(
                target = output,
                rows = listOf(PlannedRow.copies(production, 1)),
                sources = inputs.map { PlannedSource(it, 1.0, fluid = false) },
                label = "six-inputs",
            )
        val layout = Layout(plan)
        val belts = layout.entities.filterIsInstance<BeltEntity>()
        val mixed = belts.filter { it.load.isMixed }
        // the lane belts start at the machine edge; the merge tiles sit west of it
        val rowLeft = layout.machineEntities.minOf { it.area.topLeft.x }
        val laneRows =
            mixed.filter { it.area.topLeft.x >= rowLeft }.map { it.area.topLeft.y }.distinct()
        val product = belts.filter { it.material == output }
        val carried = belts.flatMap { it.materials }.toSet()
        val budget =
            beltRowBudget(
                rowInputs(listOf(production), layout.logistics),
                rowOutputs(listOf(production), layout.logistics),
                layout.logistics.oneLaneCapacity(),
            )

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(3, laneRows.size) },
            { assertEquals(emptyList(), mixed.filterNot { it.load.materials.size == 2 }) },
            { assertNotNull(product.firstOrNull(), "no product belt") },
            { assertEquals(emptyList(), product.filter { it.load.isFull }) },
            { assertEquals(emptyList(), inputs.filterNot { it in carried }) },
            { assertTrue(budget.fits) },
            { assertEquals(4, budget.rows) },
        )
    }

    @Test
    fun producedInputsPairOnBothFeedSides() {
        val ore = Material("ore")
        val parts = (1..5).map { Material("part-$it") }
        val product = Material("product")
        val producerRows = parts.mapIndexed { index, part ->
            val recipe =
                Recipe(
                    name = "producer-${index + 1}",
                    ingredients = mapOf(ore to 1.0),
                    result = mapOf(part to 1.0),
                    time = 10.0,
                    categories = setOf(Category.CRAFTING),
                )
            PlannedRow.copies(planned(recipe, Factory.ASSEMBLING_MACHINE_3), index + 1)
        }
        val consumerRecipe =
            Recipe(
                name = "consumer",
                ingredients = parts.associateWith { 1.0 },
                result = mapOf(product to 1.0),
                time = 10.0,
                categories = setOf(Category.CRAFTING),
            )
        val plan =
            BlueprintPlan(
                target = product,
                rows =
                    producerRows +
                        PlannedRow.copies(
                            planned(consumerRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                label = "consumer",
            )

        val layout = Layout(plan)
        val mixed = layout.entities.filterIsInstance<BeltEntity>().filter { it.load.isMixed }
        val lanes = mixed.filter { it.direction == EAST || it.direction == WEST }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(setOf(EAST, WEST), lanes.map { it.direction }.toSet()) },
            { assertEquals(emptyList(), mixed.filterNot { it.load.materials.size == 2 }) },
        )
    }

    @Test
    fun aRowThatCannotFitReportsTheMissingMaterials() {
        val inputs = (1..7).map { Material("input-$it") }
        val output = Material("output")
        val recipe =
            Recipe(
                name = "seven-inputs",
                ingredients = inputs.associateWith { 1.0 },
                result = mapOf(output to 1.0),
                time = 10.0,
                categories = setOf(Category.CRAFTING),
            )
        val production = planned(recipe, Factory.ASSEMBLING_MACHINE_3)
        val layout =
            Layout(
                BlueprintPlan(
                    target = output,
                    rows = listOf(PlannedRow.copies(production, 1)),
                    sources = inputs.map { PlannedSource(it, 1.0, fluid = false) },
                    label = "seven-inputs",
                ),
            )

        assertAll(
            {
                assertNotNull(layout.failures.firstOrNull(), "a row that cannot fit must report it")
            },
            { assertContains(layout.failures, "output has no output belt on its row") },
        )
    }
}
