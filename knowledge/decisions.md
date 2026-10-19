# Decisions

Significant technical/process decisions and their rationale. Newest first. When a
decision is superseded, keep the entry and mark it superseded rather than deleting
it, so history stays readable.

## 2026-10-18 — Belt-fed allocations are sized by the plan's rate, not by whole consumer machines

**Context.** The user replayed `#/2.5/logistic-science-pack/CFSXbmr/1/1`. The 1:1
bands were right, but the blueprint held an *additional* electronic-circuit and
inserter factory, "which have unconnected output belts", and a five-machine
iron-gear row where only one more assembler "would be necessary". The belted split
had sized every allocation as `ceil(builtConsumerMachines × inputPerMachine ÷
outputPerMachine)` — enough to run each built consumer machine at *full* rate. For
a complete one-machine foundry (8 gear/s when it runs) that is `ceil(3.2) = 4` gear
machines although the plan only draws 0.83 gear/s, and for the nine science machines
it added a second inserter row even though the band's own inserter machine (2.5/s)
already covered their 1.875/s. `producerOf` supports one belt source per material,
so a redundant belt-fed allocation keeps its product belt while no delivery starts
on it: the unconnected belt.

**Decision.** Size a belt-fed allocation by the plan's own rate demand — the same
`consumption × scale` an import draws — and subtract what a band's *output* already
puts on a belt:

`machines = max(ceil(beltedRate ÷ outputPerMachine) − bandOutputMachines,
need − builtMachines)`, capped at `MAX_COPIES`, and built only when at least one
consumer is not direct-fed. A band's inserted (internal) link never counts as belt
output; a recipe whose consumers are all inserted gets no belt row at all.

**Consequences.** The reported plan shrinks from 20 to 14 machines (gear 2 in total,
EC 1, inserter 1, cable 1, foundry 1, science 9), 322 belts → 220, and no belt chain
is left that no inserter takes from. The layout still assumes one belt source per
material; a material that both a band's output and a belt row produce would need two
deliveries (a merge), which no fixture needs yet — the invariant is asserted per
plan (`realBlueprintsPutEveryMaterialOnAtMostOneBelt`). Supersedes the
belted-allocation bullet of the 2026-10-18 direct-feed entry above.

## 2026-10-18 — Direct-feed bands group recipes by rate, not by machine count

**Context.** A recipe pair like `copper-cable` → `electronic-circuit` was always fed
over a belt, even when the machines stand in a ratio that lets an inserter move the
material straight from one machine into the next. The user asked for that direct
insertion whenever it is feasible, routing the recipes as one unit ("always on when
feasible", never as a chooser, never an error).

**Decision.** A new pure module `DirectFeed.kt` searches for links (a solid material
whose single in-plan producer can be inserted into a consumer) and groups them into
*belt-free bands*:

- Eligibility comes from **per-machine rates**, never from machine counts:
  `outputPerMachine = speed × (1 + productivity) × items / time` (the bonus counts —
  it is what a directly-fed consumer really receives) and
  `inputPerMachine = speed × items / time`.
- A rate `r` is accepted when a whole-machine cycle `p : q` fits
  `r / 1.05 ≤ p / q ≤ r × 1.25` with `p + q ≤ 6`; ties go to the smallest sum, then
  to the closest rate. Otherwise the link stays belted. This is the user-verified
  rule (`0.8:1` and `1.05:1` → `1:1`, `1.1:1` rejected, `1:20` rejected).
- The band is a **window of a periodic arrangement**: a cycle is found, repeated
  often enough to cover every member's whole machine need, then cut to the shortest
  length at which no consumer starves and no producer is left without a consumer
  next to it (`A B C B A` for a 1:2:1 need). `GROUP_MAX_MACHINES = 18` bounds it;
  a group that cannot fit stays belted.
- Every machine is built **complete** (the band's share is rounded up); interface
  inserters are placed in the gap column on the producer's side, as many as the
  flow needs (never a rejection), and the row keeps its own outputs.
- A band's internal material gets **no lane, sink, belt or belted allocation**: the
  band's producer allocation must not act as a belt source. A material with another
  consumer still gets one belted allocation per belted consumer plus what is left of
  the plan's count.
- `PlannedRecipe.directInsert` was removed; `BlueprintPlan.bands` is the single
  source of truth, and a band is laid out by its own code path (`placeBand`), so a
  plan without bands lays out exactly as before.

**Consequences.** `Row` became a band of heterogeneous machines and `rows` is keyed
by allocation index; only belted allocations fill `producerOf`, so belts, lanes and
sinks are unchanged for the belted part of a plan. Grouping fluid-touching recipes
is deferred: a band puts a machine's fluid port between two machines where the
current fluid feed cannot reach it, so those recipes keep their belt until the fluid
feed is reworked (a separate session). Also deferred: line-breaking a band that is
too wide (up to 18 machines stand in one row, which can force belt crossings), and
**mixed-footprint bands** — `arrange`/`placeBand` assume every machine in a row has
the same size, which keeps e.g. `iron-gear-wheel` (3×3) from inserting straight into
a `foundry` (5×5) so that the gear row has to be belted. The user's note on how it
can work: since the *smaller* machine feeds the bigger one, the smaller one needs no
output belt at all, and if it needs belt access, it can stand in the middle with
long inserters reaching the lanes above and below. Merged on branch
`bjrke/direct-insertion-groups`; see
`tasks/20261018-direct-insertion-recipe-groups-y5og.md`.

## 2026-10-18 — Belt end is a `BeltEnd` selection, not a cheats flag

**Context.** The web UI had one "test chests" checkbox (`SelectionFlag.CHEATS`)
that only switched the infinity chest on. The request was to pick the solid belt's
end marker from icons and to add real chest connectors reproducing blueprints the
user had pasted.

**Decision.** Replace the boolean with a `BeltEnd` enum whose values each carry a
URL `code` and a prototype `identifier`: `constant-combinator` (no code, the
default), `infinity-chest` (`X`, the old cheats mode), `buffer-chest` (`H`),
`storage-chest` (`G`), `wooden-chest` (`W`) and `steel-chest` (`D`). A chest marker
is a five-tile connector — `UG-in · inserter · chest · inserter · UG-out`, the belt
tunnelling under the chest; an input marker feeds the belt out of the chest, an
output marker fills it, and the plain chests buffer both ways. `infinity-chest`
keeps the `X` code and the `--cheats` CLI alias, so existing links and scripts keep
working.

**Consequences.** `selectionString` gains a `beltEnd` parameter and
`SelectionFlag` drops `CHEATS`. The blueprint model grows `mirror`, `bar`,
`requestFilter`, `circuitCondition` and `circuitConditionEnabled` to reproduce the
pasted blueprints faithfully (`import_from` is deliberately omitted — it is a
space-platform field). Connector inserters are powered, so the pole bound that
normally keeps poles out of the import extension is skipped for a chest marker
(see `pitfalls.md`). Chests need ground, so a space platform offers only the label
and any chest code in a URL or `--belt-end` clamps to it. Merged as `9560a5d`
(`!145`); see `tasks/20261018-record-chest-belt-end-2fsg.md`.

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
