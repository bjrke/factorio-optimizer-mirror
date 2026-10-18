---
task: 20261018-task-knowledge-system-8067
title: "Add a persistent task and knowledge management system"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/task-knowledge-system
issue:
completed: 2026-10-18
---

## Objective

Add a low-overhead task and knowledge system so long OpenCode sessions stop
bloating: task state lives in files under `knowledge/`, reusable project knowledge
is curated separately, and `/compact` or a fresh session can resume from verified
facts instead of a replay of old tool output.

## Acceptance criteria

- [x] Four persistence layers exist: concise conversation, active checkpoint,
      project knowledge, historical archive.
- [x] Immutable task IDs `YYYYMMDD-<slug>-<suffix>` (no prefix), unique, never renamed.
- [x] Checkpoint schema with verified facts / hypotheses / open questions.
- [x] Lifecycle create → work → checkpoint → verify → complete → promote → archive.
- [x] `Task-ID:` commit trailer with an optional, fail-safe Git hook.
- [x] `AGENTS.md` updated concisely; detailed rules moved to `knowledge/`.
- [x] Automation is a small bash CLI; no OpenCode plugin, no new dependencies.
- [x] Tests cover the lifecycle and error handling; run and report real results.

## Current state

Implemented and passing its own test suite. Files added: the `agents` CLI, the
`task-management` skill, `/task` and `/handoff` commands, the `knowledge/`
knowledge base, a task template, and `AgentsToolingTest`. `AGENTS.md` was trimmed:
the workflow section was added and the detailed testing/code-layout/generated-data
guidance was moved (not duplicated) into `knowledge/conventions.md` and
`knowledge/architecture.md`.

## Verified findings

- `agents selftest` → `selftest: 24 passed, 0 failed` (throwaway repo, real `git`).
- OpenCode v2.0.32 auto-discovers `.opencode/skills/` and `.opencode/commands/`;
  the new `task-management` skill appeared in the session with no config change.
  The project `opencode.json` is gitignored, so config was deliberately untouched.
- OpenCode V2 plugin hooks (`session.hook("compaction"|"context")`) exist, but were
  rejected: an untestable JS runtime surface in a Kotlin repo (see decisions.md).
- `--no-verify` does **not** skip `prepare-commit-msg`; disable it with
  `-c core.hooksPath=<empty>` when a trailer must be absent.

## Hypotheses / unverified

- `./gradlew check` runs the new jvmTest in CI (to be confirmed by the check run).
- Nested `AGENTS.md` discovery is documented but not exercised here; not relied on.

## Decisions

- Artifacts under a visible top-level `knowledge/`; tooling in the OpenCode skill
  dir `.opencode/skills/task-management/`. Recorded in `knowledge/decisions.md`.
- No plugin; explicit `agents`/`/handoff` checkpointing.
- `AGENTS.md` keeps a hint to `knowledge/conventions.md` for test rules rather than
  loading them on every prompt.

## Attempts and outcomes

- `prepare-commit-msg` hook: works; tested via a real commit in the throwaway repo.
- Moved `AGENTS.md` detail into `knowledge/` (conventions, architecture, pitfalls,
  decisions) with short pointers left behind.

## Failed approaches (do not repeat without new evidence)

- Using `git rev-parse --abbrev-ref HEAD` for the default branch injected a
  `HEAD\n-` value into a `sed` expression (unborn branch). Use
  `git symbolic-ref --short -q HEAD` and keep `sed_esc` newline-safe.
- Asserting a fixed task ID in the hook test broke after a later `new` changed
  `.current`; assert against `.current`.

## Open questions

- Resolved: `agents install-hook` was removed by `20261018-drop-commit-trailers-z1n1`;
  no Git hook is used, so there is nothing to install.

## Remaining tasks

- [x] Run `./gradlew jvmTest --tests "*AgentsToolingTest*"` and `./gradlew check`.
- [x] Run `./gradlew spotlessApply` and re-check formatting.
- [x] Fill the completion report, `complete`, `archive` this task.

## Tests and verification

- `.opencode/skills/task-management/scripts/agents selftest` → 24 passed, 0 failed.
- `./gradlew jvmTest` → all suites pass; `AgentsToolingTest` 4 tests, 0 failures.
- `./gradlew jsBrowserTest` → 365 tests, 0 failures (from the `check` run).
- `./gradlew spotlessCheck` → BUILD SUCCESSFUL.
- `agents validate` → `validate: OK`.
- The second clean `./gradlew check` was initially blocked by a stale Gradle daemon
  holding the cache lock (the first run was `BUILD SUCCESSFUL`, but persisting
  `.gradle/9.8.1/fileHashes` hit an EOFException). Re-run when the branch was
  finished: `./gradlew check --rerun-tasks` → BUILD SUCCESSFUL (jvmTest 315/0,
  jsBrowserTest 365/0, spotlessCheck) — environmental, not a code defect.

## Next action

- none — committed and completed. The daemon/`check` question is answered above (a
  forced re-run passes); `install-hook` no longer exists, so there is nothing to
  install.

## Completion report

- Outcome: a working, documented, tested task/knowledge system. `knowledge/`
  holds the knowledge base and task checkpoints; the `agents` CLI plus `/task`
  and `/handoff` commands drive the lifecycle; `AGENTS.md` keeps only always-on
  rules and points into `knowledge/`.
- Verification: `agents selftest` 24/24; `agents validate` OK; `AgentsToolingTest`
  4/4 in `./gradlew jvmTest`; `./gradlew jsBrowserTest` 365/0; `./gradlew check`
  and `spotlessCheck` pass. The trailer hook was verified with a real commit.
- Lessons: the repo's Kotlin daemon can fail to compile `buildSrc`
  (`kotlin version 2.4.10 / Daemon compilation failed`); in-process compilation
  (`-Dkotlin.compiler.execution.strategy=in-process`) works around it. An unborn
  branch's `HEAD` must not be fed to `sed`; and `--no-verify` does not skip
  `prepare-commit-msg`.
- Follow-up: none required. Optional: enable `snapshots`/compaction settings in the
  local `opencode.json` (it uses deprecated V1 keys), and promote more findings
  into `knowledge/pitfalls.md` as they are discovered.
