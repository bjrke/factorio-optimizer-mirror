# Decisions

Significant technical/process decisions and their rationale. Newest first.
Superseded decisions are removed once their replacement records the rationale; Git
history keeps the old text.

## 2026-10-21 — Clean the structural Detekt debt, phase B plan, layout and LP

**Context.** After the parser/resources slice, the last nine typed findings sat in
four files: `blueprintPlan` (cyclomatic), `layoutAscii` (cyclomatic and long),
`validateLayout`/`findUndergroundOutput` (cyclomatic, plus a loop with four jumps in
`validateLayout`) and `LinearProgram.minimize`/`simplex` (cyclomatic).

**Decision.** With the plan done, every baseline is empty and both
`detekt-baseline.xml` and `detekt-baseline-main.xml` are deleted; the rules and their
budgets stay untouched. The four files each split by responsibility:

- `BlueprintPlan.kt` keeps the plan data and `blueprintPlan` as the ordered steps
  (target check, target factory, recipe walk, direct feed, sources, label);
  `BlueprintSelection.kt` owns which recipes are built (the buildable/excluded rule,
  the single-producer walk that caps at `maxRecipes`, the import list) and
  `BlueprintMachine.kt` the machine geometry (footprints, fluid ports);
- `LayoutValidation.kt` runs the invariant groups in a fixed order — overlap,
  materials, undergrounds, inserters — and each group lives in
  `LayoutValidationTransport.kt`, `LayoutValidationUnderground.kt` or
  `LayoutValidationInserter.kt`; they append to one shared error list, so the message
  order is unchanged;
- `LayoutAscii.kt` only arranges board, legend and failures; the glyph/code tables are
  `LayoutAsciiBoard.kt` (`AsciiCodes`, shared so legend and grid use one numbering) and
  `LayoutAsciiLegend.kt`;
- `LinearProgram` kept its tableau and `pivot`; the entering column, leaving row, phase
  one (`makeFeasible`) and the artificial-variable cleanup (`dropArtificial`) became
  named steps.

**Consequences.** Both baselines go from 9 to 0 and are removed (like
`detekt-baseline-test.xml` in phase A), so `check` now fails on any new finding
without an allow-list in between: keep extraction the default answer to a finding. `spotlessApply check` passes (`jvmTest`,
`jsBrowserTest`, all Detekt tasks) with no test changed. Two traps were hit and are in
`pitfalls.md`: the glyph `when` needed one more split (15 branches) and phase one of
the simplex is numerically sensitive — an apparently equivalent `>= 0.0` feasibility
test instead of `< -EPS` made the optimizer report empty plans.

## 2026-10-21 — Clean the structural Detekt debt, phase B parser and resources

**Context.** After the serializer/CLI slice, 14 typed findings remained. Five were
the parser and resource pair: `parsePrototypes` (long, cyclomatic and a loop with
four jumps), its file-level `TooManyFunctions`, and `minedResourceRecipes`
(cyclomatic).

**Decision.** Split the parser along its data boundaries instead of suppressing:

- `JsonFields.kt` owns the nullable JSON accessors and the typed-object filter;
- `RecipePrototype.kt` owns one `data.raw.recipe` entry: result probability,
  ingredient/result maps, categories and surface conditions;
- `recipeParser.kt` keeps the dump orchestration, scrap reach and material list;
- `prototypeStats.kt` owns the machine and logistics stats;
- `resourceRecipes.kt` keeps extraction recipes, with the mined-resource category,
  results and fluid ingredient in their own helpers.

**Consequences.** Both baselines drop from 14 to 9 entries, the test baseline stays
empty, and `spotlessApply check` passes with the existing parser, resource and
full-dump suites. The remaining nine findings were the plan, ascii, LP and validation
group, cleared in the next slice.

## 2026-10-20 — Clean the structural Detekt debt, phase B serializer and CLI

**Context.** After the feed/optimizer slice, 19 typed findings remained. Five were
the serializer and CLI pair: `BlueprintEntity.toJson` (long and cyclomatic),
`parseArgs` (long and cyclomatic) and `settingsText` (long parameter list).

**Decision.** Split both by field and argument group instead of suppressing. The
blueprint envelope (`label`, `icons`, entity list, wires) stays in
`BlueprintJson.kt`; `BlueprintEntityJson.kt` owns one entity's position, settings,
neighbours and control behavior, with the optional fields in named writers. The
CLI parser moves to `CliArgsParser.kt`, where `valueOption`/`valueOptions` handle
singular and repeated `--name=` options and one resolver owns each decision
(space age, surface, normalize target, outputs, belt end). `settingsText` reads the
resolved belt, inserter, lanes and stacks from the active `LogisticsCalculator`
instead of taking seven parameters.

**Consequences.** The typed main baseline drops from 19 to 14 entries and the plain
baseline from 18 to 14; the test baseline stays empty. `spotlessApply check` passes,
and `BlueprintJsonTest` now covers every extracted entity section (position,
recipe, infinity chest/pipe, neighbours, marker and the existing filters/wires).
The remaining 14 findings are the plan, ascii, LP, validation, parser and
resource-recipe group.

## 2026-10-20 — Clean the structural Detekt debt, phase B feed and optimizer

**Context.** After the router/seam slice, 29 typed findings remained; ten were the
band/feed/optimizer cluster: `BandSearch.feedsOf`, `DirectFeedBuilder.build` and
`mapBelted`, `Optimizer.factoryProductions`/`solve`/`depth` and the `Optimizer` class
itself, plus `DeliveryPreparation.sinkOf`.

**Decision.** Give each mixed responsibility a named owner. `FeedWindow` performs
one band window's greedy supply flow instead of `BandSearch.feedsOf`; `DirectFeed`
keeps only link discovery, grouping and the belted allocation, with the link,
band, on-belt and belted-rate steps as methods. `MachineCatalog` owns factory
choice and machine rates, leaving `Optimizer` to the material balance; its
production labelling is split into consumption/producer/representative/depth
helpers, and `DepthIndex` memoizes the cycle-safe display depth. `sinkOf` is a
pure `when` with nullable lane sinks.

**Consequences.** The typed main baseline drops from 29 to 19 entries and the plain
baseline from 28 to 18; the test baseline stays empty. `spotlessApply check`
passes and all optimizer/direct-feed suites pass, so machine counts and band
shapes are unchanged. The remaining 19 findings are the plan, serializer, CLI,
ascii, LP, validation, parser and resource-recipe group.

## 2026-10-20 — Clean the structural Detekt debt, phase B router and seam

**Context.** Phase A left 40 typed findings. Eleven of them were the long/cyclic
`LayoutRouter.route`, its many-parameter `RouteProfile`, the seam dynamic program
(`findSeam`, `beltCuttable`, `pipeCuttable`) and `LayoutCompaction.applyCorridor`.

**Decision.** Make the search state explicit instead of keeping it in one method:
`RouteProfile` groups `RouteCosts` and `TunnelRules`, a `RouteRequest` carries the
start/goal/predicate/profile, and `RouteSearch` owns the A* frontier, relaxation,
node expansion and the in-region/corridor rules. `LayoutRouter` keeps only the
belt/pipe profiles and the public entry points. `SeamSearch` owns the seam DP and
its tie-breaks behind `findSeam`, and `CorridorShift` classifies each entity and
performs the shift. The belt/pipe cuttable checks are `all`/`none` expressions, so
they no longer need early returns.

**Consequences.** The typed main baseline drops from 40 to 29 entries and the plain
baseline from 37 to 28; the test baseline stays empty. `spotlessApply check` passes
and the four layout snapshot hashes are unchanged, so routing and compaction
geometry are neutral. The deferred work is the direct-feed/band/optimizer group and
the serializer/parser/CLI/ascii/LP/validation group.

## 2026-10-20 — Clean the structural Detekt debt, phase A

**Context.** The structural baselines held 67 typed findings. The layout stages and
model types mixed several responsibilities in one constructor or file, which is why
the findings clustered in `LayoutRouter`, `SeamGrid`, `RowPlacer`, `Optimizer`,
`recipeParser`, `LayoutBoard`, `DeliveryRouter` and the test helpers. The goal is a
reviewable baseline of zero, not a relaxed rule set.

**Decision.** Fix the low-risk half first, in behaviour-preserving slices, and keep
the remaining debt explicit:

- one `LayoutStage` context for `board`, `plan`, `logistics`, `planner`, `rowPlacer`
  and `router`, so stages stop repeating the same constructor;
- `LayoutBoard` keeps only grid/reservation/primitives; entity placement is
  `LayoutPlacement.kt`, the tunnel index is `Tunnels`, underground queries are
  `LayoutUnderground.kt`;
- `common.util.connectedComponents` replaces three hand-written breadth-first walks
  (pole wiring and the test fixtures);
- value objects for the crowds of parameters: `FactorySpec`, `Selection`, `RowBox` +
  `RowLanes`, and `RunGrowth` for an underground run;
- the row stages split by machine edge: `MachineInserterPlacer` and
  `BandInterfacePlacer`, while `RowPlacer` keeps the row order, lanes and product;
- delivery splits are their own `SplitDeliveryRouter` with `DeliveryNetworks` and
  `BeltRouteRules`, so `DeliveryRouter` only orders deliveries and places the
  target output;
- `LayoutEntity.withArea` moves the entity-kind switch into the entities, and
  `CoordinateGeometry.kt` holds the distance/compass helpers as extensions;
- `BlueprintLayoutAssertions` stays in `jvmTest`; the shared underground-run
  assertion lives in `commonTest` fixtures and adapts either the DTO or a layout
  dump.

**Consequences.** The typed main baseline drops from 59 to 40 entries, the plain
baseline from 56 to 37, and the typed test baseline is empty, so
`detekt-baseline-test.xml` is deleted. The remaining 40 findings are the deferred
router/seam, optimizer/band and parser/CLI work; the same clean-up path applies
(extract a named responsibility, then regenerate the baseline). `LongParameterList`
still exempts data classes by Detekt default, so each grouping object is a genuine
value (a machine spec, a URL choice, a row rectangle), not a bag of unrelated
arguments. See `conventions.md` and `pitfalls.md`.

## 2026-10-20 — Shrink the Detekt baselines to structural debt

**Context.** The three baselines were fully current — 192 plain, 131 typed-main and
59 typed-test entries — but most were style or false-positive noise (85 wildcard
imports, 31 return counts, 13 loop jumps, renameable files), not the structural
complexity the baselines were meant to track. Every entry needs a reviewer to
distinguish accepted debt from new noise.

**Decision.** Tune the rules that fight legitimate code and fix the rest:
`ReturnCount.excludeGuardClauses` (31 → 10), `LoopWithTooManyJumpStatements`
`maxJumpCount: 3` (13 → 4), `DestructuringDeclarationWithTooManyEntries`
`maxDestructuringEntries: 4` (all 3), and `WildcardImport.excludeImports` for the
project's own packages (`common.model`, `Direction`, `common.business`, the fixtures,
`js.component`, `de.bjrke.test`), `kotlin.test.*` and `kotlinx.serialization.json.*`.
The JSON DSL is a self-contained layer that reads as one vocabulary. The remaining
third-party wildcards (`react`, `web.cssom`, `tanstack.react.router`) were spelled
out.

Fix the mechanical findings: `.orEmpty()` instead of `?: emptySet()`/`?: ""`,
`Map.getValue` instead of `!!`, `RowLayout`'s initializer-only `val`s, the shadowed
lambda `it`, `AreaTile` → `areaTile`, five extracted `ComplexCondition`s,
`IconLayers.kt`/`Selection.kt`/`factorioUrl.kt` renamed after their type, `LayoutDump`
moved to its own file, and `@file:Suppress` for the React `XProps` names and JS
interop parameters (a file annotation, because the light-mode task reports those on
the file/interface).

**Consequences.** The baselines now hold only structural debt: 56 plain, 59 main
and 8 test entries — cyclomatic complexity, long methods, long parameter lists,
too-many-functions classes, ten return counts, four loop jumps and two nested blocks.
The remaining split candidates are the eight methods flagged for both `LongMethod`
and `CyclomaticComplexMethod`. Wildcard imports of the project's own packages and
the JSON DSL are the house style; other third-party wildcards are still findings.
See `conventions.md`.

## 2026-10-20 — Allow magic numbers, name only opaque tunables

**Context.** Detekt's introduction absorbed 65 `MagicNumber` findings into the
baselines. The optimizer is geometry-, vector- and math-heavy, so most literals are
tile offsets, degrees, prototype/game data or expected values in tests. Replacing
them with constants would force a lookup away from the assertion or coordinate that
needs the number, without making the domain clearer.

**Decision.** Disable the `MagicNumber` rule in `config/detekt/detekt.yml` and drop
its entries from all three baselines. Keep domain values in place: blueprint JSON
direction values (`0/4/8/12`), display rotations (`90/180/270/360`), fluid-port
coordinates, factory/surface data, recipe amounts, icon scales, CSS sizes and
protocol bit operations. Name only the opaque algorithm limits where the value alone
does not explain the intent: A* expansion cap, pole-connect rounds, source/marker
search bounds, fluid route-start count, pipe connector width and ASCII ruler step.
Share one `MACHINE_COUNT_EPSILON` between `BlueprintPlan` and `RowPlanner` instead
of repeating `1e-6`.

**Consequences.** New numeric literals no longer fail `check`; reviewers judge
whether a value needs a name from its context. Named constants are for limits and
thresholds, not for cardinals or test expectations. See `conventions.md`.

## 2026-10-20 — Detekt replaces the custom `checkFileSizes` task

**Context.** The 2026-10-19 line budget was a small hand-rolled Gradle task
(`checkFileSizes`) with three tiers and, later, no baseline. It measured only file
length, and needed its own `buildSrc` function and tests; a standard Kotlin linter
was wanted instead.

**Decision.** Apply Detekt (plugin id `dev.detekt`, version `2.0.0-alpha.6` — the
current 2.0 alpha, accepted as such) and delete `checkFileSizes`, its `buildSrc`
function and its 7 tests (`ci/file-size-baseline.txt` was already gone). Detekt runs
with `buildUponDefaultConfig = true`, and `config/detekt/detekt.yml` raises only
`LargeClass.allowedLines` to 500 to match the project's file budget. The plain
`detekt` task analyses all of `src` without type resolution; the KMP type-resolved
`detektMainJvm`/`detektTestJvm` tasks are wired into `check` by hand, because the
plugin registers but does not wire them. `check` is gated by three committed
baselines: `detekt-baseline.xml` (plain task) and
`detekt-baseline-main.xml`/`detekt-baseline-test.xml` (typed tasks).

**Consequences.** There is no per-file line rule: `LargeClass` counts a class, so the
500-line budget is now per class, not per file — keep a file small by responsibility.
The current light-mode findings (~325) plus the typed tasks' own are absorbed by the
baselines, so `check` fails only on a new finding; the reports remain the split
roadmap. Detekt's typed KMP tasks analyse `commonMain` and `jvmMain` as one fragment
and log three `expect`/`actual` compiler errors (detekt issue #9602), degrading type
resolution for `loadPrototypeDump` only. Removing the buildSrc test leaves buildSrc
without tests, so the obsolete CI `gradle -p buildSrc test` line was dropped. See
`conventions.md` and `pitfalls.md`.

## 2026-10-20 — Split the layout into explicit stages, verified by layout snapshots

**Context.** The 2026-10-19 decision extracted the kernel (`LayoutBoard`,
`LayoutRouter`, `UndergroundStretcher`, `LayoutCompaction`) but left
`BlueprintLayout.kt` at 1,925 lines with one `RowLayout` class owning the whole
pipeline and all plan-level state. Every stage change still meant reading the same
file.

**Decision.** Extract the remaining pipeline stages as classes in the same package
(`common.business`), one slice per commit:

- `PathPlacer` — `placePath`, the surface/tunnel roles, `directionBetween`;
- `PowerPlacer` — pole coverage, pole wires, the west bound;
- `BeltEndConnector` — connector width/offset, every belt-end marker, chest wires;
- `RowPlanner` — band-aware producer map, allocation depth, row units,
  `copiesOf`/`footprint`/`insertersFor`/`inserterPattern`;
- `RowPlacer` — `Row`/`PlacedMachine`, row/band placement, lanes, products,
  band interfaces, the allocation-to-row index and the lane direction;
- `ImportExtension` — the shared import bookkeeping (marker rows, feeds to trim,
  pinned tiles, pole/compaction west bounds);
- `FluidFeeder` — fluid edges, markers and pipe networks;
- `ImportTrimmer` — the common import column and the straight-prefix trim;
- `CompactionPass` — the compaction loop, the re-stretch and the pin/bound shift.

Each stage takes the board and the state it owns in its constructor and exposes
what later stages need; `RowLayout` keeps the ordered `build()` and the delivery
router (sinks, deliveries, splits, target output, networks, failures). No stage
interface and no DI container. Add `BlueprintLayoutSnapshotTest` first: it hashes
the canonical entity/wire output (FNV-1a) for base, chest, fluid and band plans,
and the same test run in a clean worktree at the pre-split commit proves each
slice is behavior-neutral.

**Consequences.** The finished split took `BlueprintLayout.kt` from 1,925 to 196
lines (1,925 → 1,508 → 1,370 → 1,011 → 629 → 196), extracting the belt delivery
router (sinks, deliveries, splits, import sources, `networks`, `failures`) into
`DeliveryRouter.kt` (480). Delivery went last because it owns the most shared state.
Package moves (e.g. `business/blueprint/layout`) are deferred until a further split,
to keep each commit mechanical.

## 2026-10-19 — Keep the layout modular with a per-file line budget

**Context.** `BlueprintLayout.kt` had grown to 2,446 lines with one `RowLayout`
class holding ~90 methods and ~30 mutable fields; `BlueprintLayoutTest` was 1,194
lines and `BlueprintIntegrationTest` 944. Every layout change forced an agent to
read and edit the same huge file, which is the dominant token cost.

**Decision.** Split the layout by ownership of state rather than by line count:

- `LayoutBoard` owns the tile grid, placed entities, route bounds, tunnel index and
  the tile/placement primitives;
- `LayoutRouter` owns `RouteProfile`/`RouteNode` and the weighted A*;
- `UndergroundStretcher` owns growing a run along its surface run;
- `RowLayout` keeps plan-level state (rows, sinks, deliveries, imports, networks)
  and the ordered `build()` pipeline.

The blueprint model is split the same way (`Material`, `Recipe`, `Category`,
`Surface`; the wire DTO stays in `Blueprint.kt` while Factorio JSON lives in
`BlueprintJson.kt`). Tests are grouped by scenario, and an over-long suite is
split the same way (`OptimizerTest` for synthetic machine/recipe cases,
`OptimizerSurfaceTest` for real per-surface resolution).

A `checkFileSizes` task (wired into `check`) enforced a per-file line budget with a
soft 300-line warning and a hard 500-line failure, and listed grandfathered hard
offenders in `ci/file-size-baseline.txt` as a strict ratchet. The 2026-10-20 entry
above later replaced the task with Detekt.

**Consequences.** The first slice reduced `BlueprintLayout.kt` to 1,925 lines,
recorded then as a baseline entry; all 64 layout tests still pass. The
remaining plan-level stages are extracted one at a time, each lowering the
baseline (actual names are in the 2026-10-20 entry below). No Gradle module
split: a KMP multi-module split would duplicate `expect/actual`
(`loadPrototypeDump`), resource wiring and test targets for ~7k lines of
`commonMain`, so package/file boundaries are enough at this size.

## 2026-10-19 — Drop the per-task checkpoint system

**Context.** The task workflow cost more than it returned. Every task file in Git
has exactly one commit, so the "live checkpoint" was never maintained during work
— it was written once at the end anyway, and the roundtrips spent editing it bought
nothing. The plugin injected a protocol reminder into every model call and forced
compaction at fixed 200k/250k token counts regardless of the model's window, which
compacted work in situations where the user did not expect it. User: it "creates
additional roundtrips to maintain the task document and ... compacts in situations
where I would expect them".

**Decision.** Remove the per-task layer entirely: `knowledge/tasks/`, the `agents`
CLI and its skill, the `/task` command, task IDs, checkpoints, `.active` symlinks,
commit↔task lookups and the context-budget/auto-compaction logic. Keep `knowledge/`
(`index`, `architecture`, `conventions`, `pitfalls`, `decisions`) as the durable,
reusable record and a `compaction` hook that only steers the summarizer.
`/handoff` is the explicit gesture: promote reusable findings into `knowledge/` and
state the verified state, decisions and next action for the summary. No handoff
file, and the agent never compacts on its own.

**Consequences.** Compaction timing is left to OpenCode's own `compaction.auto`.
There is no per-task history index; commit messages carry per-change detail and
`knowledge/*` carries the rationale. The always-on protocol shrinks to a short
knowledge section, and no tokens are injected per call.

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
belted-allocation bullet of the 2026-10-18 direct-feed entry below.

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
`bjrke/direct-insertion-groups` (commit `38f3cf9`).

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
(`!145`); the connector geometry and wire traps are in `pitfalls.md`.

## 2026-10-18 — Durable agent knowledge lives in a visible `knowledge/` directory

**Context.** Long OpenCode sessions grew slow and expensive, and `/compact` could
drop verified findings. Everything important had to survive outside the active
conversation in a place an agent would reliably find.

**Decision.** Durable knowledge lives in a visible top-level `knowledge/` directory:
`index.md` (navigation), `architecture.md`, `conventions.md`, `pitfalls.md` and
`decisions.md`. `AGENTS.md` keeps only always-on rules and points here. Per-change
detail stays in the commit message; only reusable, stable and verified findings are
promoted into `knowledge/`.

**Consequences.** One root directory, no hidden prefix. Agents load `index.md` first
and then only the files their work needs, so the always-on context stays small.
