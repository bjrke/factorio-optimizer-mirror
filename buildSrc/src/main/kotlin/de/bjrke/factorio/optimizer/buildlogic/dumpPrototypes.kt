package de.bjrke.factorio.optimizer.buildlogic

import java.io.File
import org.gradle.api.logging.Logging

private val LOGGER = Logging.getLogger("de.bjrke.factorio.optimizer.buildlogic.dumpPrototypes")

fun dumpPrototypes(binary: File, rawDir: File, outputDir: File) {
    check(binary.exists()) { "factorio executable not found at $binary" }

    rawDir.deleteRecursively()
    outputDir.mkdirs()

    fun dump(variant: String) {
        val spaceAge = variant == "space-age"
        val dir = rawDir.resolve(variant).also { it.mkdirs() }
        val modsDir = dir.resolve("mods").also { it.mkdirs() }
        val configIni = dir.resolve("config.ini")
        configIni.writeText(
            """
                |[path]
                |read-data=__PATH__executable__/../../data
                |write-data=${dir.absolutePath}
                """
                .trimMargin()
        )
        val modList = buildString {
            append("""{"mods":[{"name":"base","enabled":true}""")
            for (mod in listOf("elevated-rails", "quality", "recycler", "space-age")) {
                append(""",{"name":"$mod","enabled":$spaceAge}""")
            }
            append("]}")
        }
        modsDir.resolve("mod-list.json").writeText(modList)

        val exitCode =
            ProcessBuilder(
                    binary.absolutePath,
                    "--config",
                    configIni.absolutePath,
                    "--mod-directory",
                    modsDir.absolutePath,
                    "--disable-audio",
                    "--dump-data",
                )
                .inheritIO()
                .start()
                .waitFor()
        check(exitCode == 0) { "factorio --dump-data failed for '$variant' (exit $exitCode)" }

        val dumpFile = dir.resolve("script-output/data-raw-dump.json")
        check(dumpFile.exists()) { "expected dump at $dumpFile" }

        val rawTarget = rawDir.resolve("$variant.json")
        dumpFile.copyTo(rawTarget, overwrite = true)

        val target = outputDir.resolve("$variant.json")
        rawTarget.copyTo(target, overwrite = true)
        sanitizePrototypeDump(target)
        LOGGER.lifecycle("Wrote $target (${target.length()} bytes)")
    }

    dump("base")
    dump("space-age")
}
