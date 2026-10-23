import de.bjrke.factorio.optimizer.buildlogic.dumpPrototypes
import de.bjrke.factorio.optimizer.buildlogic.sanitizePrototypes
import de.bjrke.factorio.optimizer.buildlogic.updateImages
import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    kotlin("multiplatform") version libs.versions.kotlin
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "de.bjrke.factorio"

version = "1.0"

repositories { mavenCentral() }

private fun gitRevParse(vararg args: String) =
    providers
        .exec { commandLine("git", "rev-parse", *args) }
        .standardOutput
        .asText
        .map(String::trim)
        .orElse("unknown")
        .get()

private val generatedKotlinDir = layout.buildDirectory.dir("generated/commonMain/kotlin")
private val generatedResourcesDir = layout.buildDirectory.dir("generated/jsMain/resources")

val generateBuildInfo =
    tasks.register<Copy>("generateBuildInfo") {
        group = "build"
        description = "Generates BuildInfo.kt (build date, git commit, branch) into commonMain"
        from("buildSrc/templates")
        rename("BuildInfo.kt.template", "BuildInfo.kt")
        into(generatedKotlinDir.map { it.dir("de/bjrke/factorio/optimizer") })
        expand(
            mapOf(
                "date" to Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                "commit" to
                    (System.getenv("CI_COMMIT_SHORT_SHA") ?: gitRevParse("--short", "HEAD")),
                "branch" to
                    (System.getenv("CI_COMMIT_REF_NAME") ?: gitRevParse("--abbrev-ref", "HEAD")),
            ),
        )
    }

val generateLicenseResource =
    tasks.register<Copy>("generateLicenseResource") {
        group = "build"
        description = "Copies LICENSE into the jsMain resources so the web UI can display it"
        from("LICENSE")
        into(generatedResourcesDir)
        rename("LICENSE", "LICENSE.txt")
    }

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
        commonMain {
            kotlin.srcDir(generateBuildInfo.map { generatedKotlinDir.get().asFile })
            dependencies { implementation(libs.kotlinx.serialization.json) }
        }

        commonTest { dependencies { implementation(kotlin("test")) } }

        jsMain {
            resources.srcDir(generateLicenseResource.map { generatedResourcesDir.get().asFile })
            dependencies {
                implementation(dependencies.platform(libs.kotlin.wrappers.bom))
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-emotion-react
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion-react")
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-tanstack-react-router
                implementation("org.jetbrains.kotlin-wrappers:kotlin-tanstack-react-router")
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
                implementation(npm("@testing-library/react", "16.3.3"))
                implementation(npm("@testing-library/dom", "10.4.2"))
            }
        }

        jvmTest { dependencies { implementation(kotlin("test")) } }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    reports { html.required.set(true) }
}

// Detekt's KMP support registers the type-resolved `detektMainJvm`/`detektTestJvm`
// tasks but does not wire them into `check`, so do that explicitly. Those two analyse
// commonMain/commonTest with jvmMain/jvmTest; the plugin's plain `detekt` pass over all
// of `src` is dropped from `check` because it repeats that work, and the JS sets get
// their own light per-source-set tasks instead. `detekt` still exists for a one-off
// whole-tree run, so `check` now analyses each source set exactly once.
tasks.named("check") {
    dependsOn("detektMainJvm", "detektTestJvm", "detektJsMainSourceSet", "detektJsTestSourceSet")
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
        targetExclude("buildSrc/build/**")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle { ktfmt().kotlinlangStyle() }
}

detekt {
    source.setFrom(files("src"))
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
}

private val factorioDir: File
    get() =
        File(
            System.getProperty("user.home"),
            ".steam/steam/steamapps/common/Factorio",
        )

private val imgDir = "src/jsMain/resources/icons"
private val prototypesDir = "src/commonMain/resources/prototypes"
private val prototypeDumpDir = layout.buildDirectory.dir("prototype-dump").get().asFile

tasks.register("updateImages") {
    group = "factorio"
    description =
        "copies and optimizes new or significantly changed icons from the factorio steam installation"

    val iconsDir = project.rootDir.resolve(imgDir)
    val sources =
        project.fileTree(factorioDir.resolve("data")) {
            include("**/graphics/icons/**/*.png")
        }

    doLast {
        updateImages(iconsDir, sources)
    }
}

tasks.register("dumpPrototypes") {
    group = "factorio"
    description =
        "dumps data.raw for the base game and the Space Age DLC mods from the factorio steam installation"

    val outputDir = project.rootDir.resolve(prototypesDir)
    val binary = factorioDir.resolve("bin/x64/factorio")

    doLast {
        dumpPrototypes(binary, prototypeDumpDir, outputDir)
    }
}

tasks.register("sanitizePrototypes") {
    group = "factorio"
    description = "strips the prototype dumps down to the data the optimizer uses"

    val outputDir = project.rootDir.resolve(prototypesDir)

    doLast {
        sanitizePrototypes(prototypeDumpDir, outputDir)
    }
}
