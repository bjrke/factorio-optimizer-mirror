import javax.imageio.ImageIO

plugins {
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-gradle-plugin
    kotlin("multiplatform") version "1.6.0"
    // https://mvnrepository.com/artifact/com.diffplug.spotless/spotless-plugin-gradle
    id("com.diffplug.spotless") version "6.0.0"
}

group = "de.bjrke.factorio"

version = "1.0"

repositories { mavenCentral() }

kotlin {
    js(LEGACY) {
        binaries.executable()
        browser { commonWebpackConfig { cssSupport.enabled = true } }
    }
    sourceSets["jsMain"].dependencies {
        val kotlinVersionSuffix = "-pre.267-kotlin-1.6.0"
        // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-styled
        implementation("org.jetbrains.kotlin-wrappers:kotlin-styled:5.3.3$kotlinVersionSuffix")
        // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-react-router-dom
        implementation(
            "org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:5.3.0$kotlinVersionSuffix"
        )
        // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-react-redux
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react-redux:7.2.6$kotlinVersionSuffix")
    }
    sourceSets["jsTest"].dependencies { implementation(kotlin("test-js")) }

    jvm()
}

task("run", JavaExec::class) {
    main = "de.bjrke.factorio.optimizer.jvm.CliOptimizerKt"
    kotlin {
        val main = targets["jvm"].compilations["main"]
        dependsOn(main.compileAllTaskName)
        classpath({ main.output.allOutputs.files }, { configurations["jvmRuntimeClasspath"] })
    }
    systemProperty("java.awt.headless", "true")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle { ktfmt().kotlinlangStyle() }
}

val imgDir = "src/jsMain/resources/icons"
val copyImages =
    task("copyImages", Copy::class) {
        from(
            fileTree(
                    "${System.getProperty("user.home")}/.steam/steam/steamapps/common/Factorio/data/base/graphics"
                ) { include("icons/**/*.png") }
                .files
        )

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
