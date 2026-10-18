---
name: Task Management
description: Create, resume, checkpoint and complete agent tasks; find tasks already covering a branch; load and curate project knowledge. Use at the start of non-trivial work, before compaction/handoff, and when finishing.
---

# Task management

Keep durable state in `knowledge/` instead of the conversation, so a fresh session
or `/compact` can resume from verified facts. The CLI lives at
`.opencode/skills/task-management/scripts/agents` (run it from the project root;
set `AGENTS_ROOT` to target another checkout). It is a plain bash script with no
dependencies.

Most of this is automatic: `.opencode/plugins/task-context.js` briefs each session,
reminds every model call to create/keep the checkpoint, watches the context budget,
and steers compaction to preserve task-relevant knowledge. Do not wait for the user
to ask for a task.

The CLI verifies itself: `agents selftest` runs the whole lifecycle in a throwaway
repository (and the plugin unit test when node is available). This tooling is
deliberately **not** part of `./gradlew check` — the app suite must never reach
into `.opencode/` or shell out to `bash`, so run `selftest` when you touch it.

## Layout

Every task is one committed file; local symlinks mark what is in progress:

```text
knowledge/tasks/<id>.md            every task, committed, whatever its status
knowledge/tasks/.active/<id>.md    gitignored symlink -> ../<id>.md (in progress)
knowledge/tasks/.active/.current   gitignored symlink -> ../<id>.md (the focus)
knowledge/tasks/.gitignore         ignores .active/
```

The committed file is the single source of truth; a symlink is a pointer, never a
copy. Nothing under `.active/` is committed, so no in-progress task reaches
upstream (`origin/main`). `agents new` creates the `.gitignore` if it is missing.

## Picking up work

Run `agents find` before creating a task: it lists the tasks that belong to this
branch — those absent from `origin/main` and `main`, or stamped with the branch.
If one of them covers the request, `agents resume <id>` it, or `agents reopen
<id>` if it was already completed, instead of starting a duplicate. Matching is a
judgement call on the prompt, not the tool's.

## When to create a task

Create one for any non-trivial change (feature, bug fix, refactor, investigation
that spans more than a couple of steps). Skip it for trivial edits, docs-only
tweaks, and pure Q&A.

## Identity

Task IDs are immutable and follow `YYYYMMDD-<slug>-<suffix>`:

- `YYYYMMDD` — creation date.
- `<slug>` — 2–4 lowercase words derived from the objective, not the history.
- `<suffix>` — 4 random lowercase alphanumeric characters, for uniqueness.

Example: `20261018-parser-nesting-k7m2`. There is no `T-` prefix. Generate the ID
once and never rename it, even if the objective changes. Use the same ID for the
checkpoint filename; the file's own history links it to its commits. A GitLab
issue URL is an optional reference stored in the checkpoint, never encoded in the
ID.

## Lifecycle

`Create → Work → Checkpoint → Verify → Complete → Promote knowledge`

1. **Create** — `agents new "<objective>"` (add `--issue URL` and `--branch b` if
   known). It writes `knowledge/tasks/<id>.md`, links it under
   `knowledge/tasks/.active/`, points `.active/.current` at it, and prints the id.
   If `agents find` shows a task that already covers the work, `agents resume`
   (or `agents reopen`) it instead of creating a duplicate.
2. **Work** — read `knowledge/index.md`, then only the files it points to for this
   task, then the active checkpoint. Update the checkpoint after a real discovery,
   decision, milestone, test result or scope change — not after every tool call.
3. **Checkpoint** — before `/compact`, a handoff or a pause, make the checkpoint
   complete enough for another session: verified state, findings with evidence,
   decisions, failed approaches, open questions, remaining tasks, tests, next action.
4. **Verify** — inspect the actual changes and run the relevant tests/linters.
   Record real outcomes and state clearly what could not be verified.
5. **Complete** — `agents complete <id>` after the acceptance criteria are met (or
   documented exceptions). It sets `status: completed`, removes the active
   symlink and keeps the file in place — nothing is moved or archived. Fill the
   Completion report first.
6. **Promote knowledge** — move reusable findings into `knowledge/*` using the
   rules in `knowledge/conventions.md`; keep task specifics in the checkpoint.

## Checkpoint schema

YAML frontmatter, then Markdown sections:

```markdown
---
task: 20261018-parser-nesting-k7m2
title: "Handle nested parser blocks"
created: 2026-10-18
updated: 2026-10-18
status: active            # active | blocked | paused | in-review | completed
branch: bjrke/parser-nesting
issue: https://gitlab.com/bjrke/factorio-optimizer/-/issues/42
completed:
---

## Objective
... ## Acceptance criteria ... ## Current state ... ## Verified findings ...
## Hypotheses / unverified ... ## Decisions ... ## Attempts and outcomes ...
## Failed approaches (do not repeat without new evidence) ... ## Open questions ...
## Remaining tasks ... ## Tests and verification ... ## Next action ...
## Completion report (Outcome / Verification / Lessons / Follow-up)
```

Presence in `knowledge/tasks/.active/` means "being worked on"; `status:` records
the lifecycle, with `completed` the terminal one. `agents validate` rejects a task
that is both linked and `completed`.

Record conclusions with evidence (command, test name, file path), and keep
verified facts, hypotheses and open questions clearly apart. Never store secrets,
tokens or credentials.

## Commands

```sh
agents new "<objective>" [--slug s] [--issue URL] [--branch b]
agents find [--base REF] [--branch B] [--text Q] [--active]
agents list [--all] [--status S]   # default: active tasks; --all: every task
agents status                      # current task + all active tasks
agents resume <TASK-ID>            # make an already-active task current
agents reopen <TASK-ID>            # continue a completed task (re-link, make current)
agents touch <TASK-ID>             # refresh the `updated` date
agents complete <TASK-ID>          # mark completed and unlink (file stays)
agents validate                    # ids, frontmatter, sections, .active/ state, index
agents commits <TASK-ID>           # commits that touched the task's file (task -> commit)
agents tasks <rev>                 # task ids changed by a commit (commit -> task)
agents stale [--days N]            # report active tasks not updated in N days
agents selftest                    # end-to-end test in a throwaway repo
```

`validate` fails on a malformed id or filename, missing frontmatter/sections, a
dangling symlink, a `.current` that is not linked as active, a completed task that
is still linked, or a `knowledge/index.md` link to a missing file. `stale` only
reports; it never deletes.

## Git integration

Task and commit are linked by the task file's own history — there is no commit
trailer and no hook:

- `agents commits <TASK-ID>` — the commits that touched
  `knowledge/tasks/<id>.md` (follows renames).
- `agents tasks <REV>` — the task ids a commit added or changed, from its file
  list.

Because the link is the file, it survives amend/rebase/squash: after a squash the
single commit owns every task file it added, so `agents tasks <squash-commit>`
lists them all. The ID never depends on a commit hash. An uncommitted task has no
commits yet, and `agents commits` says so.

## Session handoff / compaction

Before compaction or a pause, run `/handoff` (or update the checkpoint by hand).
After compaction or in a fresh session, reconstruct state from
`knowledge/index.md`, the relevant knowledge files, the active checkpoint and the
live repository — not from a replay of old tool output. Treat checkpoint facts as
stale until re-verified against code, tests and Git.

### Context budget

The plugin measures the outgoing request on every model call and acts on two token
budgets, so compaction happens on purpose rather than only when OpenCode has to:

- **200k (soft)** — it adds a system reminder telling the agent to hand off at the
  next natural boundary: finish the current step (e.g. let the checks pass), update
  the active checkpoint, then compact. Start no new work first. The reminder repeats
  until the context shrinks.
- **250k (hard)** — a fail-safe: the plugin calls `session.compact` itself, once
  per session, and says so in the reminder. It re-arms after the context drops
  below the soft limit (i.e. after a compaction).

Neither budget is a precondition: compacting at a natural boundary is always
allowed. Once a unit of work is finished and verified, or before switching to
unrelated work, update the checkpoint and compact — deliberate compaction keeps the
checkpoint authoritative.

The plugin also registers a `compact_session` tool (no arguments). Calling it asks
OpenCode to compact the session at the next step boundary; use it right after the
handoff, or tell the user to run `/compact`. Compaction is per session: a fresh
session starts from the checkpoint, not the summary.
