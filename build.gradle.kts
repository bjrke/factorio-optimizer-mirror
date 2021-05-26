plugins {
    kotlin("js") version "1.5.0"
}

group = "de.bjrke.factorio"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    val kotlinVersionSuffix = "-pre.204-kotlin-1.5.0"
    implementation("org.jetbrains.kotlin-wrappers", "kotlin-styled", "5.3.0$kotlinVersionSuffix")
    implementation("org.jetbrains.kotlin-wrappers", "kotlin-react-router-dom", "5.2.0$kotlinVersionSuffix")
    implementation("org.jetbrains.kotlin-wrappers", "kotlin-react-redux", "7.2.3$kotlinVersionSuffix")

    testImplementation(kotlin("test-js"))
}

kotlin {
    js(LEGACY) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
}

task("copyImages", Copy::class) {
    from(fileTree("${System.getProperty("user.home")}/.steam/steam/steamapps/common/Factorio/data/base/graphics") {
        include("icons/**/*.png")
    }.files)

    duplicatesStrategy = DuplicatesStrategy.WARN
    into("src/main/resources/icons/")
}
