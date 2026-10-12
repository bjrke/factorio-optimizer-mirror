import de.bjrke.factorio.optimizer.build.sanitizePrototypeDump
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs

plugins {
    kotlin("multiplatform") version libs.versions.kotlin
    alias(libs.plugins.spotless)
}

group = "de.bjrke.factorio"

version = "1.0"

repositories { mavenCentral() }

kotlin {
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(25)) }

    js {
        binaries.executable()
        browser { commonWebpackConfig { cssSupport { enabled.set(true) } } }
    }

    jvm {
        compilations {
            val main = getByName("main")
            tasks.register<Jar>("buildFatJar") {
                group = "build"
                description = "builds the fat jar containing the CLI version of the optimizer"
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                dependsOn(main.compileAllTaskName)
                manifest {
                    attributes["Main-Class"] = "de.bjrke.factorio.optimizer.jvm.CliOptimizerKt"
                }
                from(
                    main.runtimeDependencyFiles.map {
                        if (it.isDirectory) it else zipTree(it)
                    },
                    main.output.classesDirs,
                    main.output.resourcesDir,
                )
                archiveBaseName.set(project.name)
            }
        }
    }

    sourceSets {
        commonMain { dependencies { implementation(libs.kotlinx.serialization.json) } }

        commonTest { dependencies { implementation(kotlin("test")) } }

        jsMain {
            dependencies {
                implementation(dependencies.platform(libs.kotlin.wrappers.bom))
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-emotion-react
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion-react")
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-tanstack-react-router
                implementation("org.jetbrains.kotlin-wrappers:kotlin-tanstack-react-router")
            }
        }

        jsTest { dependencies { implementation(kotlin("test-js")) } }

        jvmTest { dependencies { implementation(kotlin("test")) } }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    reports { html.required.set(true) }
}

tasks.register<JavaExec>("run") {
    group = "run"
    description = "run the CLI version of the factorio optimizer"
    mainClass.set("de.bjrke.factorio.optimizer.jvm.CliOptimizerKt")
    kotlin {
        val main = targets["jvm"].compilations["main"]
        dependsOn(main.compileAllTaskName)
        classpath({ main.output.allOutputs.files }, { configurations["jvmRuntimeClasspath"] })
    }
}

spotless {
    kotlin {
        target("src/**/*.kt", "buildSrc/**/*.kt")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle { ktfmt().kotlinlangStyle() }
}

val imgDir = "src/jsMain/resources/icons"
val imageChangedThreshold = 200000
val factorioDir by lazy {
    "${System.getProperty("user.home")}/.steam/steam/steamapps/common/Factorio"
}

tasks.register<Exec>("updateImages") {
    group = "factorio"
    description =
        "copies and optimizes new or significantly changed icons from the factorio steam installation"
    commandLine = listOf("sh")

    val iconsDir = rootDir.resolve(imgDir)
    workingDir = iconsDir
    val sources = fileTree("$factorioDir/data") { include("**/graphics/icons/**/*.png") }

    doFirst {
        iconsDir.mkdirs()

        fun significantlyChanged(img: BufferedImage, target: File): Boolean {
            if (!target.exists()) return true
            val old = ImageIO.read(target) ?: return true
            if (old.width != 64 || old.height != 64) return true
            var difference = 0
            for (y in 0 until 64) {
                for (x in 0 until 64) {
                    val a = img.getRGB(x, y)
                    val b = old.getRGB(x, y)
                    val aAlpha = a ushr 24
                    val bAlpha = b ushr 24
                    difference +=
                        maxOf(
                            abs(
                                (a shr 16 and 0xff) * aAlpha / 255 -
                                    (b shr 16 and 0xff) * bAlpha / 255
                            ),
                            abs(
                                (a shr 8 and 0xff) * aAlpha / 255 -
                                    (b shr 8 and 0xff) * bAlpha / 255
                            ),
                            abs((a and 0xff) * aAlpha / 255 - (b and 0xff) * bAlpha / 255),
                            abs(aAlpha - bAlpha),
                        )
                }
            }
            return difference > imageChangedThreshold
        }

        val chosen = LinkedHashMap<String, Pair<File, BufferedImage>>()
        sources.files
            .sortedBy { it.path }
            .forEach { source ->
                val img = ImageIO.read(source)
                if (img == null || img.width != 120 || img.height != 64) return@forEach
                val previous = chosen.putIfAbsent(source.name, source to img)
                if (previous != null) {
                    logger.warn(
                        "Duplicate icon name '${source.name}': using ${previous.first.path}, ignoring ${source.path}"
                    )
                }
            }

        val changedNames =
            chosen
                .filter { (name, image) ->
                    significantlyChanged(image.second, File(iconsDir, name))
                }
                .keys
                .toList()
        changedNames.forEach { name ->
            chosen.getValue(name).first.copyTo(File(iconsDir, name), overwrite = true)
        }
        if (changedNames.isEmpty()) {
            logger.lifecycle("No significantly changed icons found")
        }

        val files = changedNames.joinToString(" ") { "'$it'" }
        standardInput =
            if (changedNames.isEmpty()) {
                "".byteInputStream()
            } else {
                """
                mogrify -crop 64x64+0+0 $files
                chmod 664 $files
                pngquant --strip --speed 1 --ext=.png --force --skip-if-larger $files
                optipng -o7 -zm1-9 $files
                """
                    .trimIndent()
                    .byteInputStream()
            }
    }
}

tasks.register("dumpPrototypes") {
    group = "factorio"
    description =
        "dumps data.raw for the base game and the Space Age DLC mods from the factorio steam installation"

    val outputDir = rootDir.resolve("src/commonMain/resources/prototypes")
    val workDir = layout.buildDirectory.dir("prototype-dump")

    doLast {
        val binary = file("$factorioDir/bin/x64/factorio")
        check(binary.exists()) { "factorio executable not found at $binary" }

        val work = workDir.get().asFile
        work.deleteRecursively()
        outputDir.mkdirs()

        fun dump(variant: String) {
            val spaceAge = variant == "space-age"
            val dir = work.resolve(variant).also { it.mkdirs() }
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
            val target = outputDir.resolve("$variant.json")
            dumpFile.copyTo(target, overwrite = true)
            sanitizePrototypeDump(target)
            logger.lifecycle("Wrote $target (${target.length()} bytes)")
        }

        dump("base")
        dump("space-age")
    }
}

tasks.register("sanitizePrototypes") {
    group = "factorio"
    description = "strips the prototype dumps down to the data the optimizer uses"

    val outputDir = rootDir.resolve("src/commonMain/resources/prototypes")

    doLast {
        for (variant in listOf("base", "space-age")) {
            val target = outputDir.resolve("$variant.json")
            if (!target.exists()) {
                logger.warn("Skipping missing $target")
                continue
            }
            sanitizePrototypeDump(target)
            logger.lifecycle("Sanitized $target (${target.length()} bytes)")
        }
    }
}
