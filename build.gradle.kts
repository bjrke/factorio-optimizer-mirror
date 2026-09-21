import javax.imageio.ImageIO

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
    tasks.register<Copy>("copyImages") {
        description = "copies the images from the factorio steam installation"
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

tasks.register<Exec>("optimizeImages") {
    description = "optimzes the freshly copied images"
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

tasks.register<Copy>("copyRecipes") {
    description = "copies the reciepes from the factorio steam installation"
    from(file("$factorioDir/data/base/prototypes/recipe.lua"))
    into("src/commonMain/resources/recipes")
}
