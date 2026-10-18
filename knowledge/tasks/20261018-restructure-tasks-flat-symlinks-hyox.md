---
task: 20261018-restructure-tasks-flat-symlinks-hyox
title: "Restructure tasks to a flat dir with active symlinks"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/task-knowledge-system
issue: -
completed: 2026-10-18
---

## Objective

Every task should be one committed file, with in-progress work marked locally
instead of living in a separate `active/` tree, so upstream never carries active
state and a working copy can never diverge from the committed one.

## Acceptance criteria

- [x] One flat `knowledge/tasks/<id>.md` for every task; `active/` and `archive/`
      are gone.
- [x] In-progress work is a gitignored relative symlink under
      `knowledge/tasks/.active/`; `.active/.current` names the focus.
- [x] `agents` covers `find`, `reopen`, multi-`Task-ID:` commits and the new
      layout; `archive` is removed.
- [x] Docs and commands reflect the layout; the old decision is marked superseded.
- [x] `agents selftest` and the plugin test pass; `./gradlew check` unaffected.

## Current state

Layout:

```text
knowledge/tasks/<id>.md            every task, committed, whatever its status
knowledge/tasks/.active/<id>.md    gitignored symlink -> ../<id>.md (in progress)
knowledge/tasks/.active/.current   gitignored symlink -> ../<id>.md (the focus)
knowledge/tasks/.gitignore         ignores .active/
```

The committed file is the single source of truth; a symlink is a pointer, never a
copy. `agents new` writes the file, links it and points `.current` at it (and
creates `.gitignore` if missing). `agents complete` sets `status: completed` and
unlinks, leaving the file in place — nothing moves. `agents reopen` re-links a
finished task so related work continues the same record.

`find` lists the branch's tasks: files absent from `origin/main` **and** `main`
(tree comparison, so a behind/ahead mainline cannot cause false positives), or
stamped with the branch. `trailers` / `commits` / `collect-ids` preserve the 1:n
commit↔task relation for squashes.

Migrated the real tree: the three `archive/*.md` files and this task moved to
`knowledge/tasks/`, both subdirectories and their `.gitkeep`s removed, and a
`.gitignore` added.

## Verified findings

- `agents selftest` → 44 passed, 0 failed (covers new/find/reopen/complete,
  multi-trailer, `collect-ids`/`commits`, dangling `.current`, completed-linked
  conflict, `--base`/`--text`/`--active`).
- `node .opencode/skills/task-management/scripts/plugin-test.mjs` → 14 passed.
- `agents validate` → OK; `agents status` resolves the symlinked active task.
- `git status` shows nothing under `knowledge/tasks/.active/` (gitignored as
  intended).

## Hypotheses / unverified

- `-printf` in `active_ids` needs GNU find; fine on this Linux host and CI, but
  non-portable to BSD find.

## Decisions

- Active = a symlink under `.active/`; `status:` records the lifecycle, with
  `completed` terminal. `validate` rejects a task that is both linked and completed.
- No `archive/`: completion is a status change, so the file history is the archive
  and the biggest possible `origin/main` never sees in-progress work.

## Attempts and outcomes

- `git mv` of an untracked checkpoint fails (`not under version control`); used a
  plain `mv` for the in-progress file and `git mv` for tracked ones.
- First `new` printed the same-branch nudge before the id, so `head -n1` grabbed
  the note; reordered to print the id first.

## Failed approaches (do not repeat without new evidence)

- Keeping a second, local copy of the checkpoint (the earlier "container" idea):
  rejected by the user because a working copy can diverge from the committed one.

## Open questions

- none

## Remaining tasks

- [x] Rewrite the CLI, migrate the tree, add `.gitignore`.
- [x] Update `SKILL.md`, `AGENTS.md`, `knowledge/index.md`, `conventions.md`,
      `pitfalls.md`, `decisions.md`, the two commands and the plugin.
- [x] Run `./gradlew check`, commit, then complete this task.

## Tests and verification

- `agents selftest` → 44/0; `node plugin-test.mjs` → 14/0; `agents validate` → OK.
- `./gradlew check` → BUILD SUCCESSFUL.
- `agents find` lists the branch's tasks (base: `origin/main`, `main`).
- `git check-ignore` confirms `.active/` is ignored.

## Next action

- none — committed and completed; the active symlink is removed.

## Completion report

- Outcome: every task is one committed file under `knowledge/tasks/`; in-progress
  work is a gitignored relative symlink under `.active/`, so no active task
  reaches the mainline and nothing can diverge. `archive/` is gone; completion is
  a status change. The CLI gained `find`, `reopen` and multi-`Task-ID` support.
- Verification: `agents selftest` 44/0; `plugin-test.mjs` 14/0; `agents validate`
  OK; `./gradlew check` successful; `git status` clean of `.active/`.
- Lessons: the layout change invalidated the installed Git hook (it baked in the
  old `.current` path) and the first commit lost its trailer — `install-hook
  --force` fixes it; noted in `knowledge/pitfalls.md`. `git mv` cannot move an
  untracked checkpoint.
- Follow-up: none. The plugin text now points at `agents find`; that change takes
  effect on the next `opencode service restart`.
