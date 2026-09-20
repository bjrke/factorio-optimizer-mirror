# AGENTS.md

Factorio-Optimizer is a Kotlin Multiplatform (Gradle 9.7.1, Kotlin 2.4.20) app that optimizes Factorio factory layouts. It compiles to a React web app (JS) and a CLI (JVM).

## Commands

- `./gradlew jsBrowserDevelopmentRun --continuous` / `jsBrowserProductionRun --continuous` — dev server for the web UI
- `./gradlew jvmTest` — run kotlin-test suites (commonTest + jvmTest); add `--tests "*RecipeParserTest*"` to filter
- `./gradlew check` — formatting check + all tests. CI runs `gradle --no-daemon clean check jsBrowserDistribution -x jsBrowserTest` (jsBrowserTest is excluded; it needs a browser)
- `./gradlew spotlessApply` — fix formatting (Spotless/ktfmt `kotlinlangStyle`; covers `src/**/*.kt` and `build.gradle.kts`)
- `./gradlew run` — run the CLI (`CliOptimizerKt`); `./gradlew buildFatJar` produces a runnable fat jar

## Prerequisites

- JDK 25 is required. `gradle.properties` sets `org.gradle.java.installations.auto-download=false`, so a local JDK 25 must exist; otherwise the build fails with a cryptic error (just the installed version string). CI uses the `gradle:9.7.1-jdk25` image.

## Code layout (Kotlin Multiplatform)

- `src/commonMain` — shared logic: Lua parser (`LuaParser.kt`), recipe parser (`recipeParser.kt`), the optimizer (`optimizer.kt`), model, and `recipes/recipe.lua` data
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, `styled-next`, `react-router-dom`); entry point `js/index.kt`; `js/business/loadRecipe.kt` pulls recipes via `@JsModule` + webpack config in `webpack.config.d/`
- `src/jvmMain` — CLI entry `jvm/CliOptimizer.kt`; `jvm/business/loadRecipe.kt` loads recipes from the classpath resource
- `src/commonTest` — shared kotlin-test suites run by both JVM and JS test tasks

The same `commonMain` code compiles to both targets, so keep it platform-neutral. Platform differences belong in target-specific `loadRecipe.kt`/`index.kt` files.

## Git workflow

- When on `main`, branch off before making changes.
- Branch names contain the author, e.g. `<author>/<topic>`.
- Commit after the work is done. Headline is limited to 50 chars; the body
  is wrapped at 72 chars.

## Generated data — do not hand-edit

- `src/commonMain/resources/recipes/recipe.lua` is a Lua dump from a Factorio install (base + DLC). Regenerate with `./gradlew copyRecipes`, which copies `~/.steam/steam/steamapps/common/Factorio/data/base/prototypes/recipe.lua` (path is hardcoded in `build.gradle.kts`).
- `src/jsMain/resources/icons/*.png` are extracted from a Linux Factorio Steam install (120x64 crops) and optimized. `./gradlew optimizeImages` requires `imagemagick optipng pngquant` installed. Don't add icons manually.
