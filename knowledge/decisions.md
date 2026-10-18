# Decisions

Significant technical/process decisions and their rationale. Newest first. When a
decision is superseded, keep the entry and mark it superseded rather than deleting
it, so history stays readable.

## 2026-10-18 — No commit trailer; task ↔ commit is the task file's history

**Context.** A `Task-ID:` trailer is only a promise — nothing guarantees a commit
carries the right (or any) trailer — and the fail-safe Git hook that injected it was
an extra moving part. User: "there can't be a guarantee, and I don't like git hooks".

**Decision.** Drop the trailer requirement and the hook: `install-hook`,
`check-trailer`, `trailers` and `collect-ids` are removed. The mapping comes from
the task file's own history — `agents commits <TASK-ID>` lists the commits that
touched `knowledge/tasks/<id>.md` (with `--follow`), and `agents tasks <REV>` lists
the task ids a commit added or changed.

**Consequences.** The link is verifiable from Git rather than asserted in a commit
message, and it survives amend/rebase/squash: a squash commit owns every task file
it added, so one lookup returns them all. An uncommitted task has no commits yet.
Commit messages no longer carry a trailer, and the repository no longer installs any
Git hook.

## 2026-10-18 — One flat tasks dir; active work is a gitignored symlink

**Context.** The `active/` + `archive/` split implied upstream knowledge about which
tasks were in progress, and keeping a working copy of an active checkpoint next to
a committed one invites divergence. User: "in the upstream repository you will not
find any active task".

**Decision.** Every task is one committed file in a flat `knowledge/tasks/`.
In-progress work is marked by a gitignored relative symlink under
`knowledge/tasks/.active/` (`.active/.current` is the focus). A symlink is a
pointer to the single committed file, never a copy, so there is nothing to
diverge; `.active/` is gitignored so no active task reaches `origin/main`.
`archive/` is removed — completion sets `status: completed` and unlinks, leaving
the file in place.

**Consequences.** `agents` gained `find` (branch-local tasks, compared against
`origin/main` and `main` so a behind/ahead mainline cannot produce false
positives), `reopen`, and multi-`Task-ID:` commit support. `validate` now checks
the `.active/` state instead of an archive move.

> The multi-`Task-ID:` commit support was itself dropped by "No commit trailer;
> task ↔ commit is the task file's history" (above); only the layout stands.

## 2026-10-18 — Automate the workflow with an OpenCode plugin

**Context.** The scripts/commands-only design still required the human to remember
to create a task and to checkpoint before compaction.

**Decision.** Add `.opencode/plugins/task-context.js`, a dependency-free plugin
discovered from `.opencode/plugins/` with no configuration change. It uses three
hooks: `prompt` (brief a session once), `context` (remind every model call where
durable state lives, so the reminder survives compaction) and `compaction` (steer
the summarizer to keep task knowledge and drop noise). It deliberately only injects
instructions — it never reads files or runs commands, so it cannot fail on a
sandboxed runtime.

**Consequences.** `AGENTS.md` states that task creation/checkpointing is automatic.
Because the plugin cannot read the checkpoint, the authoritative state stays on
disk in `knowledge/` and the injected reminder makes the agent re-read it after
compaction; the summary itself carries the pointer and the salient decisions.
Activating or changing a plugin may need `opencode service restart`.

## 2026-10-18 — Compact on a context budget, not only on overflow

**Context.** `/compact` cannot be enforced before OpenCode compacts automatically,
and a 200k-token session is already too big to work in comfortably. The plugin's
`context` hook sees the assembled outgoing request, so the size is measurable there.

**Decision.** The plugin counts characters in the outgoing `system`/`messages`
(≈4 chars/token) and acts on two budgets: at 200k it injects a reminder to hand off
at the next natural boundary — finish the current step (so checks can pass), start
no new work, update the checkpoint, then compact — and at 250k it fails safe by
calling `ctx.session.compact({ sessionID })` itself (once per session, re-armed when
the context shrinks). It also registers a zero-argument `compact_session` tool that
calls the same `session.compact`, so the agent can compact deliberately after a
handoff — or at any natural boundary, budget or not (a finished, verified unit of
work before switching to unrelated work).

**Consequences.** Compaction runs at the next step boundary, never mid-request.
The reminder is injected per request and so repeats until the context shrinks; the
hard path may run before the checkpoint is fresh, which the injected note says (it
points back at the checkpoint). The 50k gap between the budgets is deliberate: it
leaves room to finish the current step up to passing checks before the fail-safe
fires. Thresholds `SOFT_TOKENS`/`HARD_TOKENS` and the
estimate constant are the only tuning knobs; `plugin-test.mjs` exercises both
budgets, the re-arm, and the tool.

## 2026-10-18 — Persistent task and knowledge system layout

> Partly superseded by "One flat tasks dir; active work is a gitignored symlink"
> (above): `tasks/{.current,active/,archive/}` became a flat `tasks/` marked by a
> gitignored `.active/` symlink dir, and a plugin was added.

**Context.** Long OpenCode sessions grew slow and expensive, and `/compact` could
drop verified findings. Everything important had to survive outside the active
conversation.

**Decision.**

- Artifacts live in a visible top-level `knowledge/` directory: `index.md`,
  `architecture.md`, `conventions.md`, `pitfalls.md`, `decisions.md`, and
  `tasks/{.current,active/,archive/}`. One new root directory, no hidden prefix.
- Tooling lives in OpenCode's predefined skill layout,
  `.opencode/skills/task-management/` (`SKILL.md`, `scripts/agents`,
  `references/task-template.md`), so it is discovered without touching the
  gitignored `opencode.json`.
- Task IDs are immutable `YYYYMMDD-<slug>-<suffix>` (no prefix). The active task is
  named explicitly in `knowledge/tasks/.current`; it is never guessed as "newest".
- No OpenCode plugin: the automated hooks exist, but a plugin would add an
  untestable runtime surface to a Kotlin repo. Checkpointing stays explicit via the
  `agents` CLI and the `/handoff` command.
- The `Task-ID:` commit trailer is added by an **optional**, fail-safe,
  locally-installed `prepare-commit-msg` hook rather than by rewiring Git history.
  (Removed 2026-10-18 — see "No commit trailer; task ↔ commit is the task file's
  history".)

**Consequences.** `AGENTS.md` keeps only always-on rules and points here; detailed
testing and workflow rules moved to `knowledge/conventions.md`. The layout deviates
from the spec's `.agents/tasks/...` paths to avoid a hidden directory and to reuse
OpenCode's skill convention.
