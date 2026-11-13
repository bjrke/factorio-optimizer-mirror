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
| `./gradlew jvmTestProfile` | `jvmTest` with a JDK Flight Recorder CPU profile attached; one merged recording in `build/profiles/jvmTest/`, opened in IntelliJ's JFR viewer, JMC or `jfr view hot-methods` |
| `./gradlew check` | Formatting check + detekt + `jvmTest` + `jsBrowserTest` (headless Chrome); CI runs the same command plus `jsBrowserDistribution` |
| `./gradlew detekt` | Detekt static analysis (light, whole `src` tree); `check` runs the type-resolved `detektMainJvm`/`detektTestJvm` and the light JS-source-set tasks instead, and fails on any finding |
| `./gradlew spotlessApply` | Fix formatting (Spotless/ktfmt `kotlinlangStyle`) for `src/**` and the build scripts; run it as `./gradlew spotlessApply check` |
| `./gradlew run` | Run the CLI (`CliOptimizerKt`); takes a material plus settings (`--planet`, `--factory`, `--belt`, `--inserter`, `--full-belt`, `--intermediates`, …) or a pasted web-UI URL, and outputs via `--settings`/`--listing`/`--layout`/`--json`/`--blueprint`; `./gradlew buildFatJar` makes a runnable fat jar |

## Knowledge (`knowledge/`)

- Start by reading `knowledge/index.md`, then only the files it points to for this
  work. Verify their claims against the current code, tests and Git state — notes
  go stale.
- Promote reusable, stable, verified findings into the `knowledge/` file that owns
  the area: `code.md` (map and package edges), `layout.md` or `lanes-fluids.md`
  (blueprint geometry), `planning.md`, `build.md`, `testing.md`, or
  `conventions.md` (how the work is done). A trap goes into that file's `## Traps`
  section; only a constraint no single owner file can carry goes into
  `decisions.md`. Put the reason for a value, order or shape in the KDoc it
  governs — the symbol, or the package KDoc when it spans a package.
- Before `/compact`, a handoff or a pause, run `/handoff` (or state the same
  points yourself). The compaction plugin uses the newest assistant reply's
  `## Handoff state` block as the summary, so that reply must be the last thing
  before `/compact` and must carry the verified state, decisions and next action.
  Without the block the plugin only steers the summarizer; the agent never
  compacts on its own.
- Writing or changing tests? Read `knowledge/testing.md` first.

## Testing

- Run Gradle only when the branch changes something the build consumes:
  - **application code / resources / build scripts** (`src/**`, `build.gradle.kts`,
    `buildSrc/**`, `gradle/**`, `settings.gradle.kts`) → iterate with
    `./gradlew jvmTest`, finish with `./gradlew spotlessApply check` (one
    invocation, one Gradle process);
  - **docs / knowledge only** (`*.md`, `knowledge/`, `AGENTS.md`) → **no Gradle**
    at all; Markdown is not a build input;
  - **agent tooling only** (`.opencode/**`) → **no Gradle**; syntax-check plugins
    with `node --check`.
  A branch that mixes both follows the code rule.
- Every behavior change or new logic gets tests **without being asked**; a missing
  test means the change is incomplete.
- The full rules — the `de.bjrke.test` assertion helpers, collection/emptiness
  checks, table-driven cases, source-set choice and the `jsTest` component-test
  harness — live in `knowledge/testing.md`. Read it before writing or changing
  tests.

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
  - Approved for **reading documentation only**: `opencode.ai`. Hard rule: never
    download binaries or scripts from that domain — only read its docs pages.
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

- Whenever presenting **options or choices**, use the `question` tool — never a
  prose list the user would have to retype. This applies to every decision, not
  just ones that seem worth the tool.
- A **question** (blocked/failing URL, missing resource, undecidable choice) is
  asked directly and the work stopped — never turned into a script. For a
  blocked/failing URL, name the host, paste the `curl` proof (status + raw
  body), and ask whether to approve or block it.
- A **host command** (must run outside the sandbox): never paste it into chat;
  write a short, reviewable, Ubuntu-compatible script `tmp/<topic>.sh` that saves
  results beside itself. `tmp/` is intentionally not gitignored: keep scripts
  uncommitted and delete them when done.

## Prerequisites

- JDK 25 is required; a local install must exist or the build fails with a
  cryptic error (just the installed version string). Details and the CI image are
  in `knowledge/build.md`.

## Code layout

- `commonMain` holds shared logic; `jsMain` the React UI; `jvmMain` the CLI;
  `commonTest`/`jsTest`/`jvmTest` the tests. `commonMain` must stay
  platform-neutral. Full map: `knowledge/code.md`.
- Prefer read-only collection construction: `map`/`flatMap` plus
  `toList`/`toSet`/`toMap` for transformations, and
  `buildMap`/`buildSet`/`buildList` for incremental construction. Avoid
  `mutableMapOf`/`mutableSetOf`/`mutableListOf`/`HashMap` unless the collection is
  mutated after construction. Start with `asSequence()` when the intermediate
  collection would be expensive.

## Git workflow

- Before making changes, ensure you are on a feature branch: if on `main` (or not
  already a feature branch), branch off from `main` first.
- Branch names contain the author, e.g. `<author>/<topic>`.
- **Never push, and never set or change a branch's upstream.** Pushing is the
  user's responsibility; an agent that pushes can bind a feature branch to
  `origin/main`.
- Commit after the work is done (no explicit request needed).
- Every commit message — written by hand or generated by `/squash` — follows the
  same rules: a headline of at most 50 characters, imperative, American English,
  no trailing period, no `!<MR>` suffix; one blank line, then a short body
  wrapped at 72 characters that explains why. Keep it to the essentials: no
  novel-length recap, no file-by-file or symbol-by-symbol walkthrough, no
  repetition of `knowledge/` or documentation, and no claims about tests or
  checks the diff does not show. The diff carries the what; the KDoc carries the
  why.
- **Squash only via `/squash`** (`.opencode/plugins/squash.js`, a plugin command
  that runs with no agent turn); never do the git steps by hand. If asked to
  squash, tell the user to run `/squash`.

## Generated data — do not hand-edit

- Do not hand-edit `src/commonMain/resources/prototypes/*.json` or
  `src/jsMain/resources/icons/*.png`. Regenerate them with
  `./gradlew dumpPrototypes` / `./gradlew updateImages`; details and caveats are in
  `knowledge/build.md`.
