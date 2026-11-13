import de.bjrke.factorio.optimizer.buildlogic.dumpPrototypes
import de.bjrke.factorio.optimizer.buildlogic.sanitizePrototypes
import de.bjrke.factorio.optimizer.buildlogic.updateImages
import org.gradle.api.Transformer
import org.gradle.api.provider.MapProperty

plugins {
    kotlin("multiplatform") version libs.versions.kotlin
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "de.bjrke.factorio"

version = "1.0"

repositories { mavenCentral() }

/**
 * Substitutes `${key}` placeholders while the copy runs.
 *
 * It reads a [MapProperty] instead of closing over the providers, because a `filter {}` lambda
 * captures the build script and the configuration cache rejects script object references.
 */
class ReplaceValues(private val values: MapProperty<String, String>) :
    Transformer<String?, String> {
    override fun transform(line: String): String {
        var result = line
        values.get().forEach { (key, value) -> result = result.replace("\${$key}", value) }
        return result
    }
}

// CI starts from a fresh checkout on every pipeline and must not restore task
// results from an earlier commit, so every compile, test and analysis really
// runs. Only `buildSrc` (a separate build, unaffected by this block) may use the
// build cache there. Local builds keep their cache, which is enabled in
// `gradle.properties` and not disabled by this switch.
val isCi = providers.gradleProperty("CI").isPresent

tasks.withType<Task>().configureEach { if (isCi) outputs.cacheIf { false } }

/**
 * `git rev-parse …` as a provider, so it runs when a task needs the value, not while configuring.
 */
private fun gitRevParse(vararg args: String) =
    providers
        .exec { commandLine("git", "rev-parse", *args) }
        .standardOutput
        .asText
        .map(String::trim)

/** The commit's timestamp: deterministic per commit, which the configuration cache relies on. */
private val buildDate =
    providers
        .environmentVariable("CI_COMMIT_TIMESTAMP")
        .orElse(
            providers
                .exec { commandLine("git", "show", "-s", "--format=%cI", "HEAD") }
                .standardOutput
                .asText
                .map(String::trim),
        )
        .orElse("unknown")

private val buildCommit =
    providers
        .environmentVariable("CI_COMMIT_SHORT_SHA")
        .orElse(gitRevParse("--short", "HEAD"))
        .orElse("unknown")

private val buildBranch =
    providers
        .environmentVariable("CI_COMMIT_REF_NAME")
        .orElse(gitRevParse("--abbrev-ref", "HEAD"))
        .orElse("unknown")

private val generatedKotlinDir = layout.buildDirectory.dir("generated/commonMain/kotlin")
private val generatedResourcesDir = layout.buildDirectory.dir("generated/jsMain/resources")

// The values are inputs rather than `expand` arguments, which would be read while
// configuring: a per-commit `System.getenv` or a frozen `Instant.now` would make
// the configuration cache useless and leave a stale BuildInfo.kt behind.
val buildInfo =
    objects.mapProperty(String::class.java, String::class.java).apply {
        put("date", buildDate)
        put("commit", buildCommit)
        put("branch", buildBranch)
    }

val generateBuildInfo =
    tasks.register<Copy>("generateBuildInfo") {
        group = "build"
        description = "Generates BuildInfo.kt (commit date, git commit, branch) into commonMain"
        from("buildSrc/templates")
        rename("BuildInfo.kt.template", "BuildInfo.kt")
        into(generatedKotlinDir.map { it.dir("de/bjrke/factorio/optimizer") })
        inputs.property("values", buildInfo)
        filter(ReplaceValues(buildInfo))
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
                // Minifies the production bundle; see webpack.config.d/02-esbuild-minify.js.
                implementation(npm("esbuild-loader", "4.5.0"))
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

private val jvmProfileDir = layout.buildDirectory.dir("profiles/jvmTest")

private fun Test.configureRepositoryTest() {
    useJUnitPlatform()

    reports { html.required.set(true) }
}

tasks.named<Test>("jvmTest", Test::configureRepositoryTest)

tasks.register<Test>("jvmTestProfile") {
    group = "profiling"
    description =
        "runs jvmTest with a JDK Flight Recorder CPU profile; the merged recording lands in build/profiles/jvmTest"

    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    configureRepositoryTest()

    // A single fork keeps every test case in one recording, so the profile is merged by
    // construction.
    maxParallelForks = 1
    outputs.upToDateWhen { false }

    val profileDir = jvmProfileDir.get().asFile
    doFirst {
        profileDir.deleteRecursively()
        profileDir.mkdirs()
    }
    jvmArgs(
        "-XX:StartFlightRecording=filename=${profileDir.resolve("jvmTest-%p.jfr")},settings=profile,dumponexit=true",
        "-XX:FlightRecorderOptions=stackdepth=1024",
    )
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
