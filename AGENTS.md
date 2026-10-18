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

## Tasks and knowledge (`knowledge/`)

- Non-trivial work runs as a task with an immutable ID
  `YYYYMMDD-<slug>-<suffix>` (e.g. `20261018-parser-nesting-k7m2`). Create or
  resume one with `/task`, or
  `.opencode/skills/task-management/scripts/agents new "<objective>"`.
- This is automatic — do not make the user remember the steps: if no task is
  active when non-trivial work starts, create one yourself (a provisional slug is
  fine) and report its id. Before creating one, run `agents find` and reuse a task
  that already covers the branch (`agents resume`, or `agents reopen` if it was
  completed). The `task-context` plugin briefs every session, keeps the workflow in
  front of you, and steers compaction toward the checkpoint.
- Every task is one committed file in `knowledge/tasks/<id>.md`; in-progress work
  is a gitignored symlink under `knowledge/tasks/.active/`, and
  `knowledge/tasks/.active/.current` names the active task (`agents status`).
  Never guess it as the newest file.
- Start a task by reading `knowledge/index.md`, then only the files it points to
  for this work, then the active checkpoint.
- Keep the checkpoint to conclusions, evidence, decisions and next actions — not
  transcripts. Update it after a real discovery, decision, milestone, test result
  or scope change, not after every tool call.
- Before `/compact`, a handoff or a pause, update the checkpoint (or run
  `/handoff`) so a fresh session resumes from verified facts rather than a replay.
- The `task-context` plugin watches the context budget: past 200k tokens it reminds
  you to hand off at the next natural boundary — finish the current step, start no
  new work, update the checkpoint, then compact — past 250k it requests compaction
  itself, and it registers a `compact_session` tool. Act on the reminder in the turn
  it appears.
- Compacting at a natural boundary is always allowed, budget or not: when a unit of
  work is finished and verified, or before switching to unrelated work, update the
  checkpoint and compact.
- Writing or changing tests? Read `knowledge/conventions.md` first.
- A commit needs no task trailer: the task file's own history links task and
  commit. Use `agents commits <id>` (task → commits) and `agents tasks <rev>`
  (commit → tasks); a squash commit owns every task file it added.
- Finish with `agents complete <id>` — the checkpoint stays in `knowledge/tasks/`
  and its active symlink is removed. Find older tasks with `agents find` or
  `grep`; never load the directory whole.
- Treat checkpoint facts as stale until re-verified against the code, tests and
  Git state.

## Testing

- Run Gradle only when the branch changes something the build consumes:
  - **application code / resources / build scripts** (`src/**`, `build.gradle.kts`,
    `buildSrc/**`, `gradle/**`, `settings.gradle.kts`) → iterate with
    `./gradlew jvmTest`, finish with `./gradlew spotlessApply check` (one
    invocation, one Gradle process);
  - **docs / knowledge only** (`*.md`, `knowledge/`, `AGENTS.md`) → **no Gradle**
    at all; Markdown is not a build input;
  - **agent tooling only** (`.opencode/**`) → **no Gradle**; use the tooling's own
    checks (`agents selftest`).
  A branch that mixes both follows the code rule.
- Every behavior change or new logic gets tests **without being asked**; a missing
  test means the task is incomplete.
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
  chars; body wrapped at 72 chars. No task trailer — task and commit are linked
  through the task file's history.
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
