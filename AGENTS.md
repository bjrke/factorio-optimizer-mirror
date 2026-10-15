# AGENTS.md

Factorio-Optimizer is a Kotlin Multiplatform (Gradle 9.8.1, Kotlin 2.4.21) app
optimizing Factorio factory layouts. Compiles to a React web app (JS) and a CLI
(JVM).

> Agent-loop discipline: attempt network requests without asking; only after a
> fetch exhausts the retry budget (see "Sandbox environment") stop and ask the
> user with proof — do not loop or route around it.

## Commands

| Command | Purpose |
| --- | --- |
| `./gradlew jsBrowserDevelopmentRun --continuous` / `jsBrowserProductionRun --continuous` | Web UI dev server |
| `./gradlew jvmTest` | kotlin-test suites (commonTest + jvmTest); filter with `--tests "*RecipeParserTest*"` |
| `./gradlew check` | Formatting check + all tests; CI: `gradle --no-daemon clean check jsBrowserDistribution` (adds `jsBrowserTest`, real headless Chrome) |
| `./gradlew spotlessApply` | Fix formatting (Spotless/ktfmt `kotlinlangStyle`, `src/**/*.kt` + `build.gradle.kts`) |
| `./gradlew run` | Run the CLI (`CliOptimizerKt`); `./gradlew buildFatJar` makes a runnable fat jar |

## Testing

- Every behavior change or new logic gets tests **without being asked**, in the
  matching source set (`commonMain`/`jsMain`/`jvmMain` →
  `commonTest`/`jsTest`/`jvmTest`); missing test = incomplete task. Exception:
  pure formatting/docs. Use `kotlin.test`.
- Multiple independent assertions MUST use the `de.bjrke.test` helpers —
  `assertAll`, and `assertTable`/`assertTableOf` for table-driven cases.
  Descriptive names stating expected behavior (e.g.
  `vulcanusSteelUsesLavaNotChestOre`).
- `commonTest` runs on JVM **and** JS: keep it platform-neutral; target specifics
  go in `src/jvmTest` / `src/jsTest`.
- Iterate with `./gradlew jvmTest`; finish with `./gradlew check` +
  `./gradlew spotlessApply`.

### Component tests (jsTest)

- React components are tested in `src/jsTest` with
  [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/),
  running in the real headless-Chrome karma browser used by `jsBrowserTest`.
- Facade + `ComponentTest` base live in
  `src/jsTest/kotlin/de/bjrke/test/ReactTestingLibrary.kt`: `render(...)`,
  `screen`, `fireEvent`, `within(...)`, `changeTo(...)` / `key(...)`, per-test
  `IS_REACT_ACT_ENVIRONMENT` setup + RTL `cleanup()`. Extend `ComponentTest`.
- Inner component `FC`s are `internal` so tests render them via
  `component.create { ... }`, not the `url...` wrappers (which drag in
  router/recipe context).
- npm test deps: `implementation(npm("<pkg>", "<version>"))` in `jsTest`, refresh
  with `./gradlew kotlinUpgradeYarnLock`; run
  `./gradlew jsBrowserTest --tests "*MaterialSelectorTest*"`.

## Sandbox environment (`sbx run opencode`)

- Outbound traffic goes through the Docker Sandboxes proxy on
  `gateway.docker.internal:3128`, a per-host policy. An unmatched host is **not**
  hard-blocked: it answers `HTTP/1.1 403 Forbidden`, `text/plain`, body
  `Approval required for <host>:<port>.`, hint `sbx policy approval ls`.
- Do **not** ask before fetching, and do not assume a host is blocked. Verify
  first and read the **body** — a real origin `403` lacks the `Approval required
  for …` text:
  `curl -sS -m 8 -D - -o /tmp/opencode/body -w '\n%{http_code}\n' <url>`.
- On that approval body — or once a fetch exhausts the retry budget — **stop and
  ask the user whether to approve or block the host**; no mirrors/proxies/
  alternate URLs. The ask must include the exact host/URL, the observed status,
  and the raw body verbatim (proof).
- An approved host passes silently, so allowed hosts are not tracked. A blocked
  host returns the *same* `Approval required for <host>:<port>.` body as a
  merely-pending one, so the Blocked list below is the only authoritative
  signal: a listed host must never be retried or re-asked.
  - Blocked (do not retry, do not ask): `bing.com`, `www.bing.com`.
- Retry budget: at most **2 distinct URLs/approaches per failing resource**, then
  ask one direct question naming the URL(s) and wait; do not route around it.
- Not available in the sandbox (don't search — ask the user): the local Factorio
  install needed by `dumpPrototypes` / `updateImages`. Prefer built-in knowledge
  and this repo's own files; no speculative `webfetch`.

## Host Gradle locks

- The user may leave Gradle running on the host: `./gradlew clean` fails, or
  `build/` is locked. Do not retry — **stop the loop** and ask the user to stop
  the host Gradle build/daemon.

## Asking the user for help

- A **question** (blocked/failing URL, missing resource, undecidable choice) is
  asked directly and the task stopped — never turned into a script. For a
  blocked/failing URL, name the host, paste the `curl` proof (status + raw
  body), and ask whether to approve or block it.
- A **host command** (must run outside the sandbox): never paste it into chat.
  Write a short, reviewable, Ubuntu-compatible script `tmp/<topic>.sh` that
  writes results beside itself (`tmp/<topic>.out`/`.log`). Both directions are
  painful in the sandbox, so keep everything needed in `tmp/`. `tmp/` is
  intentionally not gitignored: keep scripts uncommitted, delete when done.

## Prerequisites

- JDK 25 is required. `gradle.properties` sets
  `org.gradle.java.installations.auto-download=false`, so a local JDK 25 must
  exist; otherwise the build fails with a cryptic error (just the installed
  version string). CI:
  `registry.gitlab.com/bjrke/docker-gradle-chrome:latest` (Gradle JDK 25 +
  Google Chrome for `jsBrowserTest`).

## Code layout (Kotlin Multiplatform)

- `src/commonMain` — shared logic: recipe parser (`recipeParser.kt`), optimizer
  (`optimizer.kt`), model, dumped `prototypes/` data
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, `styled-next`,
  `react-router-dom`); entry `js/index.kt`; `js/business/loadRecipe.kt` pulls
  dumps via `@JsModule` + webpack config in `webpack.config.d/`
- `src/jvmMain` — CLI entry `jvm/CliOptimizer.kt`; `jvm/business/loadRecipe.kt`
  loads recipes from the classpath resource
- `src/commonTest` — shared kotlin-test suites run by both JVM and JS test tasks

`commonMain` compiles to both targets: keep it platform-neutral; platform
differences belong in target-specific `loadRecipe.kt`/`index.kt` files.

## Git workflow

- Before making changes, ensure you are on a feature branch: if on `main` (or not
  already a feature branch), branch off from `main` first.
- Branch names contain the author, e.g. `<author>/<topic>`.
- **Never push, and never set or change a branch's upstream.** Pushing is the
  user's responsibility; an agent that pushes can bind a feature branch to
  `origin/main`.
- Commit after the work is done (no explicit request needed). Headline ≤ 50
  chars; body wrapped at 72 chars.
- When asked to squash: choose the upstream that is ahead —
  `base=main; git merge-base --is-ancestor main origin/main && base=origin/main`
  — rebase the branch onto `$base`, then fold the branch's own commits with
  `git reset --soft "$base"` plus a single `git commit`. The squash commit must
  contain only the branch's changes; commits already in `$base` are never
  re-added.

## Generated data — do not hand-edit

- `src/commonMain/resources/prototypes/{base,space-age}.json` are `data.raw`
  dumps from `factorio --dump-data` for base and base + Space Age DLC.
  Regenerate with `./gradlew dumpPrototypes` (Factorio data stage, isolated
  config/mod-list, needs a Factorio Steam install). The game resolves the dump,
  so it includes all `data-updates.lua`/`data-final-fixes.lua` changes; the
  parser reads only the prototype types it needs. UI and CLI use the base dump,
  or the Space Age dump when active; parsed results are cached in
  `RecipeProvider`.
- `src/jsMain/resources/icons/*.png` are 120x64 crops from a Linux Factorio
  Steam install. `./gradlew updateImages` requires `imagemagick optipng
  pngquant` and only copies/optimizes icons whose top-left 64x64 region changed
  significantly. Don't add icons manually.
