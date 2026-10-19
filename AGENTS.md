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
| `./gradlew check` | Formatting check + `jvmTest` + `jsBrowserTest` (headless Chrome); CI adds `clean` + `jsBrowserDistribution` |
| `./gradlew spotlessApply` | Fix formatting (Spotless/ktfmt `kotlinlangStyle`) for `src/**` and the build scripts; run it as `./gradlew spotlessApply check` |
| `./gradlew run` | Run the CLI (`CliOptimizerKt`); takes a material plus settings (`--planet`, `--factory`, `--belt`, `--inserter`, `--full-belt`, …) or a pasted web-UI URL, and outputs via `--settings`/`--listing`/`--layout`/`--json`/`--blueprint`; `./gradlew buildFatJar` makes a runnable fat jar |

## Knowledge (`knowledge/`)

- Start by reading `knowledge/index.md`, then only the files it points to for this
  work. Verify their claims against the current code, tests and Git state — notes
  go stale.
- Promote reusable, stable, verified findings into `knowledge/`: architecture
  facts into `architecture.md`, conventions into `conventions.md`, traps and
  diagnostic dead ends into `pitfalls.md`, consequential choices into
  `decisions.md`. Leave per-change detail in the commit message.
- Before `/compact`, a handoff or a pause, run `/handoff` (or state the same
  points yourself). The compaction plugin uses the newest assistant reply's
  `## Handoff state` block as the summary, so that reply must be the last thing
  before `/compact` and must carry the verified state, decisions and next action.
  Without the block the plugin only steers the summarizer; the agent never
  compacts on its own.
- Writing or changing tests? Read `knowledge/conventions.md` first.

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
  harness — live in `knowledge/conventions.md`. Read it before writing or changing
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
  asked directly and the task stopped — never turned into a script. For a
  blocked/failing URL, name the host, paste the `curl` proof (status + raw
  body), and ask whether to approve or block it.
- A **host command** (must run outside the sandbox): never paste it into chat.
  Write a short, reviewable, Ubuntu-compatible script `tmp/<topic>.sh` that
  writes results beside itself (`tmp/<topic>.out`/`.log`). Both directions are
  painful in the sandbox, so keep everything needed in `tmp/`. `tmp/` is
  intentionally not gitignored: keep scripts uncommitted, delete when done.

## Prerequisites

- JDK 25 is required; a local install must exist or the build fails with a
  cryptic error (just the installed version string). Details and the CI image are
  in `knowledge/architecture.md`.

## Code layout

- `commonMain` holds shared logic; `jsMain` the React UI; `jvmMain` the CLI;
  `commonTest`/`jsTest`/`jvmTest` the tests. `commonMain` must stay
  platform-neutral. Full map: `knowledge/architecture.md`.

## Git workflow

- Before making changes, ensure you are on a feature branch: if on `main` (or not
  already a feature branch), branch off from `main` first.
- Branch names contain the author, e.g. `<author>/<topic>`.
- **Never push, and never set or change a branch's upstream.** Pushing is the
  user's responsibility; an agent that pushes can bind a feature branch to
  `origin/main`.
- Commit after the work is done (no explicit request needed). Headline ≤ 50
  chars; body wrapped at 72 chars, explaining why and recording findings that do
  not belong in `knowledge/`.
- When asked to squash, do exactly that and nothing else, following the recipe in
  `knowledge/conventions.md`: choose the upstream that is ahead with
  `git merge-base`, then `git reset --soft` plus one commit whose message is
  rewritten from the squashed content. The squash commit contains only the
  branch's changes.

## Generated data — do not hand-edit

- Do not hand-edit `src/commonMain/resources/prototypes/*.json` or
  `src/jsMain/resources/icons/*.png`. Regenerate them with
  `./gradlew dumpPrototypes` / `./gradlew updateImages`; details and caveats are in
  `knowledge/architecture.md`.
