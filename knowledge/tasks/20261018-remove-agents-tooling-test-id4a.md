---
task: 20261018-remove-agents-tooling-test-id4a
title: "Remove agent tooling test from the app test suite"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/task-knowledge-system
issue: -
completed: 2026-10-18
---

## Objective

`src/jvmTest/.../agents/AgentsToolingTest.kt` shelled out to `bash` and reached
into `.opencode/` agent tooling from the application's test suite. That is a
boundary violation: `./gradlew check` must cover the app only.

## Acceptance criteria

- [x] The app test suite no longer references `.opencode/`, `task-management` or
      the `agents` script.
- [x] Agent tooling stays verified by its own `agents selftest` (plus
      `plugin-test.mjs`).
- [x] The boundary is recorded so it is not reintroduced.
- [x] `./gradlew jvmTest check` still passes.

## Current state

Deleted `src/jvmTest/kotlin/de/bjrke/factorio/optimizer/agents/AgentsToolingTest.kt`
(and the now-empty `agents` directory). It had run `agents selftest`/`help`/
`list` through `ProcessBuilder("bash", ...)`. `agents selftest` already runs the
full lifecycle in a throwaway repo, so no coverage was lost — it just moved out of
the app suite, where it never belonged.

Recorded the boundary in `knowledge/conventions.md` (Testing) and in the
task-management `SKILL.md`.

## Verified findings

- `grep -rn '\.opencode\|task-management\|AgentsTooling' src/` → no matches.
- No Gradle build script (`*.kts`) referenced the script or the test.
- `./gradlew jvmTest check` → BUILD SUCCESSFUL after the deletion.

## Hypotheses / unverified

- none

## Decisions

- Agent tooling is verified by `agents selftest`, never by `src/*Test`. The app
  suite must not shell out to `bash` or read `.opencode/`.

## Attempts and outcomes

- Read the test, grepped `src/` and `*.kts` for references, deleted the file,
  re-ran the build.

## Failed approaches (do not repeat without new evidence)

- Driving the agent CLI from a Kotlin unit test (the removed approach).

## Open questions

- none

## Remaining tasks

- [x] Delete the test; confirm no other references.
- [x] Update `knowledge/conventions.md` and `SKILL.md`.
- [x] Commit and complete (the `archive` step was removed by
      `20261018-restructure-tasks-flat-symlinks-hyox`).

## Tests and verification

- `./gradlew jvmTest check` → BUILD SUCCESSFUL.
- `agents selftest` → 27 passed, 0 failed (the tooling's own check).

## Next action

- none — committed and completed; the active symlink is removed.

## Completion report

- Outcome: the application test suite no longer touches agent tooling; that
  verification lives solely in `agents selftest`.
- Verification: `grep` found no remaining references in `src/`;
  `./gradlew jvmTest check` successful; `agents selftest` 27/0.
- Lessons: test suites must not cross the application/tooling boundary; put such
  checks in the tooling's own selftest and say so in `knowledge/conventions.md`.
- Follow-up: none.
