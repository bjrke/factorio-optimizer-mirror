import javax.imageio.ImageIO

plugins {
    kotlin("js") version "1.5.0"
    id("com.diffplug.spotless") version "5.12.5"
}

group = "de.bjrke.factorio"

version = "1.0"

repositories { mavenCentral() }

dependencies {
    val kotlinVersionSuffix = "-pre.204-kotlin-1.5.0"
    implementation("org.jetbrains.kotlin-wrappers", "kotlin-styled", "5.3.0$kotlinVersionSuffix")
    implementation(
        "org.jetbrains.kotlin-wrappers",
        "kotlin-react-router-dom",
        "5.2.0$kotlinVersionSuffix"
    )
    implementation(
        "org.jetbrains.kotlin-wrappers",
        "kotlin-react-redux",
        "7.2.3$kotlinVersionSuffix"
    )

    testImplementation(kotlin("test-js"))
}

kotlin {
    js(LEGACY) {
        binaries.executable()
        browser { commonWebpackConfig { cssSupport.enabled = true } }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle { ktfmt().kotlinlangStyle() }
}

val imgDir = "src/main/resources/icons"
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
