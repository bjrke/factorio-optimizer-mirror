# AGENTS.md

Factorio-Optimizer is a Kotlin Multiplatform (Gradle 9.7.1, Kotlin 2.4.20) app that optimizes Factorio factory layouts. It compiles to a React web app (JS) and a CLI (JVM).

## Commands

- `./gradlew jsBrowserDevelopmentRun --continuous` / `jsBrowserProductionRun --continuous` — dev server for the web UI
- `./gradlew jvmTest` — run kotlin-test suites (commonTest + jvmTest); add `--tests "*RecipeParserTest*"` to filter
- `./gradlew check` — formatting check + all tests. CI runs `gradle --no-daemon clean check jsBrowserDistribution`, which includes `jsBrowserTest` (the JS web tests run in a real headless Chrome)
- `./gradlew spotlessApply` — fix formatting (Spotless/ktfmt `kotlinlangStyle`; covers `src/**/*.kt` and `build.gradle.kts`)
- `./gradlew run` — run the CLI (`CliOptimizerKt`); `./gradlew buildFatJar` produces a runnable fat jar

## Testing

- Tests are expected by default: every behavior change or new logic gets tests
  **without being asked**, regardless of source set (`commonMain`, `jsMain`,
  `jvmMain`). Treat a missing test as an incomplete task; the only exception is
  pure formatting or docs-only changes.
- Tests use `kotlin.test` (`@Test`, `assertEquals`, `assertTrue`, ...) and live
  in the matching test source set (`src/commonTest`, `src/jvmTest`,
  `src/jsTest`) under `.../de/bjrke/factorio/optimizer/...`.
- When a test makes multiple assertions that do not depend on each other, they
  MUST be combined with the `de.bjrke.test` helpers — `assertAll` for a list of
  independent checks, and `assertTable` / `assertTableOf` for table-driven
  cases. Prefer descriptive names that state the expected behavior (e.g.
  `vulcanusSteelUsesLavaNotChestOre`).
- `commonTest` runs on both JVM and JS (the JS suite runs in headless Chrome via
  `jsBrowserTest`), so keep it platform-neutral; target-specific tests belong in
  `src/jvmTest` / `src/jsTest`.
- Iterate quickly with `./gradlew jvmTest`; before finishing run
  `./gradlew check` and `./gradlew spotlessApply`.

## Sandbox environment (`sbx run opencode`)

- This agent runs inside a `sbx run opencode` sandbox: network access is
  restricted and many URLs are blocked.
- A one-off workaround for a blocked URL is acceptable. But if you hit the same
  block repeatedly, or you believe the URL is safe and would help you
  significantly, **stop the loop** and tell the user exactly which URL(s) to
  allow, so they can stop the sandbox and unblock them.
- Some resources are simply not available in the sandbox (for example the local
  Factorio installation needed by `dumpPrototypes` / `updateImages`). Do not go
  searching for them; stop and ask the user for help instead.

## Host Gradle locks

- The user also runs Gradle on the host and sometimes forgets to stop it.
  Symptoms: `./gradlew clean` fails, or the `build/` directory cannot be deleted
  because files are locked. Do not keep retrying — **stop the loop** and ask the
  user to stop the host Gradle build/daemon.

## Asking the user for help

- Never paste commands into chat for the user to run. Instead write a short,
  reviewable, Ubuntu-compatible shell script under `tmp/` (repo root).
- Name it `<topic>.sh` and have it write its results next to itself as
  `<topic>.out` (or `.log`) file(s). Both directions are painful in the sandbox:
  moving content out of it, and getting content in, so keep everything you need
  side by side in `tmp/`.
- `tmp/` is intentionally not gitignored: keep the scripts uncommitted, and
  delete the `tmp/` files once they are no longer needed.

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
