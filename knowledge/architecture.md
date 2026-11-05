# Architecture

Verified facts about how this repository is put together. Keep this a map, not a
tutorial: it should say where things live and how data flows, with paths an agent
can open. `AGENTS.md` holds the always-on summary.

## Project

Factorio-Optimizer is a Kotlin Multiplatform app (Gradle, Kotlin) that computes
layout-optimizing Factorio blueprints. It compiles to a React web app (JS) and a
CLI (JVM) from one shared core.

## Source layout

- `src/commonMain` — shared logic, one package per domain. The dependency direction
  is one-way, from the vocabulary up to the artifact. The exact edges each package
  may use are listed under "Package dependencies" (see `decisions.md`, 2026-10-22):
  - `core/model` — the grid vocabulary and nothing else: `Tile.kt` (`Coordinate`,
    `Area`, `Dimension`, `Direction`, `Io`), `TileGeometry.kt` (distance and
    compass helpers), `Material.kt`, `IconLayer.kt`. A leaf: it imports no other
    project package.
  - `core/util` — `MinHeap`, `connectedComponents`, and `MathUtils` (`countVectors`,
    `permute`, `greatestCommonDivisor`).
  - `game/prototype` — the Factorio rulebook: `Recipe.kt` (`ParsedRecipe`,
    `MachineStats`, `Recipe`, `Subgroup`), `Factory.kt`, `Category.kt`,
    `Logistics.kt` (belt/inserter/pump stats and `LogisticsConfig`),
    `Surface.kt` (`Surface`, `SurfaceProperty/Condition`, the surface rules as
    `Surface.accepts*` extensions).
  - `game/selection` — the URL/CLI codecs: `Selection.kt`
    (`Selection`, `SelectionFlag`, `selectionString`, `spaceAgeIn`,
    `fullBeltIn`), `BeltEndSelection.kt` (`beltEndFromSelection/-Identifier`),
    `SurfaceSelection.kt` (`surfaceFromUrl/-Planet`, `forSurface`). Only the
    entry points (UI, CLI) and the tests use it; the layout never sees a URL.
  - `game/production` — reads the prototype dump: `recipeParser.kt` orchestrates
    it, `RecipeLoader` (the cached per-mods parse), `prototypeDump.kt` (the
    `loadPrototypeDump` `expect` declaration), `RecipePrototype.kt`,
    `JsonFields.kt`, `resourceRecipes.kt`, `customRecipes.kt`
    (`ALL_SCIENCE_PACKS`), `spoilageRecipes.kt`, `utils.kt` (normalization) and
    `prototypeStats.kt` (`machineStats`/`logisticsStats`).
  - `optimize` — the optimizer: `Optimizer.kt` and `MachineCatalog.kt`
    (machine choice and rates) and `LinearProgram.kt`. It takes a `ParsedRecipe`
    in and returns an `OptimizationResult` (from `model`); it never loads the
    dump or builds a blueprint. Its output models now live in `model`, so the
    blueprint planner can consume an optimizer result without depending on the
    optimizer itself.
  - `model` — the shared result models: `OptimizationResult`, `FactoryProduction`
    (per-machine production) and `factoryProductionComparator`. They sit above
    the rulebook (`game/prototype`) and are consumed by both the optimizer and
    the blueprint planner, so `optimize` and the `planning` packages need not
    couple to each other — both reach the *models* instead. A leaf of its own:
    it imports only `core/model` and `game/prototype`.
  - `blueprint` — everything that picks, places and prints a blueprint, split
    into the `layout`, `planning`, `placing` and `printing` stages below. The
    optimizer (`optimize`) stays out of it.
    - `blueprint/layout` — the grid kernel: `LayoutBoard` (tiles, reservations,
      primitives), `LayoutPlacement` (single-tile entity-kind helpers),
      `LayoutTunnels` (the surface/end/tunnel placement and the tunnel-link
      plan), `LayoutUnderground`, `Tunnels`, `LayoutIndex` (the read-only tile
      index), `BeltEnd` (how a belt is terminated). `entity` holds the entity
      hierarchy (`LayoutEntity`, `LayoutTransportEntities`,
      `LayoutEntityStations`, `LayoutSettings`); `validation` holds the invariant
      checks (`LayoutValidation*.kt`). This tree must never mention the
      optimizer.
    - `blueprint/planning` — planning the blueprint. It holds the plan reduction
      (`blueprintPlan`, `includedRecipes`, `plannedSources`,
      `BlueprintSelection`) at the stage root and clusters the planner by
      concern: `plan` — the shared vocabulary (`BlueprintPlan` and its result,
      the `Planned*` types, the machine footprint and fluid ports, the budgets
      (`MAX_COPIES`, `MAX_BLUEPRINT_RECIPES`), `ImportExtension` (the import
      state the marker, trim, compaction and power stages share) and
      `LogisticsCalculator` (the layout's belt/inserter rates)); `row` — the row
      planner (`RowPlanner`, `RowPlacer`, `Row` and the inserter/interface
      placers) with the shared `LayoutStage`; `band` — the direct-feed band
      (`DirectFeed`, `DirectFeedModel`, `BandSearch`).
    - `blueprint/placing` — placing the entities and their connections:`route`
      (the weighted A* shared by belts and pipes — `LayoutRouter`,
      `RouteSearch`, `RouteProfile`), `routing` (implicit delivery — `Delivery*`,
      `Import*`, `FluidFeedLinker`, `SplitDeliveryRouter` and `BeltRouteRules`),
      `plumbing` (the fluid feed — `FluidFeeder`, `FluidLaneFeed`), `beltend`
      (`BeltEndConnector.kt` — the solid belt's end marker: label, infinity
      chest, chest), `special` (the path, power and material-search passes) and
      `compaction` (`LayoutCompaction`, `CompactionPass`, `SeamGrid`,
      `UndergroundStretcher`).
    - `blueprint/printing` — emitting the artifact: `model` (the wire DTO:
      `Blueprint`, `BlueprintEntity`, `BlueprintWire` — imports only
      `core/model` and `blueprint/layout/entity`, so the encoders and the
      assembly can share it without a cycle), `json` (`Blueprint.toJson`,
      `BlueprintEntityJson.kt` — the wire JSON, a leaf nothing else may import),
      `output` (`BlueprintString.kt`, `FactorioUrl.kt`, `LayoutSerialization.kt`,
      `ChestWires.kt`) and `inspection` (the ASCII layout and dump —
      `LayoutAscii*.kt`, `LayoutDump.kt`). The ordered `build()` pipeline lives in
      `RowLayout.kt` at the `blueprint` root, not in this stage (see
      "Blueprint layout").
  - plus the dumped `prototypes/` resources.
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, the TanStack router
  `tanstack.react.router`, `emotion-react`); entry `js/index.kt`;
  `common/game/production/loadRecipe.kt` pulls the dumps via `@JsModule` and the
  webpack config in `webpack.config.d/`.
- `src/jvmMain` — CLI entry `jvm/CliOptimizer.kt`, argument parsing
  `jvm/CliArgsParser.kt`; `jvm/common/game/production/loadRecipe.kt`
  loads recipes from a classpath resource.
- `src/commonTest` — shared kotlin-test suites, run by both `jvmTest` and
  `jsBrowserTest`; must stay platform-neutral. Every suite sits in the package it
  tests (`commonTest/…/common/blueprint/printing/…`, `.../game/production/…`), and shared
  helpers live in a `fixtures` package (`common/fixtures`: `testLogistics`,
  `calculator`, the material and named-recipe fixtures (`RecipeFixtures.kt`), the
  optimizer fixtures and the test-only `BlueprintPlan.recipes` view;
  `blueprint/printing/fixtures`: plans, layout assertions and the minimal fluid-row
  helpers).
- `src/jvmTest` / `src/jsTest` — platform-specific tests; JVM suites that need
  the build output or the source tree (`IconCoverageTest`) and the CLI
  integration tests live there.

`commonMain` compiles to both targets, so platform differences belong in the
target-specific `loadRecipe.kt`/`index.kt` files (the `loadRecipe.kt` ones are
`expect`/`actual` pairs with the common declaration, both in
`common.production`).

Package sizes (files, verified 2026-11-04). **Files** are the budget: past 10 is a
signal to split, 15 the ceiling. Nothing automates it — the file-count test was
removed, so review keeps it (see `decisions.md`, 2026-10-22). There is no line
budget; `LargeClass` caps a class at 500 lines. Recheck a count with
`find src/commonMain/…/<package> -maxdepth 1 -name '*.kt' | wc -l`.

| package | files |
| --- | --- |
| core.model | 4 |
| core.util | 3 |
| game.prototype | 5 |
| game.production | 10 |
| game.selection | 3 |
| model | 1 |
| optimize | 3 |
| blueprint | 1 |
| blueprint.layout | 9 |
| blueprint.layout.entity | 4 |
| blueprint.layout.validation | 4 |
| blueprint.planning | 2 |
| blueprint.planning.plan | 4 |
| blueprint.planning.row | 9 |
| blueprint.planning.band | 3 |
| blueprint.placing.route | 3 |
| blueprint.placing.routing | 7 |
| blueprint.placing.plumbing | 2 |
| blueprint.placing.special | 4 |
| blueprint.placing.compaction | 4 |
| blueprint.printing.model | 1 |
| blueprint.printing.json | 2 |
| blueprint.printing.inspection | 3 |
| blueprint.printing.output | 4 |

`game.production` is at 10 files — the warn line itself, so split it before adding a
file; `blueprint.layout` and `blueprint.planning.row` (9) are next.

## Package dependencies

Each package imports the others by name (no project wildcards; the
`WildcardImport` rule in `config/detekt/detekt.yml` allows only `de.bjrke.test.*`
from this repository), and a symbol no other package needs is `internal`: the
compiler then rejects a cross-package use. That is the whole enforcement: **no test
checks these edges** — a hand-written guard test does not belong in the app suites
(see `decisions.md`, 2026-10-22). The table below is the intent; keep a change to it
deliberate and reviewable, never widen a row to make an existing edge legal.

The arrows point one way from the vocabulary up to the artifact (leaf
dependencies at the bottom, the assembly root at the top):

```
core.model < game.prototype < game.production
core.model, core.util, game.prototype < model < optimize < blueprint
blueprint.layout < blueprint.placing.route, blueprint.planning.plan
blueprint.layout, blueprint.planning.plan < blueprint.planning.row,
  blueprint.placing.compaction
blueprint.planning.row < blueprint.placing.plumbing, blueprint.placing.routing
blueprint.layout.entity < blueprint.printing.model <
  blueprint.printing.{json, output, inspection}
(pulling all of the above together) < blueprint
```

`blueprint.printing.model` is the DTO leaf the encoders share; `blueprint` is the
assembly root and imports the placing stages, `blueprint.planning.plan` and
`blueprint.planning.row`, and `blueprint.printing.{model,output,inspection}`. Only
the entry points (UI, CLI) and the tests import `blueprint` itself, for `RowLayout`
(which lives at the `blueprint` root). The optimizer's *results* are reached
through the shared `model` package — planning and `optimize` never import each
other.

| package | may use | may not reach |
| --- | --- | --- |
| `core.model` | nothing | every project package |
| `core.util` | nothing | every project package |
| `game.prototype` | `core.model` | `game.production`, `model`, `optimize`, `blueprint` |
| `game.production` | `core.model`, `game.prototype` | `model`, `optimize`, `blueprint` |
| `game.selection` | `blueprint.layout`, `game.prototype` | `optimize`, `model`, the dump loader |
| `model` | `core.model`, `game.prototype` | everything above (the shared result models) |
| `optimize` | `core.model`, `game.prototype`, `model` | `game.production` (the dump loader), `game.selection`, `blueprint` |
| `blueprint` | `blueprint.layout(+entity,validation)`, the placing stages, `blueprint.planning.{plan,row}`, `blueprint.printing.{model,output,inspection}`, `core.model`, `game.prototype` | `blueprint.json`, `optimize` (the optimizer stays out) |
| `blueprint.layout` | `blueprint.layout.entity`, `core.model`, `game.prototype` | `optimize`, `blueprint`, the stages above |
| `blueprint.layout.entity` | `core.model`, `game.prototype` | everything else |
| `blueprint.layout.validation` | `blueprint.layout`, `blueprint.layout.entity`, `core.model` | `blueprint` and above |
| `blueprint.planning` | `blueprint.planning.{band,plan}`, `core.model`, `game.production`, `game.prototype`, `model` | `layout` (it builds a plan, not a grid), the placing/printing stages |
| `blueprint.planning.plan` | `core.model`, `game.prototype`, `model` | `layout`, `blueprint` |
| `blueprint.planning.row` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.placing.route`, `blueprint.planning`, `blueprint.planning.plan`, `core.model`, `game.prototype`, `model` | `routing`, `blueprint` |
| `blueprint.planning.band` | `blueprint.planning.plan`, `core.model`, `core.util`, `game.prototype`, `model` | the layout |
| `blueprint.placing.route` | `blueprint.layout`, `core.model`, `core.util` | `routing`, `blueprint` |
| `blueprint.placing.routing` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.layout.validation`, `blueprint.placing.route`, `blueprint.placing.special`, `blueprint.planning.{plan,row}`, `core.model` | `compaction`, `blueprint` |
| `blueprint.placing.plumbing` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.placing.route`, `blueprint.placing.special`, `blueprint.planning.{plan,row}`, `core.model` | `routing`, `blueprint` |
| `blueprint.placing.special` | `blueprint.layout`, `blueprint.layout.entity`, `core.model`, `core.util` | `routing`, `blueprint` |
| `blueprint.placing.compaction` | `blueprint.layout`, `blueprint.layout.entity`, `blueprint.layout.validation`, `blueprint.planning.plan`, `core.model` | `routing`, `blueprint` |
| `blueprint.printing.model` | `blueprint.layout.entity`, `core.model` | everything above (the DTO leaf) |
| `blueprint.printing.json` | `blueprint.layout.entity`, `blueprint.printing.model`, `core.model` | — and **nothing may import it**: it is a leaf writer |
| `blueprint.printing.inspection` | `blueprint.layout.entity`, `core.model` | `blueprint` and above (it only reads a finished layout) |
| `blueprint.printing.output` | `blueprint.layout.entity`, `blueprint.printing.model`, `core.model`, `game.production` | `blueprint`, `blueprint.json` |

A nested package is its own unit: it may use its parent, and the parent may not use
the child unless its row says so (`layout` cannot see `layout.entity` merely
because the names are related — `layout.entity` is listed explicitly). A package
that both is a directory and a symbol prefix (`prototype` and
`prototype.selection`) is resolved from its full dotted name, so an edge to
`…prototype.selection.X` belongs to `prototype.selection`, not to `prototype`.

When the compiler rejects a symbol, the edge is missing from the table: adding a
package or a dependency means adding a row deliberately. `pitfalls.md` has the
recipe for moving a file (the compiler's error list is the only reliable source of
edges).

## Blueprint layout

`blueprint/planning/blueprintPlan` (`BlueprintPlan.kt`) builds the plan from the
optimizer's `OptimizationResult`; `blueprint/RowLayout.kt` turns a
`BlueprintPlan` plus a `LogisticsCalculator` into a `Blueprint`
(`blueprint/printing/model/Blueprint.kt`). `RowLayout(plan, logistics, beltEnd).blueprint()`
runs the ordered passes — recipe rows, target output, import preparation,
fluid lanes and source markers, solid import deliveries, the routed fluid
fallback, produced solid deliveries, the drain/feed lane link, the fluid target
output, trim import prefixes, stretch underground runs, compaction, poles (which
place their wires) — and returns a `Blueprint` whose `entities` are numbered by
list position and whose `wires` are the chest-connector wires from
`blueprint/printing/output/ChestWires.kt`. The order is load-bearing: fluid lanes go
before solid belts, solid imports go before produced deliveries, the lane link
after the other routes so it can go around them, the import trim before the
underground stretch, compaction before the poles. `blueprint/layout/validation` also
requires every routed underground belt end to connect through a same-material
belt or the previous run (an output end may feed the next input directly);
compaction rejects a corridor that would leave an entrance or exit orphaned
(connector ends are recognized by their 2/4-tile partner pattern).
`RowLayout.layoutDump()` is the one read-back of a finished layout
(`LayoutDump`: placed `LayoutEntity`s plus failure strings); the CLI's `--layout`
renders it and the `jvmTest` suites assert on it through the `Layout` fixture in
`jvmTest/…/common/blueprint/printing/fixtures/BlueprintIntegrationFixtures.kt`, so `RowLayout`
is `internal` and keeps its accessors private.

The layout is split into a small mutable kernel plus stages, and every stage takes
one `blueprint/planning/row/LayoutStage.kt` context (`board`, `plan`, `logistics`, `planner`,
`rowPlacer`, `router`) instead of repeating those collaborators:

- `blueprint/layout/LayoutBoard.kt` — the tile grid, the placed entities, route bounds and
  the reserve/place/remove primitives. Single-tile entity placement lives in
  `LayoutPlacement.kt`; `LayoutTunnels.kt` owns the surface tile, the underground
  end and the tunnel, plus `tunnelPlan(from, to)` — the one calculation of a link's
  tiles (surface tiles and run ends, at the material's reach) — which `placeTunnelPlan`
  places and callers reuse for their fit checks, so a link is never computed twice.
  Runs meet end to end: an output end may feed the next run's input directly (pipes
  and belts alike), and the validator accepts that.
  The tunnel index is in `Tunnels.kt` and the underground queries in
  `LayoutUnderground.kt`, all as board extensions. `LayoutPipes.kt` holds the
  one pipe-tile rule, `canPlacePipes` (the tile is free and no placed pipe beside
  it carries another fluid — touching pipes merge in Factorio), which the lane
  placer and the routed fluid feed share; `touchesForeign` is its neighbor test and
  `Plumbing.joinsAt` reads a pipe-to-ground end's single mouth
  (`PipeToGroundEntity.mouth`), so a pipe beside a foreign end's closed side does
  not count as a merge. A tile merely *reserved* for another fluid stays the
  caller's check (`FluidFeeder.pipeAllowed` via `edgeFluidAt`).
- `blueprint/placing/route/LayoutRouter.kt` — the belt/pipe profiles and the routing entry points;
  `blueprint/placing/route/RouteProfile.kt` groups the costs (`RouteCosts`) and tunnel rules (`TunnelRules`) with
  the `RouteRequest`, and `blueprint/placing/route/RouteSearch.kt` owns the weighted A* frontier and node
  expansion.
- `blueprint/placing/special/PathPlacer.kt` — `placePath` and the tile roles for plain and
  underground runs; shared by belt and pipe routes. It is the shared
  helper `routing` reaches (the `special` edge in the package dependency table).
  `directionBetween` is the compass helper on the model (`core/model/Tile.kt`).
- `blueprint/placing/compaction/UndergroundStretcher.kt` — grows underground runs along their own
  straight surface run; its `RunGrowth` owns one run's budget, swallow rule and
  end sharing.
- `blueprint/planning/row/RowPlanner.kt` — the plan-level row order: the depth of every row
  (a DFS over `BlueprintPlan.rows` with a recipe path as the cycle guard), the
  `RowUnit(row, depth)` order and the pure sizing helpers (`inserterPattern`).
- `blueprint/planning/row/RowPlacer.kt` — places the machine rows (`Row` = `PlannedRow` +
  `Area` + `RowLanes` + machines, declared in `Row.kt`) and the bands, and owns
  the ordered `placedRows` list and the lane direction. `RowLanes` holds the fixed
  belt locations as `RowBelt` values (y, material, direction, io and side),
  including the product. A `PlacedMachine` carries its own `production`;
  `producerRow(material)` scans `placedRows` for the row whose `plannedRow` equals
  `BlueprintPlan.producerOf(material)`.
  `MachineInserterPlacer` places the machine-edge inserters and
  `BandInterfacePlacer` the gap inserters.
- `blueprint/planning/row/FluidLane.kt` — the board-free lane shape: a plain junction pipe above
  every port, a horizontal pipe-to-ground run across every gap (a pair of adjacent
  ends in the two-tile gap between touching machines) and one vertical shaft per
  port, whose ends sit one row inside the lane and on the port tile.
  `blueprint/planning/row/RowFluidLane.kt` assigns the recipe's fluids to the machine ports in
  `prototypeFluidPorts` order — inputs before outputs, each in recipe order — and
  decides which side that puts a lane on, the lane row (two rows beyond the
  outermost belt) and its depth: a side places one lane per fluid, each two rows
  further out than the last.
  `blueprint/planning/row/FluidLanePlacer.kt` puts it on the board: the inline `oCo` marker, the
  horizontal runs, the shaft ends and the tunnel index, skipping a shaft that finds
  no room so only that port falls back to the route. It plans each run once
  (`tunnelPlan`) and checks exactly the tiles that plan claims, so a run longer
  than the pipe reach (a band whose fluid port is far from its lane head) is
  checked and placed from the same list.
- `blueprint/planning/plan/ImportExtension.kt` — the shared import bookkeeping: the marker rows
  already claimed, the import feeds to trim, the pinned marker tiles and the
  pole/compaction west bounds.
- `blueprint/placing/plumbing/FluidFeeder.kt` — supplies every fluid. `placeImports()` lets
  `blueprint/placing/plumbing/FluidLaneFeed.kt` place a lane for every fluid of every row that gets
  one (north and south side), records the port tiles those lanes reach and leaves
  them alone, then reserves the remaining machine edges (`fluidEdge`) and places
  every routed source marker, keeping the prepared pipe groups. A lane carries one
  `(fluid, flow)` pair, so a fluid that is both an input and an output gets a feed
  lane and a drain lane; `connectLanes()` routes the drain back into the feed after
  the other routes. A routed marker
  sits next to its first sink and searches its row **outward from the port's edge**
  (`portEdge`:
  north for a north port, south for a south port, south for a side port), so a
  second fluid on the top edge is marked above the row instead of below it.
  `routeFluids()` grows those networks afterwards (`pipeAllowed`, `pipeRoute`),
  after the solid imports. The lane is primary; the A* path is only the fallback
  for a side port, a machine without a port table or a shaft that found no room.
  A fluid target whose output port is on a side edge is routed to a marker by the
  same path (`connectTargetOutput`), not left as a stub. The lane/marker pass runs
  after `RowPlacer.placeAll()`, so the belts and inserters a shaft buries itself
  under already stand (see `decisions.md`, 2026-10-26).
- `blueprint/placing/routing/ImportTrimmer.kt` — moves every import marker to one common column and
  removes the straight belt prefix in front of it (`lastStraightEast`). A **fluid**
  lane marker moves into that same column; `blueprint/placing/routing/FluidFeedLinker.kt` joins its
  lane head with pipe-to-ground runs along the lane row (`FluidFeed` in
  `blueprint/planning/plan/ImportExtension.kt`). Without solid imports the column is the westmost
  inline fluid marker, so the stacked lane markers still share one column. The
  marker, link and head are pinned so compaction shifts them as one group. The
  link is not pre-checked for room: the lane row was laid before any route ran, so
  a link that would overlap a placed tile or touch a foreign fluid is placed,
  rejected by `validateLayout` and rolled back, keeping the inline marker.
- `blueprint/placing/compaction/CompactionPass.kt` — the seam-compaction loop; it re-stretches the
  underground runs after each corridor and shifts the import pins and bounds. A
  candidate that fails only after the stretch is ruled out by its seam path and the
  loop tries the next corridor instead of aborting. The solid and fluid import
  markers, their links and the lane head are pinned
  (`ImportExtension.pinnedImportTiles`, passed to `nextCompaction` as `pinned`),
  so compaction moves them as one column; `fluidPorts` stays read-only (the
  stretcher uses it to avoid swallowing a port).
- `blueprint/placing/compaction/LayoutCompaction.kt` — seam compaction over a `List<LayoutEntity>`;
  `SeamGrid.kt` owns the removable-run grid and its `SeamSearch` dynamic program;
  `CorridorShift` (in `LayoutCompaction.kt`) cuts one corridor and moves the far
  side.
- `blueprint/placing/special/PowerPlacer.kt` — pole coverage and pole-wire neighbours over the
  final geometry.
- `blueprint/placing/special/BeltEndConnector.kt` — the label, infinity-chest and chest belt ends,
  their connector width/offset, and the chest circuit wires.
- `blueprint/placing/routing/DeliveryRouter.kt` — routes the solid imports first and the produced
  deliveries afterwards, and places the target's output connector;
  `DeliveryPreparation` reserves the sinks and sources (imports first),
  `SplitDeliveryRouter` inserts the branch splitters, `DeliveryNetworks` remembers
  the produced trunks and `BeltRouteRules` is the belt-tile predicate shared by
  both routes. `blueprint/placing/routing/FluidFeedLinker.kt` links a trimmed fluid marker to its
  lane head.
- `RowLayout` keeps the ordered `build()` pipeline (idempotent, so `blueprint()`
  and `layoutDump()` can share one instance); the `RowPlacer`,
  `ImportExtension`, `FluidFeeder`, `ImportTrimmer` and `CompactionPass` stages own
  the rest.

`common/core/util/Graph.kt` holds `connectedComponents`, used by pole wiring and the
layout test fixtures instead of a per-suite breadth-first walk.
`common/core/util/MathUtils.kt` holds the generic `countVectors`, `permute` and
`greatestCommonDivisor` the band search uses.

`blueprint/planning/band/DirectFeed.kt` (pure, commonMain) decides which recipes a plan builds as
a **direct-feed band**: a solid material whose producer and consumer stand in a
small whole-machine cycle (`cycleRatio`, per-machine rates with productivity) is
inserted machine to machine, the recipes become one interleaved row, and
`BlueprintPlan.rows` (`PlannedRow`/`PlannedLink`) is the single source of truth
for it. A band is a `PlannedRow` whose `links` are non-empty and whose `machines`
hold one `FactoryProduction` per machine; a plain row repeats its one production
once per machine and has no links. The search is a graph walk from the target:
`DirectFeedModel.FeedLink` carries its producing `FactoryProduction`, its parent
link and the inserted material, and lazily expands its ingredients, skipping
fluids and any material already on the path so a recipe cycle is entered once.
`DirectFeedContext` owns the target, the included productions, the scale, the
logistics and the per-material producer index; a material is direct-fed only
when the plan has exactly one producer for it. `blueprint/planning/band/BandSearch` is a set of
extensions on that context and finds the shortest window (`FeedWindow` checks
that every consumer is covered and every producer feeds someone). `RowPlacer`
places every row through one `placeRow`: a plain row repeats its production with
touching machines, while a row with links leaves a gap column after each machine
that has a `PlannedLink` and touches everywhere else. Only belted productions
feed belts and sinks, so a band's internal material never gets a belt; the row
still gets **fluid lanes** for its machines' fluid ports, including a machine
that takes or gives a fluid while its solid link comes from a neighbor; only the
link material must be solid (see `decisions.md`, 2026-11-02). See `decisions.md`
(2026-10-18) for the band rules and the open line-breaking follow-ups,
(2026-10-24) for why the row is the occurrence, and (2026-11-04) for the
recursive link graph and its recipe-path cycle guard.

A solid belt's end is a `BeltEnd` (`blueprint/layout/BeltEnd.kt`): the constant-combinator
label (default), the infinity chest, or a chest the inserters feed
(`buffer`/`storage`/`wooden`/`steel`). `placeSolidConnector` places it;
`BeltEndConnector.width`/`left` keep the routing corridors aligned; a fluid belt
always keeps the combinator/pipe label. `blueprint/printing/json/BlueprintJson.kt` writes the
blueprint envelope (`label`, `icons`, `entities`, top-level `wires`);
`blueprint/printing/json/BlueprintEntityJson.kt` writes each entity's position and optional
`request_filters`, `bar`, `mirror`, `control_behavior` (circuit condition),
`infinity_settings` and `neighbours` fields. An infinity pipe writes
`infinity_settings` only as a source; a sink is unfiltered and writes none, so
the game deletes whatever reaches it. See `decisions.md` (2026-10-18) for the
geometry and `pitfalls.md` for the wire and pole traps.

The layout tests are grouped by scenario (`BlueprintLayoutTest`,
`BlueprintImportTest`, `BlueprintFluidLayoutTest`, `BlueprintBandTest`,
`BlueprintBandFluidTest`, `BlueprintBeltEndTest`); `BlueprintLayoutSnapshotTest`
hashes the canonical entity/wire list of representative plans, so any geometry or
entity-order change is deliberate. The JVM integration suite is split by plan, layout, power, fluid,
belt end and serialization. `./gradlew check` runs Detekt (see `conventions.md`):
the type-resolved `detektMainJvm`/`detektTestJvm` plus the light
`detektJsMainSourceSet`/`detektJsTestSourceSet`; there are no committed
baselines, so every finding fails.


## Generated data — do not hand-edit

- `src/commonMain/resources/prototypes/{base,space-age}.json` are `data.raw`
  dumps from `factorio --dump-data` for the base game and base + Space Age DLC.
  Regenerate with `./gradlew dumpPrototypes` (needs a Factorio Steam install).
  Because the game resolves the whole data stage, the dump already contains every
  `data-updates.lua`/`data-final-fixes.lua` change; the parser reads only the
  prototype types it needs. `RecipeLoader` (`game/production/`) caches parsed results; the UI and
  CLI use the base dump, or the Space Age dump when Space Age is active.
- `src/jsMain/resources/icons/*.png` are 64x64 icons cropped from the 120x64
  source sheets of a Linux Factorio Steam install. `./gradlew updateImages` copies
  and optimizes only icons whose top-left 64x64 region changed by more than
  `imageChangedThreshold` (needs `imagemagick optipng pngquant`). Do not add icons
  by hand.

## Prerequisites

- JDK 25 is required. `gradle.properties` sets
  `org.gradle.java.installations.auto-download=false`, so a local JDK 25 must
  exist or the build fails with a cryptic error (only the installed version
  string). CI image: `$CI_REGISTRY_IMAGE:latest`
  (`registry.gitlab.com/bjrke/factorio-optimizer:latest`), built from
  `ci/Dockerfile` on `gradle:9.8.1-jdk25` plus Google Chrome for `jsBrowserTest`.

## Related tooling

- `buildSrc/` — Gradle build logic (`dumpPrototypes`, `sanitizePrototypes`,
  `updateImages` are the `factorio` group tasks).
- `.opencode/skills/` — the Factorio blueprint and optimizer skills.
