# AGENTS.md

Factorio-Optimizer is a Kotlin Multiplatform (Gradle 9.7.1, Kotlin 2.4.20) app that optimizes Factorio factory layouts. It compiles to a React web app (JS) and a CLI (JVM).

> Agent-loop discipline: when a network request or a resource is blocked, stop
> after the retry budget in "Sandbox environment" and ask the user directly —
> do not loop or route around it.

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

### Component tests (jsTest)

- React components are tested in `src/jsTest` with
  [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/)
  (`@testing-library/react`), which runs in the real headless-Chrome karma browser
  used by `jsBrowserTest`.
- The Kotlin facade and the `ComponentTest` base class live in
  `src/jsTest/kotlin/de/bjrke/test/ReactTestingLibrary.kt`: `render(...)`,
  `screen`, `fireEvent`, `within(...)`, the `changeTo(...)` / `key(...)` event
  helpers, and per-test `IS_REACT_ACT_ENVIRONMENT` setup + RTL `cleanup()`.
  Component tests extend `ComponentTest`.
- Inner component `FC`s are `internal` so tests render them directly with
  `component.create { ... }`, instead of going through the `url...` wrappers
  (which drag in router/recipe context).
- Add npm test dependencies in the `jsTest` source set with
  `implementation(npm("<pkg>", "<version>"))` and refresh the lock with
  `./gradlew kotlinUpgradeYarnLock`; run the suite with
  `./gradlew jsBrowserTest --tests "*MaterialSelectorTest*"`.

## Sandbox environment (`sbx run opencode`)

- This agent runs inside a `sbx run opencode` sandbox where traffic goes through
  the `gateway.docker.internal:3128` proxy. The proxy is **permissive**: most
  hosts work, only a few are blocked. Don't assume a fetch is blocked — a quick
  `curl -sS -m 8 -o /dev/null -w "%{http_code}" <url>` answers it in seconds.
- Verified reachable (last checked 2026-10): `github.com`,
  `raw.githubusercontent.com`, `gitlab.com`, `repo.maven.apache.org`,
  `plugins.gradle.org`, `services.gradle.org`, `unpkg.com`, `dl.google.com`,
  `archive.ubuntu.com`, and the npm/yarn registries `registry.npmjs.org`,
  `registry.yarnpkg.com`, `yarnpkg.com`. Chrome can be installed from
  `https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb`,
  which is needed to run `jsBrowserTest`.
- Known blocked (proxy returns HTTP 403): `repo1.maven.org`,
  `cdn.jsdelivr.net`. This list may drift, hence the curl check above.
- Retry budget: at most **2 distinct URLs/approaches per blocked resource**.
  After that, **stop the loop** — no proxies, mirrors, or alternate URLs — and
  ask the user one direct question naming the exact URL(s) to allow. Waiting for
  the answer is the correct next step; do not continue the task around it.
- Some resources are simply not available in the sandbox (for example the local
  Factorio installation needed by `dumpPrototypes` / `updateImages`). Do not go
  searching for them; stop and ask the user for help instead.
- Prefer your built-in knowledge and this repository's own files. Do not
  `webfetch` speculatively.

## Host Gradle locks

- The user also runs Gradle on the host and sometimes forgets to stop it.
  Symptoms: `./gradlew clean` fails, or the `build/` directory cannot be deleted
  because files are locked. Do not keep retrying — **stop the loop** and ask the
  user to stop the host Gradle build/daemon.

## Asking the user for help

- Distinguish the two cases: a **question** (a blocked URL, a missing resource,
  or a decision you cannot make) must be asked directly and the task stopped —
  do not turn it into a script. A **host command** (something that must run
  outside the sandbox) is handled with a `tmp/` script below.
- For host commands, never paste the command into chat for the user to run.
  Instead write a short, reviewable, Ubuntu-compatible shell script under `tmp/`
  (repo root).
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
