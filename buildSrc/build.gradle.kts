plugins { `kotlin-dsl` }

repositories { mavenCentral() }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
