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
        group = "factorio"
        description = "copies the icon images from all mods of the factorio steam installation"
        from(fileTree("$factorioDir/data") { include("**/graphics/icons/**/*.png") }.files)

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
    group = "factorio"
    description = "optimizes the freshly copied images"
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

tasks.register("copyRecipes") {
    group = "factorio"
    description = "scrapes recipe files from all mods of the factorio steam installation"

    doLast {
        val recipeDir = file("src/commonMain/resources/recipes")
        recipeDir.listFiles()?.forEach {
            if (it.isDirectory || it.name != "loader.js") it.deleteRecursively()
        }

        val recipePattern = Regex("""type\s*=\s*"recipe"""")
        val dataDir = file("$factorioDir/data")

        val mods =
            dataDir.listFiles { it.isDirectory }?.filter { it.name != "core" }?.sortedBy { it.name }
                ?: emptyList()

        val modRecipeFiles =
            mods
                .associateWith { mod ->
                    mod.walkTopDown()
                        .filter {
                            it.isFile &&
                                it.extension == "lua" &&
                                !it.path.contains("/script/") &&
                                it.name != "data-updates.lua" &&
                                it.name != "data-final-fixes.lua" &&
                                it.readText().contains(recipePattern)
                        }
                        .toList()
                }
                .filterValues { it.isNotEmpty() }
                .entries
                .sortedBy { (mod, recipeFiles) -> mod }

        for ((mod, recipeFiles) in modRecipeFiles) {
            val modDir = recipeDir.resolve(mod.name).also { it.mkdirs() }
            for (recipeFile in recipeFiles) {
                recipeFile.copyTo(modDir.resolve(recipeFile.relativeTo(mod)))
            }
        }

        val modsTxtContent = buildString {
            for ((mod, recipeFiles) in modRecipeFiles) {
                append(mod.name).append("\t")

                Regex(""""dependencies"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(mod.resolve("info.json").takeIf { it.exists() }?.readText() ?: "")
                    .take(1)
                    .flatMap { Regex(""""([^"]+)"""").findAll(it.groupValues[1]) }
                    .map { it.groupValues[1].trim() }
                    .mapNotNull {
                        when {
                            it.isEmpty() -> null
                            it.startsWith("!") -> null // conflict
                            it.startsWith("?") -> null // optional / load order
                            it.startsWith("(") -> null // optional / load order
                            it.startsWith("~") -> null // optional / load order
                            it.startsWith("+") -> null // optional / load order
                            else -> it.substringBefore(" ")
                        }
                    }
                    .joinTo(this, ",")

                append("\t")
                recipeFiles.joinTo(this, ";") {
                    it.relativeTo(dataDir).invariantSeparatorsPath
                }
                append("\n")
            }
        }
        recipeDir.resolve("mods.txt").writeText(modsTxtContent)
    }
}
