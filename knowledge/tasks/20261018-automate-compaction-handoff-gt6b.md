---
task: 20261018-automate-compaction-handoff-gt6b
title: "Automate task creation and compaction knowledge handoff"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/task-knowledge-system
issue:
completed: 2026-10-18
---

## Objective

Remove the manual steps around the task/knowledge system: create a task
automatically when none is active, and make compaction preserve relevant
intermediate knowledge while dropping the rest — without the user having to
remember anything.

## Acceptance criteria

- [x] A task is created automatically (by the agent) when non-trivial work starts
      without one; the user is not asked to remember the steps.
- [x] Compaction automatically preserves task-relevant knowledge and drops noise.
- [x] The automatic layer needs no configuration change and cannot break OpenCode
      if its optional APIs are unavailable.
- [x] Automated tests cover the new logic.

## Current state

Added `.opencode/plugins/task-context.js`, a dependency-free OpenCode V2 plugin
discovered from `.opencode/plugins/` with no config change. It registers three
hooks and only injects instructions (never reads files, never runs commands):

- `prompt` — briefs a session once (per session id, tracked in plugin storage).
- `context` — reminds every model call where durable state lives; because it is
  injected per request it survives compaction.
- `compaction` — steers the summarizer to keep the task id/checkpoint, objective,
  verified findings, decisions, failed approaches, remaining tasks and next action,
  and to drop superseded experiments and repeated tool output.

Docs updated: `AGENTS.md` (task creation is automatic), `knowledge/index.md`
("Automatic layer"), `knowledge/decisions.md`, `knowledge/pitfalls.md`,
`SKILL.md`. `agents selftest` now also checks the plugin and runs the node unit
test when node is available.

## Verified findings

- `node .opencode/skills/task-management/scripts/plugin-test.mjs` → 14 passed,
  0 failed. The plugin is evaluated from a data URL, so the `@opencode/plugin`
  fallback path is exercised.
- `agents selftest` → 27 passed, 0 failed (was 24; +3 plugin checks).

## Hypotheses / unverified

- OpenCode loads a plain `{ id, setup }` default export from a `.js` plugin file.
  Documented behaviour, but not verifiable without restarting the server.
- Hot reload: `.opencode/plugins/*.js` changes may need `opencode service restart`.

## Decisions

- Inject instructions only, never read files or run commands: keeps the plugin
  safe on a sandboxed runtime and testable without OpenCode.
- Keep the checkpoint on disk as the authoritative state; the summary carries a
  pointer plus the salient decisions, and the per-request reminder makes the agent
  re-read the checkpoint after compaction.

## Failed approaches (do not repeat without new evidence)

- Reading the checkpoint from the plugin via `node:fs` was considered and rejected:
  the plugin runtime is not guaranteed to expose filesystem access.

## Open questions

- Resolved: the plugin hot-reloaded, so no restart was needed. `opencode service
  restart` (which drops the TUI connection) is only needed when a plugin edit does
  not take effect.

## Remaining tasks

- [x] Run `agents validate`, `./gradlew jvmTest` and `./gradlew check`.
- [x] Commit, then complete + archive this task.

## Tests and verification

- `agents selftest` → 27 passed, 0 failed.
- `node plugin-test.mjs` → 14 passed, 0 failed.
- `./gradlew jvmTest` (AgentsToolingTest) → 4 tests, 0 failures.
- `./gradlew check` → BUILD SUCCESSFUL.
- `agents validate` → OK.

## Next action

- none — committed and completed; the active symlink is removed.

## Completion report

- Outcome: the plugin now briefs each session once, reminds every model call where
  durable state lives, and steers compaction to keep task knowledge and drop noise,
  so task creation and the compaction handoff no longer depend on the user
  remembering them.
- Verification: `agents selftest` 27/0; `plugin-test.mjs` 14/0; `AgentsToolingTest`
  4/0; `./gradlew check` successful.
- Lessons: keep plugin logic to instruction injection so it needs no filesystem or
  shell access and can be unit-tested from a data URL without the OpenCode runtime.
- Follow-up: activate/confirm with `opencode service restart` (drops the TUI
  connection). If OpenCode rejects a plain `{ id, setup }` export, switch to the
  `Plugin.define` object the runtime exposes.
