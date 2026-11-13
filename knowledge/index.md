# Agent knowledge index

Load this file first when starting or resuming work. Then load **only** the files
this work needs, and verify their claims against the current code, tests and Git
state — notes go stale.

| Load | When |
| --- | --- |
| `knowledge/code.md` | you need the module map, the package dependency table, the package budgets, or are moving a file |
| `knowledge/layout.md` | you change the blueprint pipeline, a route, compaction, poles, belt ends or the blueprint output |
| `knowledge/lanes-fluids.md` | you change belt lanes, mixed rows, import trimming, fluid ports or fluid lanes |
| `knowledge/planning.md` | you change the optimizer, selection, imports, direct feed or the band search |
| `knowledge/build.md` | a build or test task misbehaves, or you regenerate game data or profile |
| `knowledge/testing.md` | before writing or changing tests: suites, fixtures, source sets, jsTest |
| `knowledge/conventions.md` | you write or restructure code, or need the Git and knowledge-promotion rules |
| `knowledge/decisions.md` | a constraint spans files, or you need why something is the way it is |

`AGENTS.md` at the repository root holds the rules that must apply to **every**
prompt (commands, sandbox policy, Git workflow, knowledge rules). Everything an
agent does not need on every turn lives here instead.

## Where a fact lives

Every fact has one home, chosen by area; other files cross-reference it instead of
restating it. A trap goes into the `## Traps` section of the file that owns the
area.

- `code.md` — where the code lives and which package may import which.
- `layout.md` — the pipeline, the grid, routing, compaction and the output.
- `lanes-fluids.md` — how a row's belts and fluid lanes are shaped.
- `planning.md` — how the optimizer chooses recipes and builds bands.
- `build.md` — the Gradle build, generated data, profiling and the JDK.
- `testing.md` — how tests are placed, built and asserted.
- `conventions.md` — how the work is done: code shape, Detekt rules, naming,
  refactors, Git.
- `decisions.md` — the constraints no single owner file can carry, and why
  (decision + guardrail); every mechanism it names lives in that symbol's KDoc.

Promote a finding to the file that owns it, then delete the duplicate. A decision
keeps the constraint and its rationale; the mechanism is the code, the symptom is
a trap.

## Compaction

OpenCode compacts the session automatically when the context fills
(`opencode.json`: `compaction.auto`). `.opencode/plugins/compaction.js` only steers
the summarizer to keep verified state, decisions and the next action, and to drop
transcripts. Run `/handoff` deliberately before a pause to promote reusable
findings into `knowledge/` and state the same points. There is no agent-initiated
compaction, no handoff file, no `knowledge/tasks/`, no task IDs and no checkpoints:
`knowledge/` holds the durable record and the commit message holds the change
history.

## History

Commit messages hold the change history; rationale lives in the KDoc it governs
and is promoted into the files above when it outlives the change. A decision that
no longer applies is deleted from `decisions.md` rather than kept as history. Use
`git log` and `git blame` to trace the change itself.
