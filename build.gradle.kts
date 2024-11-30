import javax.imageio.ImageIO

plugins {
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-gradle-plugin
    kotlin("multiplatform") version "1.7.22"
    // https://mvnrepository.com/artifact/com.diffplug.spotless/spotless-plugin-gradle
    id("com.diffplug.spotless") version "6.25.0"
}

group = "de.bjrke.factorio"

version = "1.0"

repositories { mavenCentral() }

val kotestVersion = "5.5.5"

kotlin {
    js(IR) {
        binaries.executable()
        browser { commonWebpackConfig { cssSupport.enabled = true } }
    }
    jvm {
        withJava()

        compilations {
            val main = getByName("main")
            tasks.register<Jar>("buildFatJar") {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                dependsOn(tasks.build)
                manifest {
                    attributes["Main-Class"] = "de.bjrke.factorio.optimizer.jvm.CliOptimizerKt"
                }
                from(
                    configurations.getByName("runtimeClasspath").map {
                        if (it.isDirectory) it else zipTree(it)
                    },
                    main.output.classesDirs,
                    main.output.resourcesDir
                )
                archiveBaseName.set(project.name)
            }
        }
    }

    sourceSets {
        commonTest {
            dependencies {
                // https://mvnrepository.com/artifact/io.kotest/kotest-framework-engine
                implementation("io.kotest:kotest-framework-engine:$kotestVersion")
                // https://mvnrepository.com/artifact/io.kotest/kotest-framework-datatest
                implementation("io.kotest:kotest-framework-datatest:$kotestVersion")
                // https://mvnrepository.com/artifact/io.kotest/kotest-assertions-core
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
            }
        }

        getByName("jsMain") {
            dependencies {
                val kotlinVersionSuffix = "-pre.451"
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-styled
                implementation(
                    "org.jetbrains.kotlin-wrappers:kotlin-styled:5.3.6$kotlinVersionSuffix"
                )
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-react-router-dom
                implementation(
                    "org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:6.3.0$kotlinVersionSuffix"
                )
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-react-redux
                implementation(
                    "org.jetbrains.kotlin-wrappers:kotlin-react-redux:7.2.6$kotlinVersionSuffix"
                )
            }
        }

        getByName("jsTest") { dependencies { implementation(kotlin("test-js")) } }

        getByName("jvmTest") {
            dependencies {
                // https://mvnrepository.com/artifact/io.kotest/kotest-runner-junit5-jvm
                implementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    reports { html.required.set(true) }
}

task("run", JavaExec::class) {
    mainClass.set("de.bjrke.factorio.optimizer.jvm.CliOptimizerKt")
    kotlin {
        val main = targets["jvm"].compilations["main"]
        dependsOn(main.compileAllTaskName)
        classpath({ main.output.allOutputs.files }, { configurations["jvmRuntimeClasspath"] })
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle { ktfmt().kotlinlangStyle() }
}

val imgDir = "src/jsMain/resources/icons"
val factorioDir by lazy {
    "${System.getProperty("user.home")}/.steam/steam/steamapps/common/Factorio"
}

val copyImages =
    task("copyImages", Copy::class) {
        from(fileTree("$factorioDir/data/base/graphics") { include("icons/**/*.png") }.files)

        duplicatesStrategy = DuplicatesStrategy.WARN
        into(imgDir)
        eachFile {
            val img = ImageIO.read(file)
            if (img.width != 120 || img.height != 64) {
                exclude()
            }
        }
    }

task("optimizeImages", Exec::class) {
    dependsOn(copyImages)
    commandLine = listOf("sh")

    workingDir = rootDir.resolve(imgDir)
    standardInput =
        """
        mogrify -crop 64x64+0+0 *
        chmod 664 *
        pngquant --strip --speed 1 --ext=.png --force --skip-if-larger *
        optipng -o7 -zm1-9 *
        """
            .trimIndent()
            .byteInputStream()
}

task("copyRecipes", Copy::class) {
    from(file("$factorioDir/data/base/prototypes/recipe.lua"))
    into("src/commonMain/resources/recipes")
}
