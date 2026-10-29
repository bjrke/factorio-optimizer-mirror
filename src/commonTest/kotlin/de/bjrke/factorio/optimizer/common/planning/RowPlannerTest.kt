package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
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

class RowPlannerTest {

    private val planner = RowPlanner(basePlan, logistics)

    @Test
    fun theInserterPatternInterleavesTheLanesAndScalesThemToTheFreeColumns() =
        assertTableOf(
            InserterCase(2, 1, 4, listOf(false, true, false)),
            InserterCase(1, 2, 4, listOf(false, true, true)),
            InserterCase(2, 0, 3, listOf(false, false)),
            InserterCase(0, 2, 3, listOf(true, true)),
            InserterCase(0, 0, 3, emptyList()),
            InserterCase(3, 2, 3, listOf(false, true, false)),
            InserterCase(2, 2, 3, listOf(false, true, false)),
            InserterCase(1, 5, 3, listOf(false, true, true)),
            InserterCase(0, 5, 2, listOf(true, true)),
            InserterCase(5, 0, 2, listOf(false, false)),
            InserterCase(5, 5, 2, listOf(false, true)),
            InserterCase(5, 50, 2, listOf(false, true)),
            InserterCase(50, 5, 2, listOf(false, true)),
            InserterCase(2, 1, 1, listOf(false)),
            InserterCase(2, 1, 0, emptyList()),
        ) { case ->
            assertEquals(
                case.expected,
                planner.inserterPattern(case.near, case.far, case.width),
            )
        }
}
