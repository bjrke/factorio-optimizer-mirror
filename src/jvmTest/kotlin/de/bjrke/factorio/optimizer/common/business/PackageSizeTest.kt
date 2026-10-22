package de.bjrke.factorio.optimizer.common.business

import de.bjrke.test.assertTable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keeps every package of `commonMain` inside the project's budget: a package that grows past it is
 * the signal to cut it along a measured seam, not to add a file. detekt has no package-size rule,
 * so the check walks the source tree.
 *
 * The file count is the budget: more than 10 files is warned (printed, the build still passes),
 * more than 15 files fails. There is no line budget; file length is already capped per file by
 * `LargeClass` in `config/detekt/detekt.yml` (see `knowledge/decisions.md`, 2026-10-22).
 */
class PackageSizeTest {

    @Test
    fun everyPackageStaysWithinTheFileBudget() {
        assertTable(packages()) { (name, files) ->
            assertTrue(files.size <= MAX_FILES, "$name has ${files.size} files (max $MAX_FILES)")
        }
    }

    @Test
    fun packagesAboveTheSoftFileBudgetAreReported() {
        val crowded =
            packages().filter { (_, files) -> files.size > WARN_FILES }.map { (name, _) -> name }
        println("packages above $WARN_FILES files: ${crowded.ifEmpty { listOf("none") }}")
    }

    /** `(package path relative to common/, its *.kt files)`, one entry per package directory. */
    private fun packages(): List<Pair<String, List<File>>> {
        val root = commonMainDirectory()
        return root
            .walkTopDown()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val files =
                    dir.listFiles { file -> file.extension == "kt" }.orEmpty().sortedBy { it.name }
                if (files.isEmpty()) null else dir.relativeTo(root).path to files
            }
            .sortedBy { it.first }
            .toList()
    }

    /**
     * Locates `src/commonMain/kotlin/…/common` from the test working directory, walking up so the
     * test also runs when Gradle or an IDE launches it from a subdirectory.
     */
    private fun commonMainDirectory(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/commonMain/kotlin/de/bjrke/factorio/optimizer/common")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("could not locate the common package root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val WARN_FILES = 10
        const val MAX_FILES = 15
    }
}
