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
    testImplementation(kotlin("test-js"))
    val kotlinVersionSuffix = "-pre.113-kotlin-1.4.0"
    implementation("org.jetbrains", "kotlin-styled", "1.0.0$kotlinVersionSuffix")
    implementation("org.jetbrains", "kotlin-react-router-dom", "5.1.2$kotlinVersionSuffix")
    implementation("org.jetbrains", "kotlin-react-redux", "5.0.7$kotlinVersionSuffix")
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
