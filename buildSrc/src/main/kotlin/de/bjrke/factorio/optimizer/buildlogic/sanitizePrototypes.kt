package de.bjrke.factorio.optimizer.buildlogic

import java.io.File
import org.gradle.api.logging.Logging

private val LOGGER = Logging.getLogger("de.bjrke.factorio.optimizer.buildlogic.sanitizePrototypes")

fun sanitizePrototypes(rawDir: File, outputDir: File) {
    outputDir.mkdirs()

    for (variant in listOf("base", "space-age")) {
        val rawSource = rawDir.resolve("$variant.json")
        val target = outputDir.resolve("$variant.json")

        when {
            rawSource.exists() -> rawSource.copyTo(target, overwrite = true)
            !target.exists() -> {
                LOGGER.warn("Skipping missing $target")
                continue
            }
        }

        sanitizePrototypeDump(target)
        LOGGER.lifecycle("Sanitized $target (${target.length()} bytes)")
    }
}
