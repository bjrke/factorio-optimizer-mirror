package de.bjrke.factorio.optimizer.buildlogic

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CheckFileSizesTest {

    @Test
    fun fileOverTheHardBudgetFails() {
        val result = check(mapOf("A.kt" to 501))

        assertEquals(listOf("A.kt has 501 lines; split it (hard budget 500)"), result.violations)
        assertNotNull(result.error)
    }

    @Test
    fun fileAtTheHardBudgetIsAllowedButWarns() {
        val result = check(mapOf("A.kt" to 500))

        assertEquals(emptyList(), result.violations)
        assertEquals(listOf("A.kt has 500 lines; over the soft budget 400"), result.warnings)
        assertNull(result.error)
    }

    @Test
    fun fileOverTheSoftBudgetWarns() {
        val result = check(mapOf("A.kt" to 401))

        assertEquals(emptyList(), result.violations)
        assertEquals(listOf("A.kt has 401 lines; over the soft budget 400"), result.warnings)
        assertEquals(emptyList(), result.infos)
        assertNull(result.error)
    }

    @Test
    fun fileAtTheSoftBudgetIsOnlyInfo() {
        val result = check(mapOf("A.kt" to 400))

        assertEquals(emptyList(), result.violations)
        assertEquals(emptyList(), result.warnings)
        assertEquals(listOf("A.kt has 400 lines; over the super-soft budget 300"), result.infos)
        assertNull(result.error)
    }

    @Test
    fun fileOverTheSuperSoftBudgetIsOnlyInfo() {
        val result = check(mapOf("A.kt" to 301))

        assertEquals(emptyList(), result.violations)
        assertEquals(emptyList(), result.warnings)
        assertEquals(listOf("A.kt has 301 lines; over the super-soft budget 300"), result.infos)
        assertNull(result.error)
    }

    @Test
    fun fileAtTheSuperSoftBudgetIsSilent() {
        val result = check(mapOf("A.kt" to 300))

        assertEquals(emptyList(), result.violations)
        assertEquals(emptyList(), result.warnings)
        assertEquals(emptyList(), result.infos)
        assertNull(result.error)
    }

    @Test
    fun smallFileIsSilent() {
        val result = check(mapOf("A.kt" to 42))

        assertEquals(emptyList(), result.violations)
        assertEquals(emptyList(), result.warnings)
        assertEquals(emptyList(), result.infos)
        assertNull(result.error)
    }

    private class Result(
        val violations: List<String>,
        val warnings: List<String>,
        val infos: List<String>,
        val error: IllegalStateException?,
    )

    private fun check(files: Map<String, Int>): Result {
        val root = Files.createTempDirectory("checkFileSizes").toFile()
        for ((path, lines) in files) {
            val file = File(root, path)
            file.parentFile.mkdirs()
            file.writeText("x\n".repeat(lines))
        }

        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val infos = mutableListOf<String>()
        val error =
            try {
                checkFileSizes(
                    root,
                    SUPER_SOFT_SOURCE_LINES,
                    SOFT_SOURCE_LINES,
                    HARD_SOURCE_LINES,
                    violations::add,
                    warnings::add,
                    infos::add,
                )
                null
            } catch (exception: IllegalStateException) {
                exception
            }
        return Result(violations, warnings, infos, error)
    }
}
