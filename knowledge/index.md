# Agent knowledge index

Load this file first when starting or resuming work. Then load **only** the files
this work needs, and verify their claims against the current code, tests and Git
state — notes go stale.

| Load | When |
| --- | --- |
| `knowledge/architecture.md` | you need the module map, data flow, key files, or how generated data is produced |
| `knowledge/conventions.md` | before writing or changing tests or non-trivial code, or when a workflow question comes up |
| `knowledge/pitfalls.md` | a result is surprising, a build/test misbehaves, or before trusting a "known" behavior |
| `knowledge/decisions.md` | before changing an established approach, or to understand why something is the way it is |

`AGENTS.md` at the repository root holds the rules that must apply to **every**
prompt (commands, sandbox policy, Git workflow, knowledge rules). Everything an
agent does not need on every turn lives here instead.

## Where a fact lives

Every fact has one home; other files cross-reference it instead of restating it.

- `architecture.md` — where it lives, how data flows, the load-bearing order.
- `decisions.md` — what must stay true and why (decision + guardrail).
- `pitfalls.md` — how to recognize a failure, its cause, its fix and evidence.
- `conventions.md` — how to do the work.

Promote a finding to the file that owns it, then delete the duplicate. A
decision keeps the constraint and its rationale; the mechanism is architecture,
the symptom is pitfalls.

## Compaction

OpenCode compacts the session automatically when the context fills
(`opencode.json`: `compaction.auto`). `.opencode/plugins/compaction.js` only steers
the summarizer to keep verified state, decisions and the next action, and to drop
transcripts. Run `/handoff` deliberately before a pause to promote reusable
findings into `knowledge/` and state the same points. There is no agent-initiated
compaction, no handoff file, no `knowledge/tasks/`, no task IDs and no checkpoints:
`knowledge/` holds the durable record and commit messages hold the per-change
detail.

## History

Per-change history lives in commit messages; rationale that outlives a change is
promoted into the files above, and a decision that no longer applies is deleted
from `decisions.md` rather than kept as history. Use `git log` and `git blame` to
trace it.
