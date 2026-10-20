package de.bjrke.factorio.optimizer.buildlogic

import java.io.File

/** Above this line count a Kotlin source file reports a hint, the first sign to split it soon. */
const val SUPER_SOFT_SOURCE_LINES = 300

/** Above this line count a Kotlin source file warns, so an agent can see what to split next. */
const val SOFT_SOURCE_LINES = 400

/** The hard line budget for one Kotlin source file; a file over it must be split. */
const val HARD_SOURCE_LINES = 500

/**
 * Reports Kotlin files under [sourceRoot] by how far they outgrow the line budgets: over
 * [superSoftLines] through [info], over [softLines] through [warn], and over [hardLines] through
 * [report] followed by an error that fails the build.
 */
fun checkFileSizes(
    sourceRoot: File,
    superSoftLines: Int = SUPER_SOFT_SOURCE_LINES,
    softLines: Int = SOFT_SOURCE_LINES,
    hardLines: Int = HARD_SOURCE_LINES,
    report: (String) -> Unit = {},
    warn: (String) -> Unit = {},
    info: (String) -> Unit = {},
) {
    val sizes =
        sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath to it.readLines().size }
            .toList()

    val violations = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val infos = mutableListOf<String>()
    for ((path, lines) in sizes.sortedBy { it.first }) {
        when {
            lines > hardLines ->
                violations += "$path has $lines lines; split it (hard budget $hardLines)"
            lines > softLines ->
                warnings += "$path has $lines lines; over the soft budget $softLines"
            lines > superSoftLines ->
                infos += "$path has $lines lines; over the super-soft budget $superSoftLines"
        }
    }

    infos.forEach(info)
    warnings.forEach(warn)
    if (violations.isNotEmpty()) {
        violations.forEach(report)
        error("Kotlin source files exceed the line budget")
    }
}
