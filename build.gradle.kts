plugins { id("com.diffplug.spotless") version "5.12.5" }

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle { ktfmt().kotlinlangStyle() }
}

configure(subprojects) {
    apply(plugin = "com.diffplug.spotless")

    repositories { mavenCentral() }

    spotless { java { googleJavaFormat().aosp() } }
}
