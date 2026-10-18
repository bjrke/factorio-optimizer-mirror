---
task: 20261018-drop-commit-trailers-z1n1
title: "Drop commit trailers and hook; map tasks via file history"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/task-knowledge-system
issue: -
completed: 2026-10-18
---

## Objective

A `Task-ID:` trailer is an unverifiable promise, and the Git hook that wrote it is
an unwanted moving part. Remove both and derive the task↔commit link from the task
file's own history, with tooling to look it up in both directions.

## Acceptance criteria

- [x] No Git hook is installed or offered; `install-hook` is gone.
- [x] No commit-trailer requirement anywhere; `check-trailer`/`trailers`/
      `collect-ids` are gone.
- [x] `agents commits <TASK-ID>` finds the commits that touched the task file, and
      `agents tasks <REV>` finds the tasks a commit changed.
- [x] The link survives amend/rebase/squash (a squash commit owns every task file
      it added).
- [x] Docs and `selftest` updated; `./gradlew check` unaffected.

## Current state

The task file `knowledge/tasks/<id>.md` is now the record of which commits concern
a task, so nothing has to be asserted in a commit message. `agents commits <id>`
runs `git log --follow -- knowledge/tasks/<id>.md`; `agents tasks <rev>` reads
`git show --name-only <rev> -- knowledge/tasks` and maps the paths back to ids.

Removed: `install-hook`, `check-trailer`, `trailers`, `collect-ids`, and the local
`.git/hooks/prepare-commit-msg`. The squash recipe in `knowledge/conventions.md`
collects nothing now; it just resets and commits.

Docs updated: `AGENTS.md` (no trailer), `SKILL.md` (Identity, commands, Git
integration), `knowledge/index.md`, `knowledge/conventions.md`, `knowledge/pitfalls.md`
(replaces the hook pitfall), `knowledge/decisions.md` (new entry; the trailer
decision marked superseded).

## Verified findings

- `agents selftest` → 40 passed, 0 failed (trailer tests replaced by file-history
  tests: `tasks HEAD` lists changed task ids, `commits <id>` finds the commit,
  `tasks` excludes untouched tasks).
- `node .opencode/skills/task-management/scripts/plugin-test.mjs` → 14 passed.
- No stale references to the removed commands outside the historical task records.

## Hypotheses / unverified

- `git show --name-only <merge>` prints nothing for a real merge commit; the
  workflow squashes/rebases rather than merges, so this is not exercised.

## Decisions

- The task file's Git history is the single, verifiable task↔commit link. It
  needs no convention to honour and cannot disagree with the tree.

## Attempts and outcomes

- Removed the trailer code paths and the hook; repurposed the `commits` verb and
  added `tasks`; rewrote the `selftest` trailer block around file history.

## Failed approaches (do not repeat without new evidence)

- Enforcing task↔commit with a `prepare-commit-msg` hook: unverifiable and a
  liability; removed.

## Open questions

- none

## Remaining tasks

- [x] Remove the hook and the trailer commands; add `commits`/`tasks`.
- [x] Update the docs and the self-test.
- [x] Run `./gradlew check`, commit, then complete this task.

## Tests and verification

- `agents selftest` → 40/0; `plugin-test.mjs` → 14/0; `agents validate` → OK.
- `./gradlew check` → BUILD SUCCESSFUL.
- On the real repo: `agents tasks HEAD` → `20261018-restructure-tasks-flat-symlinks-hyox`;
  `agents commits <hyox>` → the two commits that touched its file.

## Next action

- none — committed and completed; the active symlink is removed.

## Completion report

- Outcome: task ↔ commit is now read from the task file's own history. The Git
  hook, the `Task-ID:` trailer requirement and the trailer commands are gone;
  `agents commits <id>` and `agents tasks <rev>` do the lookups.
- Verification: `agents selftest` 40/0; `plugin-test.mjs` 14/0; `agents validate`
  OK; `./gradlew check` successful.
- Lessons: a squash commit owns every task file it added, so a single `git show`
  recovers the whole branch's tasks — no message convention needed. `git log
  --follow` keeps the lookup working across the layout migration.
- Follow-up: none.
