# Decisions

Significant technical and process decisions and their rationale, newest first.
This file records the **current** reasoning only: a superseded decision, and the
per-change narrative behind each decision, lives in the commit messages and
`git log`. Anything here should still constrain the code today.

## 2026-10-24 — The row is the occurrence, not the planned recipe

**Context.** Direct-feed bands and belt rows can both build the same recipe, so
`BlueprintPlan` entries could not be told apart by recipe value. The first fix
used the list position as an `Int` "allocation"; the second used the object's
reference identity. Both are hidden keys: an identity object works like an index
in a map. The recipe's output, machine count and rate cannot distinguish the
occurrences either — a band and a belt row can have the same machine count and
the same product.

**Decision.** The occurrence is the **row**, and a row is plain data:

```kotlin
data class PlannedRecipe(val production: FactoryProduction)

data class PlannedRow(
    /** One entry per machine, left to right; a plain row repeats the same recipe. */
    val machines: List<PlannedRecipe>,
    /** The direct insertions of a band; empty for a plain row. */
    val links: List<PlannedLink> = emptyList(),
)
```

`BlueprintPlan` holds `rows: List<PlannedRow>`, not a global recipe registry.
`PlannedRecipe` is a data class again; its machine count is the number of times
it appears in its row (`PlannedRow.machinesOf`), so it cannot disagree with the
row. A plain row is a one-recipe list repeated once per machine; a band is its
machine sequence plus links. `BlueprintPlan.recipes` is a derived view for
callers that only need the recipes.

No `Map` or `Set` is keyed by a planned recipe or a row:

- `producerOf(material)` scans `plan.rows` for the first row exporting it; the
  placed row is found by `row.plannedRow == producer`, a value comparison;
- band membership and order are the `rows`/`machines` lists themselves, so
  `RowPlanner` no longer reconstructs them;
- depth is a DFS with a `List<Recipe>` path as the cycle guard, no memo map.

The plan is capped at `MAX_BLUEPRINT_RECIPES = 8` recipes and a band at
`GROUP_MAX_MACHINES = 18`, so the scans are smaller than the hash tables they
replace. `BlueprintPlan.beltMaterials` (a list) answers "is this material
produced at all?"; `BlueprintPlan.producerOf` keeps the old first-producer
semantics, and `producerRecipeOf` the depth-order producer.

**Consequences.** Two structurally identical rows compare equal and are
interchangeable (same recipes, machine counts, links and geometry); they are not
collapsed by any map or set. A duplicate recipe in two different rows is simply
two lists of values. Prose and fields say "planned recipe" /
`machine.plannedRecipe` / `row.plannedRecipes`, not "allocation".

## 2026-10-23 — Fluids are built as one lane above (or below) the belts

**Context.** `FluidFeeder.feed()` runs after the rows and their edges, so
`fluidEdge()` finds every machine edge already carrying the inserters
`MachineInserterPlacer` put there. The first attempt put a pipe line on the
inserter row itself and moved inserters off the port tiles; the user rejected it:
an AM3's north edge always carries exactly underground-belt exit + short + long
inserter, and the machine port tile is that same inserter row.

**Decision.** Each row that takes (or gives) a fluid through a port the table puts
on its north (or south) edge gets **one lane**:

- the lane row lies **two rows beyond the outermost belt** of the row: `topmost
  belt − 2` on the north side, `last belt below + 2` on the south side. There is no
  free row between the belts and the machines, so a lane placed there would
  displace a belt;
- the lane row has a **plain junction pipe above every port**, joined by a
  **horizontal pipe-to-ground run across every gap** (the two tiles between
  touching machines' ports carry the two ends directly next to each other,
  `pUUp`; a wider gap buries the tiles between them). A surface pipe would wall
  the row off; the run is what the player can walk across. A pipe-to-ground may
  surface in the very next tile, unlike an underground belt
  (`validateUndergrounds` allows distance 1 for pipes only);
- the lane marker sits in the **same west column as the solid import markers**. It
  is placed inline (`oCo`) first; `ImportTrimmer` later moves it into the common
  import column and `FluidFeedLinker` joins its output to the lane head with
  pipe-to-ground runs along the lane row. A belt, underground belt or inserter
  crossing the lane row in the west corridor is buried under by the link; only a
  non-buryable entity at the source or a run end keeps the inline marker. The
  marker, link and head are pinned, so compaction shifts them as one column;
- every port is reached by one **vertical shaft**: its top end stands **one row
  inside** the lane (lane row + 1 north, lane row − 1 south), its bottom end on
  the port tile. The junction pipe above the end stays a surface pipe;
- the shaft buries itself under both belt rows and the inserter row; a belt or an
  inserter over a run is passed under, never moved;
- a shaft that finds no room in its column is **skipped** — its port alone falls
  back to the routed feed. The lane is primary; the A* route is only the fallback
  for a side port, a machine without a port table, or a skipped column;
- a reserved fluid port **must not cost an input inserter**: `MachineInserterPlacer`
  spreads the short/long pattern over the free columns and falls back to the
  machine's last column (normally kept for a pole) when the port took one;
- fluid **outputs** mirror the same code on the south side; `placeTargetOutput`
  stays for a solid target only (the fluid feeder has its own);
- machines **touch** in a fluid row (`stride = size.width`): the lane needs no
  horizontal gap.

**Consequences.** `planning/FluidLane.kt` holds the board-free shape (lane row,
junction pipes, horizontal runs and shaft pairs), `planning/RowFluidLane.kt` which
fluid a row gets and the port per machine, `planning/FluidLanePlacer.kt` the board
side (marker, ends, tunnel index, skip-on-collision) and
`plumbing/FluidLaneFeed.kt` ties lanes to the feeder; `routing/FluidFeedLinker.kt`
draws the marker-to-head link. `RowShape` lives in `Row.kt` and each
`PlacedMachine` carries its `plannedRecipe`, so the lane code reads the prototype
and ingredients without a `BlueprintPlan`. The port rows stay whatever
`blueprintMachineFluidPorts` says; it agrees with `inserterPortColumns` again
(`blueprintMachineEdge`/`PortFlow`). A row whose machine has a port only on a side
edge keeps the routed feed — the electromagnetic plant is fed through its top
`BOTH` port instead of its west third-row port.

## 2026-10-26 — Machine ports follow the prototype, and the blueprint turns the machine

**Context.** Four reported layouts put a fluid into a machine's output box or onto the wrong
port: the cryogenic plant fed petroleum gas from the north although its inputs are on the
south edge, the electromagnetic plant fed heavy oil into a north output box, and a
heavy-oil-cracking chemical plant swapped water and heavy oil because the lane sorted fluids
by name. The chemical plant also lost its long inserter because every port in the table was
kept free, including the corner the recipe did not use.

**Decision.** `blueprintMachineFluidPorts` is the prototype table (`fluid_boxes`
`production_type`) **after** the blueprint's rotation, not a row-side interpretation of the
edge. `blueprintMachineDirection` turns the machines whose prototypes do not fit the row:
the cryogenic plant, foundry and oil refinery by 180°, the electromagnetic plant by 90°
clockwise; the others stay at the prototype facing. A lane's side comes from the port the
recipe assigns to the fluid, in recipe order (never alphabetical), so north can feed and
south can feed. `inserterPortColumns` reserves only the ports the recipe's fluids use. A
fluid target whose output port is on a side edge is routed to a marker like an input, instead
of leaving a bare pipe stub.

**Consequences.** `MachineEntity` carries the blueprint `direction` (north stays the omitted
default); `RowFluidLane` owns the recipe-order port assignment, and `FluidLaneFeed` and
`MachineInserterPlacer` read the same assignment. Layouts with a cryogenic plant, foundry,
oil refinery or electromagnetic plant change geometry; the four reported URLs are pinned in
`BlueprintFluidPortLayoutTest`.

## 2026-10-23 — A machine port's side is a `Direction`, not its own enum

**Context.** `blueprintMachineEdge` originally returned a new `MachineEdge` enum
(`TOP`/`BOTTOM`/`WEST`/`EAST`) that was an exact 1:1 rename of four `Direction`
values, so callers carried a `TOP ↔ NORTH` translation and two names for
`EAST`/`WEST`.

**Decision.** `blueprintMachineEdge` returns `Direction` and
`blueprintMachineEdgeFlow` takes one; callers use `NORTH`/`SOUTH`. A separate
compass enum is only worth it when it narrows the domain (`LineSide` keeps a lane
to two of four sides and makes its `when` exhaustive) or carries semantics
`Direction` cannot express (`PortFlow`, `BeltEnd`).

**Consequences.** One compass vocabulary; `RowFluidLane` translates `LineSide` to
`Direction` where the row side becomes a machine side. No behavior change.

## 2026-10-22 — Domains in the package tree, one-way and compiler-enforced

**Context.** `common.business` held 62 of 98 `commonMain` files and `common.model`
was a catch-all (recipes, machines, surfaces, belt ends, URL codecs, blueprint
DTOs, JSON writer). Edges were invisible to import-based tooling.

**Decision.** The package tree is the domain model and the arrows point one way
(the full table is in `architecture.md`, "Package dependencies"):

- `model` is the grid vocabulary only; `prototype` the Factorio rulebook,
  `prototype/selection` the URL codecs; `production` reads the dump; `service` is
  the optimizer and never loads the dump; `plan`/`route`/`layout` are the shared
  vocabulary and grid kernel; the stage packages (`planning`, `routing`,
  `plumbing`, `special`, `compaction`) drive them; `beltend` and `band` sit
  between; `blueprint` assembles the artifact.
- A symbol no other package needs is `internal`; the compiler rejects a
  cross-package use. Every cross-package reference is an explicit import of the
  names used — no project wildcards — so an edge is visible in the file header.
- **Enforcement is the compiler plus review, not a test.** `PackageEdgesTest` and
  `PackageSizeTest` were removed: they were app tests holding architecture data and
  needed an edit on every package move. `ForbiddenImport` cannot replace them (one
  flat, global list; `DependencyRules` no longer exists). A future automated guard
  belongs at the Gradle/build level, reads compiled output, and must be proven by
  injecting the violation it should reject. `pitfalls.md` records the evaluated
  tools.

**Consequences.** `model` shrank from 15 files to 4; `layout` split into
`layout/entity` + `layout/validation`; `blueprint` into planning/output/json/
inspection plus the `blueprint/model` DTO leaf. The cycles
`blueprint ⇄ compaction`, `layout ⇄ special`, `routing ⇄ beltend` and
`production ⇄ production.stats` are gone, the last one by folding the single stats
file back into `production`; the package graph is one-way. `fromPlaced` replaced
`buildBlueprint`.

**Budgets.** The package budget is the **file count**: past 10 files is a signal to
split, 15 the ceiling; review keeps it, no task does. There is no line budget: file
length is capped per class by `LargeClass` (500). `model` was the only package
without file head-room for a while; `planning` and `production` sit at 9 now.

**How to move a file.** `git mv`, rewrite the `package` line, then run
`./gradlew compileKotlinJvm compileTestKotlinJvm` and add the import the
**compiler** asks for, one symbol at a time. Do not infer packages from a text
scan; `pitfalls.md` has the traps.

## 2026-10-20 — The layout is explicit stages, pinned by snapshots

**Context.** `BlueprintLayout.kt` had grown to 2,446 lines with one `RowLayout`
class holding ~90 methods and ~30 mutable fields; every layout change meant
editing that file.

**Decision.** Split by ownership of state, not by line count, into classes that
take the board (or plain data) and the shared state they own in their constructor:

- `LayoutBoard` owns the mutable grid, placements, reservations and primitives;
  `LayoutRouter` the weighted A*; `UndergroundStretcher` the run growth;
  `LayoutCompaction` the seam search; `RowLayout` keeps the ordered `build()`.
- The remaining stages are `PathPlacer`, `PowerPlacer`, `BeltEndConnector`,
  `RowPlanner`, `RowPlacer`, `ImportExtension`, `FluidFeeder`, `ImportTrimmer`,
  `CompactionPass`, `DeliveryRouter` (see `architecture.md` for the current
  owners).
- Every stage takes one `planning/LayoutStage.kt` context (`board`, `plan`,
  `logistics`, `planner`, `rowPlacer`, `router`). There is **no stage interface and
  no DI container**: the `build()` order is load-bearing. State several stages
  mutate lives in one small holder (`ImportExtension`), not in a lambda or a copied
  value.

**Consequences.** The file went from 1,925 to 196 lines. `BlueprintLayoutSnapshotTest`
hashes the canonical entity/wire list (FNV-1a) for the base, chest, fluid and band
plans; prove any further split neutral in a clean worktree at the pre-change
commit. There is **no Gradle module split**: a KMP multi-module split would
duplicate `expect`/`actual`, resource wiring and test targets for ~7k lines of
`commonMain`, so package and file boundaries are enough.

## 2026-10-20 — Detekt is the quality gate; no baselines

**Context.** A hand-rolled `checkFileSizes` task enforced a per-file line budget and
needed its own `buildSrc` function and tests; a standard Kotlin linter was wanted
instead.

**Decision.** Apply Detekt (`dev.detekt` 2.0.0-alpha.6) with
`buildUponDefaultConfig = true` and `config/detekt/detekt.yml` as the only
override, and delete `checkFileSizes`:

- `LargeClass.allowedLines = 500` is the project's per-class budget.
- `MagicNumber` is off: tile offsets, degrees, game data and test expectations read
  better in place; name only opaque algorithm limits.
- `ReturnCount.excludeGuardClauses`, `LoopWithTooManyJumpStatements.maxJumpCount = 3`
  and `DestructuringDeclarationWithTooManyEntries.maxDestructuringEntries = 4`
  match the readable forms. `TooManyFunctions` keeps Detekt's default of 11 per
  file/class.
- `check` runs the type-resolved `detektMainJvm`/`detektTestJvm` plus the light
  `detektJsMainSourceSet`/`detektJsTestSourceSet`; the plain `detekt` task over all
  of `src` is **not** in `check` (it would analyze the same files twice) but stays
  available for a one-off run.
- **No committed baselines.** Every finding fails `check`; fix it by extracting a
  named responsibility, and suppress only a proven false positive with a written
  reason.

**Consequences.** The typed KMP tasks report three `expect`/`actual` compiler
errors although the build passes (detekt issue #9602, see `pitfalls.md`). The
wildcard ban is not enforced by a Detekt boundary rule — `ForbiddenImport` stays
empty on purpose — it is house style so review can see each edge.

## 2026-10-19 — No per-task checkpoint layer

**Context.** Every task file in Git had exactly one commit, so the "live
checkpoint" was written once at the end anyway; the plugin also forced compaction
at fixed token counts regardless of the model's window.

**Decision.** Remove `knowledge/tasks/`, the `agents` CLI and its skill, the
`/task` command, task IDs, checkpoints and `.active` symlinks. Keep `knowledge/`
as the durable record and the compaction hook that only steers the summarizer.
`/handoff` is the explicit gesture: promote reusable findings and state the
verified state, decisions and next action. No handoff file, and the agent never
compacts on its own; OpenCode's `compaction.auto` decides when.

## 2026-10-18 — Belt-fed planned recipes are sized by the plan's rate, not by whole consumer machines

**Context.** A reported plan held an extra electronic-circuit and inserter factory
with unconnected output belts, and a five-machine iron-gear row where one more
assembler would have been enough. The belted split sized every planned recipe as
`ceil(builtConsumerMachines × inputPerMachine ÷ outputPerMachine)` — enough to run
each built consumer at full rate — while the plan drew far less. The plan has one
belt source per material, so a redundant planned recipe keeps its product belt and
no delivery starts on it.

**Decision.** Size a belt-fed planned recipe by the plan's own rate demand — the same
`consumption × scale` an import draws — and subtract what a band's *output* already
puts on a belt:

`machines = max(ceil(beltedRate ÷ outputPerMachine) − bandOutputMachines,
need − builtMachines)`, capped at `MAX_COPIES`, and built only when at least one
consumer is not direct-fed. A band's inserted (internal) link never counts as belt
output; a recipe whose consumers are all inserted gets no belt row at all.

**Consequences.** The reported plan shrank from 20 to 14 machines and 322 to 220
belts, with no belt chain that no inserter takes from. The layout still assumes one
belt source per material; a material that both a band's output and a belt row
produce would need two deliveries (a merge), which no fixture needs yet — the
invariant is asserted per plan
(`realBlueprintsPutEveryMaterialOnAtMostOneBelt`). Supersedes the belted
planned-recipe bullet of the direct-feed entry below.

## 2026-10-18 — Direct-feed bands group recipes by rate, not by machine count

**Context.** A recipe pair like `copper-cable` → `electronic-circuit` was always fed
over a belt, even when the machines stand in a ratio that lets an inserter move the
material straight from one machine into the next. The user asked for that direct
insertion whenever it is feasible, as one unit ("always on when feasible", never a
chooser, never an error).

**Decision.** `special/DirectFeed.kt` (pure) searches for links (a solid material
whose single in-plan producer can be inserted into a consumer) and groups them into
belt-free bands:

- Eligibility comes from **per-machine rates**, never machine counts:
  `outputPerMachine = speed × (1 + productivity) × items / time` (the bonus counts —
  it is what a directly-fed consumer really receives) and
  `inputPerMachine = speed × items / time`.
- A rate `r` is accepted when a whole-machine cycle `p : q` fits
  `r / 1.05 ≤ p / q ≤ r × 1.25` with `p + q ≤ 6`; ties go to the smallest sum, then
  the closest rate. Otherwise the link stays belted (user-verified: `0.8:1` and
  `1.05:1` → `1:1`, `1.1:1` and `1:20` rejected).
- The band is a **window of a periodic arrangement**: a cycle is found, repeated
  often enough to cover every member's whole machine need, then cut to the shortest
  length at which no consumer starves and no producer is left without a consumer
  (`A B C B A` for a 1:2:1 need). `GROUP_MAX_MACHINES = 18`,
  `MAX_BAND_RECIPES = 3` and `MAX_BAND_LINKS = 3` bound it; a group that cannot fit
  stays belted.
- Every machine is built **complete** (the band's share is rounded up); interface
  inserters are placed in the gap column on the producer's side, as many as the flow
  needs. A band's internal material gets **no lane, sink, belt or belted planned
  recipe**: only belted planned recipes are belt sources
  (`BlueprintPlan.producerOf`).
- `BlueprintPlan.rows` (`PlannedRow`/`PlannedLink`) is the single source of truth;
  a band is a row with links and is laid out by its own code path (`placeBand`), and
  a plan without bands lays out exactly as before. `PlannedRecipe.directInsert` is
  gone.
- A recipe that takes **or produces** a fluid is skipped for now: a band puts a
  fluid port between two machines where the lane/feed cannot reach it (see
  `pitfalls.md`).

**Consequences.** `Row` became a band of heterogeneous machines; belts, lanes and
sinks are unchanged for the belted part. (The original `rows`-by-allocation-index
keying is gone; see 2026-10-24.)
Deferred: line-breaking a band that is too wide (up to 18 machines in one row can
force belt crossings), and **mixed-footprint bands** — `arrange`/`placeBand` assume
one machine size per row, which keeps e.g. `iron-gear-wheel` (3×3) from inserting
straight into a `foundry` (5×5). The user's note: since the *smaller* machine feeds
the bigger one, the smaller one needs no output belt, and if it needs belt access it
can stand in the middle with long inserters reaching the lanes above and below.

## 2026-10-18 — Belt end is a `BeltEnd` selection, not a cheats flag

**Context.** The web UI had one "test chests" checkbox (`SelectionFlag.CHEATS`)
that only switched the infinity chest on. The request was to pick the solid belt's
end marker from icons and to reproduce pasted blueprints with real chests.

**Decision.** Replace the boolean with a `BeltEnd` enum whose values each carry a
URL `code` and a prototype `identifier`: `constant-combinator` (no code, the
default), `infinity-chest` (`X`, the old cheats mode), `buffer-chest` (`H`),
`storage-chest` (`G`), `wooden-chest` (`W`) and `steel-chest` (`D`). A chest marker
is a five-tile connector — `UG-in · inserter · chest · inserter · UG-out`, the belt
tunnelling under the chest; an input marker feeds the belt out of the chest, an
output marker fills it, and plain chests buffer both ways. `infinity-chest` keeps
the `X` code and the `--cheats` CLI alias, so existing links and scripts keep
working.

**Consequences.** `BeltEnd` is a field of `Selection` (`selection.beltEnd`), whose
`code` `selectionString` emits; `SelectionFlag` drops `CHEATS`. The blueprint model
grows `mirror`, `bar`, `requestFilter`, `circuitCondition` and
`circuitConditionEnabled` to reproduce pasted blueprints faithfully (`import_from`
is deliberately omitted — it is a space-platform field).
Connector inserters are powered, so the pole bound that normally keeps poles out of
the import extension is skipped for a chest marker (see `pitfalls.md`). Chests need
ground, so a space platform offers only the label and any chest code in a URL or
`--belt-end` clamps to it.

## 2026-10-18 — Durable agent knowledge lives in a visible `knowledge/` directory

**Context.** Long OpenCode sessions grew slow and expensive, and `/compact` could
drop verified findings. Everything important had to survive outside the active
conversation in a place an agent would reliably find.

**Decision.** Durable knowledge lives in a visible top-level `knowledge/`
directory: `index.md` (navigation), `architecture.md`, `conventions.md`,
`pitfalls.md` and `decisions.md`. `AGENTS.md` keeps only always-on rules and points
here. Per-change detail stays in the commit message; only reusable, stable and
verified findings are promoted.

**Consequences.** One root directory, no hidden prefix. Agents load `index.md` first
and then only the files their work needs, so the always-on context stays small.

## 2026-10-26 — One fluid source-block placement, with the role on `FluidFeed`

**Context.** `placeInfinityPipe` had three callers (`FluidLanePlacer`, `FluidFeeder`,
`FluidFeedLinker`) and `placeInfinityPipeSink` two, because three pipeline stages each
copied the cheat/marker branch. The trimmer had no role, so a sink lane whose marker
it relocated became a filtered supply pipe.

**Decision.** `LayoutBoard.placeFluidSource(beltEnd, end, fluid, sink)` is the only
caller of the two infinity-pipe primitives and the only place that branches on
`BeltEnd.INFINITY_CHEST`. It builds the block west from its eastmost tile `end`: one
tile for a cheat connector, three for a real marker. `FluidFeed` carries that eastmost
tile and the `sink` role, so the trimmer re-places a drain as a drain.
`removeFluidSource` is the symmetric removal.

**Consequences.** The three stages still place a source, but only through the shared
helper; a new stage cannot forget the cheat/marker split or the role. The lane
placer's old west-bound search was dead: a longer block further west could never fit
when the three-tile block at `end - 2` did not, so `sourceTiles` became a fixed-width
`sourceEnd` and `place` lost its `westOf` parameter.
