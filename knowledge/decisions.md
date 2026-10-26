# Decisions

Significant technical and process decisions and their rationale, newest first.
This file records **current constraints only**. Each entry states the decision and
the guardrail that must survive; mechanics belong in `architecture.md` or
`pitfalls.md`, and the pre-change history belongs in the commit message. Keep an
entry under ~20 lines and prepend a new one. A change that supersedes an entry
rewrites or deletes it — never add a "supersedes" note.

## 2026-10-26 — Fluid ports follow the prototype rotation

**Decision.** `blueprintMachineFluidPorts` is the prototype `fluid_boxes`
`production_type` **after** the blueprint rotation, not a row-side reading of the
edge. `blueprintMachineDirection` turns machines whose prototypes do not fit the
row: the cryogenic plant, foundry and oil refinery by 180°, the electromagnetic
plant by 90° clockwise. A lane's side comes from the port the recipe assigns to
the fluid, in recipe order (never alphabetical), and `inserterPortColumns` reserves
only the ports the recipe's fluids use. A fluid target with a side output port is
routed to a marker like an input, not left as a pipe stub.

**Guardrail.** Never infer an input from "north/west" or sort fluids by name; a
wrong box shows up as one fluid that never arrives while the other works. The
four reported URLs are pinned in `BlueprintFluidPortLayoutTest`.

**See.** `architecture.md` §Blueprint layout; `pitfalls.md` "Layout and fluids".

## 2026-10-26 — Fluid feeding: one lane per row, the route is the fallback

**Decision.** A row with a fluid port on its north or south edge gets one lane two
rows beyond its outermost belt (there is no free row between belts and machines):
a junction pipe above every port, a horizontal pipe-to-ground run across every gap
and one vertical shaft per port that buries itself under belts and inserters. The
lane is primary; the A* feed only serves a side port, a machine without a port
table or a shaft that found none. Fluid outputs mirror the inputs, machines touch
in a fluid row, and a reserved port never costs an input inserter. The marker sits
in the common import column, pinned with its link and lane head.

**Guardrail.** Do not put a pipe or an inserter on the inserter row, and do not
spend an input inserter on a port. A stage places fluid sources only through
`LayoutBoard.placeFluidSource`, which owns the cheat/marker split; `FluidFeed`
carries the sink role so the trimmer cannot turn a drain into a supply.

**See.** `architecture.md` §Blueprint layout; `planning/FluidLane.kt`,
`planning/RowFluidLane.kt`, `planning/FluidLanePlacer.kt`,
`plumbing/FluidLaneFeed.kt`, `routing/FluidFeedLinker.kt`.

## 2026-10-24 — The row is the occurrence, not the recipe

**Decision.** A plan occurrence is a plain-data `PlannedRow`: `machines` left to
right plus the band `links`. A plain row repeats its recipe once per machine; a
band is its machine sequence plus links. `BlueprintPlan.recipes` is a derived view
and band membership and order are the `rows`/`machines` lists themselves. No `Map`
or `Set` is keyed by a planned recipe or a row: producer lookup scans `plan.rows`
and compares `row.plannedRow == producer` by value.

**Guardrail.** Do not reintroduce an allocation index, reference identity or a
recipe-keyed map: structurally identical rows must compare equal and stay
interchangeable. The plan is bounded by `MAX_BLUEPRINT_RECIPES` and a band by
`GROUP_MAX_MACHINES`, so the scans stay smaller than the hash tables they replace.

**See.** `architecture.md` §Blueprint layout; `plan/BlueprintPlan.kt`,
`planning/Row.kt`.

## 2026-10-22 — The package tree is the domain model

**Decision.** Packages are domains and the arrows point one way, from the grid
vocabulary up to the artifact (`architecture.md` "Package dependencies"). A symbol
no other package needs is `internal`; every cross-package reference is an explicit
import of the names used. Enforcement is the compiler plus review — no app test,
and `ForbiddenImport` cannot express the edges. The package budget is the file
count (past 10 files is a signal to split, 15 the ceiling); there is no line
budget beyond `LargeClass` (500).

**Guardrail.** Never widen a row or add a package edge to silence a failure; a new
edge is a deliberate table change. A future automated guard belongs at the
Gradle/build level and must be proven by injecting the violation it rejects.

**See.** `architecture.md` §Source layout and §Package dependencies;
`pitfalls.md` for moving a file.

## 2026-10-20 — The layout is explicit stages with one ordered `build()`

**Decision.** The layout splits by ownership of state, not by line count: the
board and its primitives, the router, the stretcher, the compaction and the row
planner. Every stage takes one `planning/LayoutStage` context; shared mutable
state lives in one small holder. There is no stage interface and no DI container,
and no KMP module split — it would duplicate `expect`/`actual`, resources and test
targets for ~7k lines of `commonMain`.

**Guardrail.** The `build()` order is load-bearing: pipes before belts, the import
trim before the underground stretch, compaction before the poles. Any further
split must prove itself neutral against `BlueprintLayoutSnapshotTest` at the
pre-change commit.

**See.** `architecture.md` §Blueprint layout;
`jvmTest/…/blueprint/BlueprintLayoutSnapshotTest.kt`.

## 2026-10-20 — Detekt is the quality gate, without baselines

**Decision.** Detekt is the gate, configured through `buildUponDefaultConfig` plus
`config/detekt/detekt.yml`: `LargeClass` 500, `MagicNumber` off, guard clauses
excluded from `ReturnCount`, ≤ 3 loop jumps and ≤ 4 destructured names. `check`
runs the type-resolved JVM tasks and the light JS source-set tasks; the whole-tree
`detekt` task stays available for one-off runs. There are **no committed
baselines**.

**Guardrail.** Every finding fails `check`. Fix it by extracting a named
responsibility; suppress only a proven false positive with a written reason.

**See.** `config/detekt/detekt.yml`; `conventions.md` §Code size and file layout.

## 2026-10-19 — No per-task checkpoint layer

**Decision.** There is no `knowledge/tasks/`, no task IDs and no checkpoints.
`knowledge/` holds the durable record and commit messages hold the per-change
detail; `/handoff` is the explicit gesture that promotes findings and states the
next action. The agent never compacts on its own; OpenCode's `compaction.auto`
decides when.

**Guardrail.** Do not reintroduce a task file or a checkpoint mechanism to carry
state between turns.

## 2026-10-18 — Belt-fed planned recipes are sized by plan demand

**Decision.** A belt-fed planned recipe is sized by the plan's own rate demand,
not by whole consumer machines:

`machines = max(ceil(beltedRate ÷ outputPerMachine) − bandOutputMachines,
need − builtMachines)`, capped at `MAX_COPIES`, and built only when at least one
consumer is not direct-fed. A band's inserted (internal) link never counts as belt
output.

**Guardrail.** Keep one belt source per material; a fully inserted recipe gets no
belt row. `realBlueprintsPutEveryMaterialOnAtMostOneBelt` (in
`BlueprintLayoutIntegrationTest`) asserts the invariant.

**See.** `special/DirectFeed.kt` (`belted`); `architecture.md` §Blueprint layout.

## 2026-10-18 — Direct-feed bands group recipes by rate

**Decision.** `special/DirectFeed.kt` turns a solid material whose producer and
consumer stand in a small whole-machine cycle into a belt-free band, judged by
**per-machine rates** (productivity included), never machine counts: a rate `r` is
accepted when `r / 1.05 ≤ p / q ≤ r × 1.25` with `p + q ≤ 6`; ties go to the
smallest sum, then the closest rate. The band is a window of a periodic
arrangement cut to the shortest length where no consumer starves and no producer
is left without a consumer. Bounds: `GROUP_MAX_MACHINES = 18`, `MAX_BAND_RECIPES = 3`,
`MAX_BAND_LINKS = 3`. Every machine is built complete; a band's internal material
gets no lane, sink, belt or belted planned recipe.

**Guardrail.** Do not group by machine count or give a band's internal material a
belt. A recipe that takes **or** produces a fluid stays belted: a band puts the
port in a gap the feed cannot reach. Open: line-breaking a too-wide band and
mixed-footprint bands.

**See.** `architecture.md` §Blueprint layout; `special/DirectFeed.kt`,
`band/BandSearch.kt`.

## 2026-10-18 — Belt end is a `BeltEnd` selection

**Decision.** The solid belt's end marker is a `BeltEnd` value
(`constant-combinator` by default, `infinity-chest` = the old `X`/`--cheats` mode,
`buffer-`, `storage-`, `wooden-` or `steel-chest`), selected in `Selection` and
encoded in the URL. A chest marker is the five-tile
`UG-in · inserter · chest · inserter · UG-out` connector; plain chests buffer both
ways, infinity chests source/sink. A space platform offers only the label and
clamps every chest code to it.

**Guardrail.** Keep the `X` code and `--cheats` alias working; connector inserters
are powered, so the pole bound that keeps poles out of the import extension is
skipped for a chest marker.

**See.** `architecture.md` §Blueprint layout; `layout/BeltEnd.kt`,
`beltend/BeltEndConnector.kt`.

## 2026-10-18 — Durable knowledge lives in a visible `knowledge/` directory

**Decision.** Durable knowledge lives in the visible top-level `knowledge/`
directory (`index.md`, `architecture.md`, `conventions.md`, `pitfalls.md`,
`decisions.md`); `AGENTS.md` keeps only always-on rules and points here. Agents
load `index.md` first, then only the files the work needs.

**Guardrail.** Promote only reusable, stable and verified findings; keep per-change
detail in the commit message and delete a decision that no longer applies instead
of archiving it.
