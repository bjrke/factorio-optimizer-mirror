package de.bjrke.factorio.optimizer.buildlogic

import java.io.File

/** Above this line count a Kotlin source file warns, so an agent can see what to split next. */
const val SOFT_SOURCE_LINES = 300

/** The hard line budget for one Kotlin source file; a file over it must be split. */
const val HARD_SOURCE_LINES = 500

/**
 * Fails when a Kotlin file under [sourceRoot] outgrows [hardLines], unless [baselineFile] lists it.
 * The baseline is a strict ratchet: an entry must match the file's current line count, so a file
 * that shrinks has to lower its entry and a file that reaches the hard budget has to drop it. A
 * file over [softLines] but within the hard budget only warns, through [warn].
 */
fun checkFileSizes(
    sourceRoot: File,
    baselineFile: File,
    softLines: Int = SOFT_SOURCE_LINES,
    hardLines: Int = HARD_SOURCE_LINES,
    report: (String) -> Unit = {},
    warn: (String) -> Unit = {},
) {
    val baseline =
        if (!baselineFile.exists()) emptyMap()
        else
            baselineFile
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { line ->
                    line.substringBefore('=').trim() to line.substringAfter('=').trim().toInt()
                }

    val sizes =
        sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath to it.readLines().size }
            .toList()

    val known = sizes.toMap()
    val violations = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    for ((path, lines) in sizes.sortedBy { it.first }) {
        val allowed = baseline[path]
        if (lines > hardLines) {
            when {
                allowed == null ->
                    violations += "$path has $lines lines; split it (hard budget $hardLines)"
                lines > allowed -> violations += "$path grew to $lines lines (baseline $allowed)"
                lines < allowed ->
                    violations += "$path shrank to $lines lines; lower its baseline from $allowed"
            }
        } else if (lines > softLines) {
            warnings += "$path has $lines lines; over the soft budget $softLines"
        }
    }

    val stale =
        baseline.keys
            .filter { it !in known || known.getValue(it) <= hardLines }
            .sorted()
            .map { "$it no longer needs a baseline entry; remove it" }

    warnings.forEach(warn)
    if (violations.isNotEmpty() || stale.isNotEmpty()) {
        (violations + stale).forEach(report)
        error("Kotlin source files exceed the line budget")
    }
}
