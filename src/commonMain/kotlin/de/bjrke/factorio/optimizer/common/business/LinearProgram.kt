package de.bjrke.factorio.optimizer.common.business

private const val EPS = 1e-9

/** The outcome of a [LinearProgram]. */
sealed interface LinearProgramResult {
    /**
     * An optimal [solution] (length = number of variables) with the minimized objective [value].
     */
    data class Optimal(val solution: DoubleArray, val value: Double) : LinearProgramResult {
        override fun equals(other: Any?) =
            other is Optimal && solution.contentEquals(other.solution) && value == other.value

        override fun hashCode() = 31 * solution.contentHashCode() + value.hashCode()
    }

    data object Infeasible : LinearProgramResult

    data object Unbounded : LinearProgramResult
}

/**
 * Solves `minimize c·x` subject to `A·x <= b` and `x >= 0` with the two-phase simplex method.
 *
 * The tableau layout and the phase-one handling of a negative right-hand side follow the classic
 * KACTL/CP-Algorithms formulation: the objective row holds `c`, the last column is the artificial
 * variable of phase one and the second-to-last objective row its auxiliary objective. Pivoting is
 * deterministic (smallest variable index breaks ties) so the same input always yields the same
 * vertex.
 */
class LinearProgram(
    a: Array<DoubleArray>,
    b: DoubleArray,
    c: DoubleArray,
) {
    private val rows = b.size
    private val columns = c.size

    /** Index of the basic variable per row; indices `< columns` are `x`, the rest are slacks. */
    private val basic = IntArray(rows) { columns + it }

    private val lastRow = rows + 1
    private val lastCol = columns + 1

    /** Index of the non-basic variable per column; `-1` is the artificial variable. */
    private val nonBasic = IntArray(lastCol) { it }.also { it[columns] = -1 }

    private val tableau =
        Array(lastRow + 1) { i ->
                DoubleArray(lastCol + 1) { j ->
                    a.getOrNull(i)?.getOrNull(j) ?: 0.0
                }
            }
            .also {
                for (i in 0 until rows) {
                    it[i][columns] = -1.0
                    it[i][lastCol] = b[i]
                }
                for (j in 0 until columns) {
                    // objective row stores c so that the tableau maximizes -c·x, i.e. minimizes c·x
                    it[rows][j] = c[j]
                }
                it[lastRow][columns] = 1.0
            }

    private fun pivot(row: Int, column: Int) {
        val inverse = 1.0 / tableau[row][column]
        for (i in 0..lastRow) {
            if (i != row) {
                for (j in 0..lastCol) {
                    if (j != column) {
                        tableau[i][j] -= tableau[row][j] * tableau[i][column] * inverse
                    }
                }
            }
        }
        for (j in 0..lastCol) tableau[row][j] *= inverse
        for (i in 0..lastRow) tableau[i][column] *= -inverse
        tableau[row][column] = inverse
        val swap = basic[row]
        basic[row] = nonBasic[column]
        nonBasic[column] = swap
    }

    private fun simplex(phase: Int): Boolean {
        val objectiveRow = tableau[if (phase == 1) lastRow else rows]
        while (true) {
            var column = -1
            for (j in 0..columns) {
                when {
                    phase == 2 && nonBasic[j] == -1 -> continue
                    column == -1 -> column = j
                    objectiveRow[j] < objectiveRow[column] -> column = j
                    objectiveRow[j] == objectiveRow[column] && nonBasic[j] < nonBasic[column] ->
                        column = j
                }
            }
            if (objectiveRow[column] > -EPS) return true
            var row = -1
            for (i in 0 until rows) {
                when {
                    tableau[i][column] < EPS -> continue
                    row == -1 -> row = i
                    tableau[i][lastCol] / tableau[i][column] <
                        tableau[row][lastCol] / tableau[row][column] -> row = i
                    tableau[i][lastCol] / tableau[i][column] ==
                        tableau[row][lastCol] / tableau[row][column] && basic[i] < basic[row] ->
                        row = i
                }
            }
            if (row == -1) return false
            pivot(row, column)
        }
    }

    fun minimize(): LinearProgramResult {
        var row = 0
        for (i in 1 until rows) {
            if (tableau[i][lastCol] < tableau[row][lastCol]) row = i
        }

        if (tableau[row][lastCol] < -EPS) {
            pivot(row, columns)
            if (!simplex(1) || tableau[lastRow][lastCol] < -EPS)
                return LinearProgramResult.Infeasible
            for (i in 0 until rows) {
                if (basic[i] != -1) continue
                var column = 0
                for (j in 1..columns) {
                    if (
                        tableau[i][j] < tableau[i][column] ||
                            (tableau[i][j] == tableau[i][column] && nonBasic[j] < nonBasic[column])
                    ) {
                        column = j
                    }
                }
                pivot(i, column)
            }
        }

        return if (simplex(2)) {
            val solution = DoubleArray(columns)
            for (i in 0 until rows) {
                if (basic[i] < columns) solution[basic[i]] = tableau[i][lastCol]
            }
            // the objective row maximizes -c·x, so its value is the negative of the minimum
            LinearProgramResult.Optimal(solution, -tableau[rows][lastCol])
        } else LinearProgramResult.Unbounded
    }
}
