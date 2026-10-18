# Agent knowledge index

Load this file first when starting or resuming a task. Then load **only** the
files this task needs, and verify their claims against the current code, tests
and Git state — checkpoints and notes go stale.

| Load | When |
| --- | --- |
| `knowledge/architecture.md` | you need the module map, data flow, key files, or how generated data is produced |
| `knowledge/conventions.md` | before writing or changing tests or non-trivial code, or when a workflow question comes up |
| `knowledge/pitfalls.md` | a result is surprising, a build/test misbehaves, or before trusting a "known" behavior |
| `knowledge/decisions.md` | before changing an established approach, or to understand why something is the way it is |
| `knowledge/tasks/<TASK-ID>.md` | resuming that task (find it via `agents find` or `agents status`) |
| `.opencode/skills/task-management/SKILL.md` | the full task lifecycle, checkpoint schema and tool usage |

`AGENTS.md` at the repository root holds the rules that must apply to **every**
prompt (commands, sandbox policy, Git workflow, this workflow). Everything an
agent does not need on every turn lives here instead.

## Automatic layer

`.opencode/plugins/task-context.js` (auto-loaded by OpenCode, no config change)
briefs each session once, reminds every model call where durable state lives
(so the reminder survives compaction), watches the context budget (a reminder at
200k tokens, a plugin-requested compaction at 250k), and steers the summarizer to
keep task-relevant knowledge and drop noise. It registers a `compact_session` tool
so the agent can compact at the budget or at any natural boundary (a finished,
verified unit of work before switching). It never reads files or runs
commands. Test it with `node .opencode/skills/task-management/scripts/plugin-test.mjs`.
Changes to a plugin may need `opencode service restart` to take effect.

## Active tasks

Every task is one committed file in `knowledge/tasks/`. Work in progress is marked
by a gitignored symlink under `knowledge/tasks/.active/`, and
`knowledge/tasks/.active/.current` names the active task; never guess it as "the
newest file". Run `.opencode/skills/task-management/scripts/agents status` (or
`list`), and `agents find` to see the tasks that belong to the current branch.

## Finding older tasks

There is one flat `knowledge/tasks/` directory, so search it selectively — do
**not** load it in bulk:

```sh
.opencode/skills/task-management/scripts/agents find --text "socket timeout"
grep -rl "socket timeout" knowledge/tasks/
```

A commit and a task are linked by the task file's own history: `agents commits
<TASK-ID>` finds the commits that touched it, and `agents tasks <rev>` the tasks a
commit changed. A squash commit owns every task file it added.
