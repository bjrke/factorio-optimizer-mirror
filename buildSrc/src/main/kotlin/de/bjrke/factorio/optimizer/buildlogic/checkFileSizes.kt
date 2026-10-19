package de.bjrke.factorio.optimizer.buildlogic

import java.io.File

/** The line budget for one Kotlin source file, so an agent never has to read a huge module. */
const val MAX_SOURCE_LINES = 700

/**
 * Fails when a Kotlin file under [sourceRoot] outgrows [maxLines], unless [baselineFile] lists it.
 * The baseline is a strict ratchet: an entry must match the file's current line count, so a file
 * that shrinks has to lower its entry and a file that reaches the budget has to drop it.
 */
fun checkFileSizes(
    sourceRoot: File,
    baselineFile: File,
    maxLines: Int = MAX_SOURCE_LINES,
    report: (String) -> Unit = {},
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
    val violations =
        sizes
            .mapNotNull { (path, lines) ->
                val allowed = baseline[path]
                when {
                    lines <= maxLines -> null
                    allowed == null -> "$path has $lines lines; split it (budget $maxLines)"
                    lines > allowed -> "$path grew to $lines lines (baseline $allowed)"
                    lines < allowed ->
                        "$path shrank to $lines lines; lower its baseline from $allowed"
                    else -> null
                }
            }
            .sorted()

    val stale =
        baseline.keys
            .filter { it !in known || known.getValue(it) <= maxLines }
            .sorted()
            .map { "$it no longer needs a baseline entry; remove it" }

    if (violations.isNotEmpty() || stale.isNotEmpty()) {
        (violations + stale).forEach(report)
        error("Kotlin source files exceed the line budget")
    }
}
