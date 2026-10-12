# AGENTS.md

Factorio-Optimizer is a Kotlin Multiplatform (Gradle 9.7.1, Kotlin 2.4.20) app that optimizes Factorio factory layouts. It compiles to a React web app (JS) and a CLI (JVM).

## Commands

- `./gradlew jsBrowserDevelopmentRun --continuous` / `jsBrowserProductionRun --continuous` — dev server for the web UI
- `./gradlew jvmTest` — run kotlin-test suites (commonTest + jvmTest); add `--tests "*RecipeParserTest*"` to filter
- `./gradlew check` — formatting check + all tests. CI runs `gradle --no-daemon clean check jsBrowserDistribution`, which includes `jsBrowserTest` (the JS web tests run in a real headless Chrome)
- `./gradlew spotlessApply` — fix formatting (Spotless/ktfmt `kotlinlangStyle`; covers `src/**/*.kt` and `build.gradle.kts`)
- `./gradlew run` — run the CLI (`CliOptimizerKt`); `./gradlew buildFatJar` produces a runnable fat jar

## Prerequisites

- JDK 25 is required. `gradle.properties` sets `org.gradle.java.installations.auto-download=false`, so a local JDK 25 must exist; otherwise the build fails with a cryptic error (just the installed version string). CI uses the external image `registry.gitlab.com/bjrke/docker-gradle-chrome:latest` (gradle JDK 25 + Google Chrome for `jsBrowserTest`).

## Code layout (Kotlin Multiplatform)

- `src/commonMain` — shared logic: recipe parser (`recipeParser.kt`), the optimizer (`optimizer.kt`), model, and the dumped `prototypes/` data
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, `styled-next`, `react-router-dom`); entry point `js/index.kt`; `js/business/loadRecipe.kt` pulls the prototype dumps via `@JsModule` + webpack config in `webpack.config.d/`
- `src/jvmMain` — CLI entry `jvm/CliOptimizer.kt`; `jvm/business/loadRecipe.kt` loads recipes from the classpath resource
- `src/commonTest` — shared kotlin-test suites run by both JVM and JS test tasks

The same `commonMain` code compiles to both targets, so keep it platform-neutral. Platform differences belong in target-specific `loadRecipe.kt`/`index.kt` files.

## Git workflow

- Before making changes, always ensure you are on a feature branch: if the
  current branch is `main` (or not already a feature branch), branch off from
  `main` first.
- Branch names contain the author, e.g. `<author>/<topic>`.
- Commit after the work is done (no explicit user request needed). Headline is
  limited to 50 chars; the body is wrapped at 72 chars.

## Generated data — do not hand-edit

- `src/commonMain/resources/prototypes/{base,space-age}.json` are the `data.raw` dumps produced by `factorio --dump-data` for the base game and for base + the Space Age DLC mods. Regenerate with `./gradlew dumpPrototypes`, which runs the Factorio data stage with an isolated config/mod-list (requires a Factorio Steam install). The dump is resolved by the game itself, so it already includes all `data-updates.lua`/`data-final-fixes.lua` changes; the parser reads only the prototype types it needs and ignores the rest. The web UI and CLI use the base dump, or the Space Age dump when Space Age is active; parsed results are cached in `RecipeProvider`.
- `src/jsMain/resources/icons/*.png` are extracted from a Linux Factorio Steam install (120x64 crops) and optimized. `./gradlew updateImages` requires `imagemagick optipng pngquant` installed; it only copies and optimizes icons whose top-left 64x64 region changed significantly. Don't add icons manually.
