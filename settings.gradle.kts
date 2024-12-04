rootProject.name = "factorio-optimizer"

dependencyResolutionManagement {
    repositories { mavenCentral() }

    versionCatalogs {
        create("kotlinWrappers") {
            // https://mvnrepository.com/artifact/org.jetbrains.kotlin-wrappers/kotlin-wrappers-catalog
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:0.0.1-pre.790")
        }
    }
}
