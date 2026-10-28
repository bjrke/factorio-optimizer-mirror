# Decisions

Significant technical and process decisions and their rationale, newest first.
This file records **current constraints only**. Each entry states the decision and
the guardrail that must survive; mechanics belong in `architecture.md` or
`pitfalls.md`, and the pre-change history belongs in the commit message. Keep an
entry under ~20 lines and prepend a new one. A change that supersedes an entry
rewrites or deletes it — never add a "supersedes" note.

## 2026-10-27 — Port tables are written in the placed orientation

**Decision.** `prototypeFluidPorts` lists each machine's `fluid_boxes` already turned into the
orientation `blueprintMachineDirection` places the machine in, so callers use the tiles as-is.
The order is load-bearing: the inputs come first in the order the recipe's fluid ingredients take
them, then the outputs in result order. A lane's side comes from the port the recipe assigns to
the fluid, in that order (never alphabetical); `inserterPortColumns` reserves only the ports the
recipe's fluids use. A fluid target with a side output port is routed to a marker like an input,
not left as a pipe stub.

**Guardrail.** Never re-rotate or coordinate-sort the table: rotating and sorting it is what
produced wrong port assignments for the cryogenic plant, foundry and oil refinery. Never infer an
input from "north/west" or sort fluids by name; a wrong box shows up as one fluid that never
arrives while the other works. The four reported URLs are pinned in
`BlueprintFluidPortLayoutTest`.

**See.** `architecture.md` §Blueprint layout; `pitfalls.md` "Layout and fluids";
`plan/BlueprintMachine.kt`.

## 2026-10-27 — Every fluid port on an edge gets its own lane

**Decision.** A row side places one lane per fluid that has a port there, in recipe order, each two
rows further out than the last. The outer lanes' shafts dive under the inner lane rows to reach
their own ports, and every lane marker joins one west column (the westmost inline fluid marker when
the plan has no solid import). There is no route for a second fluid on the same edge.

**Guardrail.** A pipe-to-ground end joins a surface pipe only on its mouth, so two fluids may share
a wall. `touchesForeign` and `validateLayout` must read the mouth (`Plumbing.joinsAt`), or a lane's
port pipe looks like a two-fluid merge. A shaft may only bury a pipe-to-ground end that runs on the
other axis.

**See.** `planning/RowFluidLane.kt`, `planning/FluidLanePlacer.kt`, `plumbing/FluidLaneFeed.kt`,
`layout/LayoutPipes.kt`, `layout/validation/LayoutValidationTransport.kt`, `routing/ImportTrimmer.kt`.

## 2026-10-27 — A routed fluid marker stays on its port's side

**Decision.** The routed fluid fallback places its marker just west of the first sink and searches
the marker row **outward from the port's edge**: north for a north port, south for a south port,
south for a side port. The scan skips only rows a previous marker claimed or rows whose pipe block
is blocked.

**Guardrail.** Never scan a single direction. A north-edge second fluid (the chemical plant's
second top corner) used to be marked below the whole row, and its feed then looped around the
factory; the reported light-oil URL now marks heavy oil above the row, beside the water lane.

**See.** `plumbing/FluidFeeder.kt` (`fluidMarker`, `portEdge`), `BlueprintFluidLayoutTest`,
`BlueprintFluidPortLayoutTest`.

## 2026-10-26 — Import belts are routed before produced deliveries

**Decision.** The solid import routes are laid after the fluid lanes and their source markers, and
before the routed fluid fallback and the produced solid deliveries. All solid sources are still
reserved and placed up front, imports first, so an early route cannot take a later source or
marker. An import therefore keeps the direct path along its marker row; a produced trunk tunnels
under it instead of forcing the import to turn. The target output keeps its existing placement.

**Guardrail.** Do not route produced deliveries before imports, and do not run the solid imports
before the fluid lanes/markers: the pipe lane geometry is fixed first and only the routed fluid
fallback yields to an import. A single turn in an import makes `lastStraightEast` collapse, so the
trimmer cannot move all markers to one column; keep the import's run straight east from its
connector to the lane. The reported uranium-rounds URL pins the straight uranium-238 import.

**See.** `routing/DeliveryPreparation.kt`, `routing/DeliveryRouter.kt`,
`routing/ImportTrimmer.kt`, `plumbing/FluidFeeder.kt`, `BlueprintLayoutIntegrationTest`.

## 2026-10-26 — A routed tunnel end connects through a belt

**Decision.** `validateLayout` requires the back tile of every routed underground belt input to
hold a same-material conveyor (or loader output) facing into the run, and the front tile of every
routed output to hold a same-material conveyor (or loader input). A tunnel end may not feed another
tunnel end directly. Connector ends are the exception, and are recognized by their 2-tile label or
4-tile chest partner with the marker/chest/inserter halfway between the two ends.

**Guardrail.** A corridor may not cut a belt that feeds a tunnel entrance. Evidence: the reported
uranium-rounds URL, where compaction removed `(39,42) SOUTH` and left the input at `(39,17) SOUTH`
beside `(39,16) EAST`; the router was correct before compaction. A candidate that only fails after
stretching is ruled out by its seam path and the pass tries the next corridor — aborting the whole
pass leaves a long straight belt uncollapsed (the `0.3` uranium variant). Do not weaken the check
to "the back tile is occupied", and do not exempt a route end merely because some connector is
adjacent — `(38,17) SOUTH` can sit beside another import's loader.

**See.** `layout/validation/LayoutValidationUnderground.kt`,
`compaction/CompactionPass.kt`, `BlueprintLayoutAssertions.kt`, `LayoutCompactionTest`.

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
