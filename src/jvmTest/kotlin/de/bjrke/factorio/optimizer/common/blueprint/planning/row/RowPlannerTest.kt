package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuit
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.splitter
import de.bjrke.factorio.optimizer.common.fixtures.splitterRecipe
import de.bjrke.factorio.optimizer.common.fixtures.transportBeltRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** One inserter-pattern case: how many near and far inserters, and the free edge columns. */
private data class InserterCase(
    val near: Int,
    val far: Int,
    val width: Int,
    val expected: List<Boolean>,
)

private fun testRecipe(
    name: String,
    ingredients: Map<Material, Double>,
    result: Material,
) =
    Recipe(
        name = name,
        ingredients = ingredients,
        result = mapOf(result to 1.0),
        time = 1.0,
        categories = setOf(Category.CRAFTING),
    )

private fun testRow(recipe: Recipe, material: Material) =
    PlannedRow.copies(planned(recipe, material, Factory.ASSEMBLING_MACHINE_3), 1)

class RowPlannerTest {

    private val planner = RowPlanner(basePlan, logistics)

    @Test
    fun theInserterPatternInterleavesTheLanesAndScalesThemToTheFreeColumns() =
        assertTableOf(
            InserterCase(2, 1, 4, listOf(false, true, false)),
            InserterCase(1, 2, 4, listOf(true, false, true)),
            InserterCase(2, 0, 3, listOf(false, false)),
            InserterCase(0, 2, 3, listOf(true, true)),
            InserterCase(0, 0, 3, emptyList()),
            InserterCase(3, 2, 3, listOf(false, true, false)),
            InserterCase(2, 2, 3, listOf(false, true, false)),
            InserterCase(1, 5, 3, listOf(true, false, true)),
            InserterCase(0, 5, 2, listOf(true, true)),
            InserterCase(5, 0, 2, listOf(false, false)),
            InserterCase(5, 5, 2, listOf(false, true)),
            InserterCase(5, 50, 2, listOf(true, false)),
            InserterCase(50, 5, 2, listOf(false, true)),
            InserterCase(2, 1, 1, listOf(false)),
            InserterCase(2, 1, 0, emptyList()),
        ) { case ->
            assertEquals(
                case.expected,
                planner
                    .inserterPattern(
                        mapOf(
                            false to case.near.toDouble(),
                            true to case.far.toDouble(),
                        ),
                        case.width,
                    )
                    .toList(),
            )
        }

    @Test
    fun aTargetSitsBetweenItsProducerBranches() {
        val circuit =
            PlannedRow.copies(planned(electronicCircuitRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val cable = PlannedRow.copies(planned(copperCableRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val target = PlannedRow.copies(planned(splitterRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val belt = PlannedRow.copies(planned(transportBeltRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val gearRow =
            PlannedRow.copies(planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val plan =
            BlueprintPlan(
                target = splitter,
                rows = listOf(circuit, cable, target, belt, gearRow),
                sources =
                    listOf(
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(copperPlate, 1.0, fluid = false),
                    ),
                label = "splitter",
            )

        val units = RowPlanner(plan, logistics).units

        assertAll(
            {
                assertEquals(
                    listOf(cable, circuit, target, belt, gearRow),
                    units.map { it.row },
                    "the target's north branch stays above it and the south branch below it",
                )
            },
            {
                assertEquals(
                    listOf(
                        LineSide.NORTH,
                        LineSide.NORTH,
                        null,
                        LineSide.SOUTH,
                        LineSide.SOUTH,
                    ),
                    units.map { it.side },
                )
            },
        )
    }

    @Test
    fun theLargestProducerBranchSitsNearestTheTarget() {
        // a contrived two-input target: the circuit branch has a cable child, the plastic branch
        // does not, so the branch sizes differ and the larger one must be nearest the target
        val targetRecipe =
            Recipe(
                name = "two-input-target",
                ingredients = mapOf(electronicCircuit to 1.0, plasticBar to 1.0),
                result = mapOf(advancedCircuit to 1.0),
                time = 1.0,
                categories = setOf(Category.CRAFTING),
            )
        val target =
            PlannedRow.copies(
                planned(targetRecipe, advancedCircuit, Factory.ASSEMBLING_MACHINE_3),
                1,
            )
        val circuit =
            PlannedRow.copies(planned(electronicCircuitRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val cable = PlannedRow.copies(planned(copperCableRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val plastic = PlannedRow.copies(planned(plasticBarRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val plan =
            BlueprintPlan(
                target = advancedCircuit,
                rows = listOf(target, circuit, cable, plastic),
                sources = emptyList(),
                label = "two-input-target",
            )

        val units = RowPlanner(plan, logistics).units

        assertAll(
            {
                assertEquals(
                    listOf(target, circuit, cable, plastic),
                    units.map { it.row },
                    "the larger circuit branch is nearest the target",
                )
            },
            {
                assertEquals(
                    listOf(null, LineSide.SOUTH, LineSide.SOUTH, LineSide.SOUTH),
                    units.map { it.side },
                )
            },
        )
    }

    @Test
    fun aTargetDistributesSmallProducerBranchesAcrossBothSides() {
        val input1 = Material("input-1")
        val input2 = Material("input-2")
        val input3 = Material("input-3")
        val sub1 = Material("sub-1")
        val sub2 = Material("sub-2")
        val sub3 = Material("sub-3")
        val targetMaterial = Material("target")
        val target =
            testRow(
                testRecipe(
                    "target",
                    mapOf(input1 to 1.0, input2 to 1.0, input3 to 1.0),
                    targetMaterial,
                ),
                targetMaterial,
            )
        val producer1 =
            testRow(testRecipe("producer-1", mapOf(sub1 to 1.0, sub2 to 1.0), input1), input1)
        val producer2 = testRow(testRecipe("producer-2", mapOf(sub3 to 1.0), input2), input2)
        val producer3 = testRow(testRecipe("producer-3", emptyMap(), input3), input3)
        val sub1Row = testRow(testRecipe("sub-1", emptyMap(), sub1), sub1)
        val sub2Row = testRow(testRecipe("sub-2", emptyMap(), sub2), sub2)
        val sub3Row = testRow(testRecipe("sub-3", emptyMap(), sub3), sub3)
        val plan =
            BlueprintPlan(
                target = targetMaterial,
                rows = listOf(target, producer1, producer2, producer3, sub1Row, sub2Row, sub3Row),
                sources = emptyList(),
                label = "target",
            )

        val planner = RowPlanner(plan, logistics)
        val targetUnit = planner.units.single { it.row == target }

        assertAll(
            {
                assertEquals(
                    LineSide.NORTH,
                    planner.preferredInputSide(targetUnit, input1),
                    "the largest branch goes north",
                )
            },
            {
                assertEquals(
                    LineSide.SOUTH,
                    planner.preferredInputSide(targetUnit, input2),
                    "the second branch goes south",
                )
            },
            {
                assertEquals(
                    LineSide.NORTH,
                    planner.preferredInputSide(targetUnit, input3),
                    "the third branch goes north",
                )
            },
        )
    }

    @Test
    fun structurallyEqualRowsStayDistinctOccurrences() {
        val duplicate =
            PlannedRow.copies(planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val target = PlannedRow.copies(planned(splitterRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val plan =
            BlueprintPlan(
                target = splitter,
                rows = listOf(duplicate, duplicate, target),
                sources = emptyList(),
                label = "splitter",
            )

        val units = RowPlanner(plan, logistics).units

        assertAll(
            { assertEquals(3, units.size) },
            { assertEquals(2, units.count { it.row == duplicate }) },
            { assertEquals(setOf(0, 1, 2), units.map { it.index }.toSet()) },
        )
    }
}
