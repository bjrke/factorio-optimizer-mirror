package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LinearProgramTest {

    private fun solve(
        a: Array<DoubleArray>,
        b: DoubleArray,
        c: DoubleArray,
        normalizeRows: Boolean = false,
    ) = LinearProgram(a, b, c, normalizeRows).minimize()

    @Test
    fun minimizesASingleVariable() {
        val result = solve(arrayOf(doubleArrayOf(-1.0)), doubleArrayOf(-1.0), doubleArrayOf(1.0))

        val optimal = result as LinearProgramResult.Optimal
        assertAll(
            { assertEquals(1.0, optimal.value, TOLERANCE) },
            { assertEquals(1.0, optimal.solution[0], TOLERANCE) },
        )
    }

    @Test
    fun sumsTheCostAcrossVariables() {
        val result =
            solve(
                arrayOf(doubleArrayOf(-1.0, -1.0)),
                doubleArrayOf(-2.0),
                doubleArrayOf(1.0, 1.0),
            )

        val optimal = result as LinearProgramResult.Optimal
        assertEquals(2.0, optimal.value, TOLERANCE)
    }

    @Test
    fun picksTheCheaperOfTwoProducers() {
        // satisfy x + y >= 1; x costs 1, y costs 10 -> all demand goes to x
        val result =
            solve(
                arrayOf(doubleArrayOf(-1.0, -1.0)),
                doubleArrayOf(-1.0),
                doubleArrayOf(1.0, 10.0),
            )

        val optimal = result as LinearProgramResult.Optimal
        assertAll(
            { assertEquals(1.0, optimal.value, TOLERANCE) },
            { assertEquals(1.0, optimal.solution[0], TOLERANCE) },
            { assertEquals(0.0, optimal.solution[1], TOLERANCE) },
        )
    }

    @Test
    fun respectsAnUpperBound() {
        // 1 <= x <= 2, minimize x -> 1
        val result =
            solve(
                arrayOf(doubleArrayOf(1.0), doubleArrayOf(-1.0)),
                doubleArrayOf(2.0, -1.0),
                doubleArrayOf(1.0),
            )

        assertEquals(1.0, (result as LinearProgramResult.Optimal).value, TOLERANCE)
    }

    @Test
    fun reportsInfeasibleProblems() {
        // x <= -1 with x >= 0
        val result = solve(arrayOf(doubleArrayOf(1.0)), doubleArrayOf(-1.0), doubleArrayOf(1.0))

        assertIs<LinearProgramResult.Infeasible>(result)
    }

    @Test
    fun reportsUnboundedProblems() {
        // minimize -x with only y <= 1: x can grow forever
        val result =
            solve(arrayOf(doubleArrayOf(0.0, 1.0)), doubleArrayOf(1.0), doubleArrayOf(-1.0, 0.0))

        assertIs<LinearProgramResult.Unbounded>(result)
    }

    @Test
    fun aRowThatMixesRateScalesKeepsItsOptimum() {
        // the optimizer's rows mix uranium processing's 5.8e-4/s with demands of 2.5/s; dividing a
        // constraint by a positive factor is the same constraint and must not move the optimum
        val rows = arrayOf(doubleArrayOf(-1e6, -1e-6))
        val bounds = doubleArrayOf(-1.0)
        val cost = doubleArrayOf(1.0, 1.0)
        val raw = solve(rows, bounds, cost) as LinearProgramResult.Optimal
        val normalized =
            solve(rows, bounds, cost, normalizeRows = true) as LinearProgramResult.Optimal

        assertAll(
            { assertEquals(1e-6, raw.value, TOLERANCE) },
            { assertEquals(raw.value, normalized.value, TOLERANCE) },
            { assertEquals(raw.solution[0], normalized.solution[0], TOLERANCE) },
            { assertEquals(raw.solution[1], normalized.solution[1], TOLERANCE) },
        )
    }
}
