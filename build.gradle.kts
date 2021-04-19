plugins {
    kotlin("js") version "1.4.32"
}

group = "de.bjrke.factorio"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers")
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    val kotlinVersionSuffix = "-pre.153-kotlin-1.4.32"
    implementation("org.jetbrains", "kotlin-styled", "5.2.3$kotlinVersionSuffix")
    implementation("org.jetbrains", "kotlin-react-router-dom", "5.2.0$kotlinVersionSuffix")
    implementation("org.jetbrains", "kotlin-react-redux", "7.2.3$kotlinVersionSuffix")

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
