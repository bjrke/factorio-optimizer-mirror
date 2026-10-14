package de.bjrke.factorio.optimizer

/** The footer build line, e.g. `build 2026-10-10T10:49:10Z · ace16532 · main`. */
fun buildInfoLine(date: String, commit: String, branch: String) = "build $date · $commit · $branch"
