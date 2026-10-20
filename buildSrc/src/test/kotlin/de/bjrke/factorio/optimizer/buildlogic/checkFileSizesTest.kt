package de.bjrke.factorio.optimizer.buildlogic

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
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
        assertEquals(listOf("A.kt has 500 lines; over the soft budget 300"), result.warnings)
        assertNull(result.error)
    }

    @Test
    fun baselinedFileAtItsExactSizePasses() {
        val result = check(mapOf("A.kt" to 620), baseline = mapOf("A.kt" to 620))

        assertEquals(emptyList(), result.violations)
        assertNull(result.error)
    }

    @Test
    fun baselinedFileThatShrankMustLowerItsEntry() {
        val result = check(mapOf("A.kt" to 510), baseline = mapOf("A.kt" to 620))

        assertContains(result.violations, "A.kt shrank to 510 lines; lower its baseline from 620")
        assertNotNull(result.error)
    }

    @Test
    fun baselinedFileThatGrewFails() {
        val result = check(mapOf("A.kt" to 640), baseline = mapOf("A.kt" to 620))

        assertContains(result.violations, "A.kt grew to 640 lines (baseline 620)")
        assertNotNull(result.error)
    }

    @Test
    fun staleEntryForAFileWithinTheHardBudgetFails() {
        val result = check(mapOf("A.kt" to 400), baseline = mapOf("A.kt" to 400))

        assertContains(result.violations, "A.kt no longer needs a baseline entry; remove it")
        assertNotNull(result.error)
    }

    @Test
    fun staleEntryForAMissingFileFails() {
        val result = check(emptyMap(), baseline = mapOf("Gone.kt" to 510))

        assertContains(result.violations, "Gone.kt no longer needs a baseline entry; remove it")
        assertNotNull(result.error)
    }

    @Test
    fun fileOverTheSoftBudgetOnlyWarns() {
        val result = check(mapOf("A.kt" to 301))

        assertEquals(emptyList(), result.violations)
        assertEquals(listOf("A.kt has 301 lines; over the soft budget 300"), result.warnings)
        assertNull(result.error)
    }

    @Test
    fun fileAtTheSoftBudgetIsSilent() {
        val result = check(mapOf("A.kt" to 300))

        assertEquals(emptyList(), result.violations)
        assertEquals(emptyList(), result.warnings)
        assertNull(result.error)
    }

    @Test
    fun smallFileIsSilent() {
        val result = check(mapOf("A.kt" to 42))

        assertEquals(emptyList(), result.violations)
        assertEquals(emptyList(), result.warnings)
        assertNull(result.error)
    }

    private class Result(
        val violations: List<String>,
        val warnings: List<String>,
        val error: IllegalStateException?,
    )

    private fun check(
        files: Map<String, Int>,
        baseline: Map<String, Int> = emptyMap(),
    ): Result {
        val root = Files.createTempDirectory("checkFileSizes").toFile()
        for ((path, lines) in files) {
            val file = File(root, path)
            file.parentFile.mkdirs()
            file.writeText("x\n".repeat(lines))
        }
        val baselineFile = File(root, "baseline.txt")
        baselineFile.writeText(baseline.entries.joinToString("\n") { "${it.key}=${it.value}" })

        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val error =
            try {
                checkFileSizes(
                    root,
                    baselineFile,
                    SOFT_SOURCE_LINES,
                    HARD_SOURCE_LINES,
                    violations::add,
                    warnings::add,
                )
                null
            } catch (exception: IllegalStateException) {
                exception
            }
        return Result(violations, warnings, error)
    }
}
