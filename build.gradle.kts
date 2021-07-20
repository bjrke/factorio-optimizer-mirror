import javax.imageio.ImageIO

plugins {
    kotlin("multiplatform") version "1.5.0"
    id("com.diffplug.spotless") version "5.12.5"
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
        val kotlinVersionSuffix = "-pre.204-kotlin-1.5.0"
        implementation("org.jetbrains.kotlin-wrappers:kotlin-styled:5.3.0$kotlinVersionSuffix")
        implementation(
            "org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:5.2.0$kotlinVersionSuffix"
        )
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react-redux:7.2.3$kotlinVersionSuffix")
    }
    sourceSets["jsTest"].dependencies { implementation(kotlin("test-js")) }
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
