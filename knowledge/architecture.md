# Architecture

Verified facts about how this repository is put together. Keep this a map, not a
tutorial: it should say where things live and how data flows, with paths an agent
can open. `AGENTS.md` holds the always-on summary.

## Project

Factorio-Optimizer is a Kotlin Multiplatform app (Gradle, Kotlin) that computes
layout-optimizing Factorio blueprints. It compiles to a React web app (JS) and a
CLI (JVM) from one shared core.

## Source layout

- `src/commonMain` — shared logic, one package per domain. The dependency
  direction is one-way, from the vocabulary up to the artifact; the exact edges
  each package may use are listed under "Package dependencies" below (see
  `decisions.md`, 2026-10-21 to 2026-10-23):
  - `model` — the grid vocabulary and nothing else: `Tile.kt` (`Coordinate`,
    `Area`, `Dimension`, `Direction`, `Io`), `TileGeometry.kt` (distance and
    compass helpers), `Material.kt`, `IconLayer.kt`. A leaf: it imports no other
    project package.
  - `prototype` — the Factorio rulebook: `Recipe.kt` (`ParsedRecipe`,
    `MachineStats`, `Recipe`, `Subgroup`), `Factory.kt`, `Category.kt`,
    `Logistics.kt` (belt/inserter/pump stats and `LogisticsConfig`),
    `Surface.kt` (`Surface`, `SurfaceProperty/Condition`, the surface rules as
    `Surface.accepts*` extensions).
  - `prototype/selection` — the URL/CLI codecs: `Selection.kt`
    (`Selection`, `SelectionFlag`, `selectionString`, `spaceAgeIn`,
    `fullBeltIn`), `BeltEndSelection.kt` (`beltEndFromSelection/-Identifier`),
    `SurfaceSelection.kt` (`surfaceFromUrl/-Planet`, `forSurface`). Only the
    entry points and `prototype` use it; the layout never sees a URL.
  - `production` — reads the prototype dump: `recipeParser.kt` orchestrates it,
    `RecipeLoader` (the cached per-mods parse), `RecipePrototype.kt`,
    `JsonFields.kt`, `resourceRecipes.kt`, `customRecipes.kt`
    (`ALL_SCIENCE_PACKS`), `spoilageRecipes.kt`, `utils.kt` (normalisation);
    `production/stats` holds `machineStats`/`logisticsStats`.
  - `service` — the optimizer: `Optimizer.kt` and `MachineCatalog.kt` (machine
    choice and rates), `LinearProgram.kt`, and `OptimizationResult.kt`
    (`OptimizationResult`, `FactoryProduction`). It takes a `ParsedRecipe` in and
    returns an `OptimizationResult`; it never loads the dump or builds a
    blueprint.
  - `util` — `MinHeap`, `connectedComponents`.
  - `plan` — the vocabulary the stages share: `BlueprintPlan` and its result,
    the `Planned*` types, the machine footprint and fluid ports, their budgets
    (`MAX_COPIES`, `GROUP_MAX_MACHINES`), `ImportExtension` (the import state the
    marker, trim, compaction and power stages share), and `LogisticsCalculator`
    (the layout's belt/inserter rates).
  - `band` — the direct-feed band: `BandSearch`.
  - `route` — the weighted A* shared by belts and pipes (`LayoutRouter`,
    `RouteSearch`, `RouteProfile`); it sits below `routing`, `plumbing` and
    `planning`, which all drive it.
  - `layout` — the grid kernel: `LayoutBoard` (tiles, reservations, primitives),
    `LayoutPlacement` (entity-kind helpers), `LayoutUnderground`, `Tunnels`,
    `LayoutIndex` (the read-only tile index), `BeltEnd` (how a belt is
    terminated). `layout/entity` holds the entity hierarchy (`LayoutEntity`,
    `LayoutTransportEntities`, `LayoutEntityStations`, `LayoutSettings`);
    `layout/validation` holds the invariant checks (`LayoutValidation*.kt`).
    This tree must never mention the blueprint.
  - `beltend` — `BeltEndConnector.kt`: the solid belt's end marker (label,
    infinity chest, chest).
  - `planning` — the row planner (`RowPlanner`, `RowPlacer`, `Row` and the
    inserter/interface placers) and the shared `LayoutStage`.
  - `routing` — implicit delivery (`Delivery*`, `Import*`, `SplitDeliveryRouter`)
    and `BeltRouteRules`; `plumbing` — the fluid feed (`FluidFeeder`, `FluidLaneFeed`);
    `special` — the path, power and material-search pass; `compaction` —
    `LayoutCompaction`, `CompactionPass`, `SeamGrid`, `UndergroundStretcher`.
  - `blueprint` — the artifact and its assembly: `Blueprint.kt` (`Blueprint`, its
    entities and wires), `BlueprintLayout.kt` (`fromPlaced`, the ordered stage
    pipeline). `blueprint/planning` turns an `OptimizationResult` into a plan
    (`blueprintPlan`, `includedRecipes`, `plannedSources`); `blueprint/output`
    encodes it (`BlueprintString.kt`, `FactorioUrl.kt`, `toBlueprintEntity` in
    `LayoutSerialization.kt`, `chestWires`); `blueprint/inspection` renders the
    ASCII layout and the dump.
  - `blueprint/json` — `Blueprint.toJson`: the wire JSON, a leaf nothing else may
    import, so the encoder stays out of every stage.
  - plus the dumped `prototypes/` resources.
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, `styled-next`,
  `react-router-dom`); entry `js/index.kt`; `js/common/production/loadRecipe.kt`
  pulls the dumps via `@JsModule` and the webpack config in `webpack.config.d/`.
- `src/jvmMain` — CLI entry `jvm/CliOptimizer.kt`, argument parsing
  `jvm/CliArgsParser.kt`; `jvm/common/production/loadRecipe.kt`
  loads recipes from a classpath resource.
- `src/commonTest` — shared kotlin-test suites, run by both `jvmTest` and
  `jsBrowserTest`; must stay platform-neutral. Every suite sits in the package it
  tests (`commonTest/…/common/blueprint/…`, `.../production/…`), and shared
  helpers live in that package's `fixtures` package (`service/fixtures`:
  `testLogistics`, `calculator`, the optimizer fixtures; `blueprint/fixtures`:
  materials, recipes, plans, layout assertions).
- `src/jvmTest` / `src/jsTest` — platform-specific tests; JVM suites that need
  the build output or the source tree (`IconCoverageTest`) and the CLI
  integration tests live there.

`commonMain` compiles to both targets, so platform differences belong in the
target-specific `loadRecipe.kt`/`index.kt` files (the `loadRecipe.kt` ones are
`expect`/`actual` pairs with the common declaration, both in
`common.production`).

Current sizes, files / lines — **files** are the budget: a package that grows
past 10 files is a signal to split it, with 15 the hard ceiling. There is no
package line budget: file length is capped per file by `LargeClass`.

| package | files | lines |
| --- | --- | --- |
| band | 1 | 281 |
| beltend | 1 | 180 |
| blueprint | 2 | 267 |
| blueprint/inspection | 3 | 229 |
| blueprint/json | 2 | 211 |
| blueprint/output | 4 | 265 |
| blueprint/planning | 2 | 213 |
| compaction | 4 | 730 |
| layout | 6 | 435 |
| layout/entity | 4 | 255 |
| layout/validation | 4 | 235 |
| model | 4 | 216 |
| plan | 5 | 547 |
| planning | 9 | 1195 |
| plumbing | 2 | 408 |
| production | 9 | 511 |
| production/stats | 1 | 142 |
| prototype | 5 | 673 |
| prototype/selection | 3 | 134 |
| route | 3 | 343 |
| routing | 7 | 864 |
| service | 4 | 607 |
| special | 4 | 601 |
| util | 2 | 85 |

`production` (9) is the closest to the warn line.

## Package dependencies

Each package imports the others by name (no project wildcards, enforced by
`WildcardImport` in `config/detekt/detekt.yml`), and a symbol no other package needs
is `internal`: the compiler then rejects a cross-package use. That is the whole
enforcement: **no test checks these edges** — a hand-written guard test does not
belong in the app suites (see `decisions.md`, 2026-10-23). The table below is the
intent; keep a change to it deliberate and reviewable, never widen a row to make an
existing edge legal.

The arrows point one way:

```
model < prototype < production < service < plan, route, layout <
beltend, band < planning < routing, plumbing, special, compaction < blueprint <
blueprint.output, blueprint.inspection
```

| package | may use | may not reach |
| --- | --- | --- |
| `model` | nothing | everything else |
| `prototype` | `model` | `production`, `service`, … |
| `prototype.selection` | `model`, `prototype`, `layout` | `production` and above (the URL codecs must not load the dump) |
| `production` | `model`, `prototype`, `production.stats` | `service` and above |
| `production.stats` | `model`, `prototype`, `production` | `service` and above |
| `service` | `model`, `prototype` | `production` (the dump loader), `layout`, `blueprint`, the URL codecs |
| `util` | nothing | every project package |
| `plan` | `model`, `prototype`, `service`, `util` | `layout`, `blueprint`, the stages |
| `band` | `model`, `prototype`, `plan`, `service` | the layout |
| `route` | `model`, `prototype`, `util`, `layout` | `beltend`, `routing`, `planning`, `blueprint` |
| `layout` | `model`, `prototype`, `layout.entity`, `util` | `special`, `beltend`, `blueprint` |
| `layout.entity` | `model` | everything else |
| `layout.validation` | `model`, `layout`, `layout.entity` | `beltend` and above |
| `beltend` | `model`, `layout`, `layout.entity` | `blueprint` |
| `compaction` | `model`, `layout`, `layout.entity`, `layout.validation`, `plan` | `beltend`, `routing`, `blueprint` |
| `planning` | `model`, `prototype`, `plan`, `layout`, `layout.entity`, `route` | `special`, `routing`, `blueprint` |
| `special` | `model`, `prototype`, `plan`, `band`, `service`, `util`, `layout`, `layout.entity` | `routing`, `beltend`, `blueprint` |
| `plumbing` | `model`, `prototype`, `plan`, `planning`, `route`, `special`, `layout`, `layout.entity` | `routing`, `beltend`, `blueprint` |
| `routing` | `model`, `prototype`, `plan`, `planning`, `route`, `special`, `beltend`, `layout`, `layout.entity`, `layout.validation` | `compaction`, `blueprint` |
| `blueprint` | everything below it, plus `production`, `service` (the composition root) | `blueprint.json` |
| `blueprint.planning` | `model`, `prototype`, `production`, `service`, `plan`, `band`, `special` | `layout` (it builds a plan, not a grid) |
| `blueprint.output` | `model`, `prototype`, `production`, `layout`, `layout.entity`, `blueprint` | `blueprint.json` |
| `blueprint.inspection` | `model`, `layout`, `layout.entity` | everything above (it only reads a finished layout) |
| `blueprint.json` | `model`, `layout`, `layout.entity`, `blueprint` | — and **nothing may import it**: it is a leaf writer |

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

`blueprint/planning/blueprintPlan` (`blueprintPlan.kt`) builds the plan from the
optimizer's `OptimizationResult`; `blueprint/BlueprintLayout.kt` turns a
`BlueprintPlan` plus a `LogisticsCalculator` into a `Blueprint`
(`blueprint/Blueprint.kt`). `fromPlaced(plan, logistics, beltEnd)` runs
`RowLayout` in ordered passes — recipe rows, target output, imports,
routes, fluid feeds, trim import prefixes, stretch underground runs, compaction,
poles, pole wires — and returns a `Blueprint` whose `entities` become the
`entities` array (numbered by list position) and whose `wires` become the
top-level `wires` array. The order is load-bearing: pipes go before belts, the
import trim before the underground stretch, compaction before the poles.
`blueprintLayoutDump` (`blueprint/inspection/`) is the one read-back of a finished
layout (`LayoutDump`: placed `LayoutEntity`s plus failure strings); the CLI's
`--layout` renders it and
the test suites assert on it via `commonTest/…/common/blueprint/fixtures/BlueprintLayoutAccess.kt`,
so `RowLayout` and its accessors stay private.

The layout is split into a small mutable kernel plus stages, and every stage takes
one `planning/LayoutStage.kt` context (`board`, `plan`, `logistics`, `planner`,
`rowPlacer`, `router`) instead of repeating those collaborators:

- `layout/LayoutBoard.kt` — the tile grid, the placed entities, route bounds and
  the reserve/place/remove primitives. Entity placement lives in
  `LayoutPlacement.kt`, the tunnel index in `Tunnels.kt` and the underground queries
  in `LayoutUnderground.kt`, all as board extensions. `LayoutPipes.kt` holds the
  one pipe-tile rule, `canPlacePipes` (the tile is free and no placed pipe beside
  it carries another fluid — touching pipes merge in Factorio), which the lane
  placer and the routed fluid feed share; `touchesForeign` is its neighbor test,
  while a tile merely *reserved* for another fluid stays the caller's check
  (`FluidFeeder.pipeAllowed` via `edgeFluidAt`).
- `route/LayoutRouter.kt` — the belt/pipe profiles and the routing entry points;
  `route/RouteProfile.kt` groups the costs (`RouteCosts`) and tunnel rules (`TunnelRules`) with
  the `RouteRequest`, and `route/RouteSearch.kt` owns the weighted A* frontier and node
  expansion.
- `special/PathPlacer.kt` — `placePath`, the tile roles for plain and underground
  runs, and `directionBetween`; shared by belt and pipe routes. It is the shared
  helper `routing` reaches (the `special` edge in the package dependency table).
- `compaction/UndergroundStretcher.kt` — grows underground runs along their own
  straight surface run; its `RunGrowth` owns one run's budget, swallow rule and
  end sharing.
- `planning/RowPlanner.kt` — the plan-level row order: the band-aware producer
  map, allocation depth, row units and the pure sizing helpers (`copiesOf`,
  `inserterPattern`).
- `planning/RowPlacer.kt` — places the machine rows (`Row` = `RowBox` +
  `RowLanes` + `RowShape` + machines, declared in `Row.kt`), the bands, lanes and
  product, and owns the allocation-to-row index and the lane direction.
  `Row.box.machines` carries each allocation's prototype and ingredients.
  `MachineInserterPlacer` places the machine-edge inserters and
  `BandInterfacePlacer` the gap inserters.
- `planning/FluidLane.kt` — the board-free lane shape: a plain junction pipe above
  every port, a horizontal pipe-to-ground run across every gap (a pair of adjacent
  ends in the two-tile gap between touching machines) and one vertical shaft per
  port, whose ends sit one row inside the lane and on the port tile.
  `planning/RowFluidLane.kt` decides which fluid a row gets on which side, the port
  per machine and the lane row (two rows beyond the outermost belt).
  `planning/FluidLanePlacer.kt` puts it on the board: the inline `oCo` marker, the
  horizontal runs, the shaft ends and the tunnel index, skipping a shaft that finds
  no room so only that port falls back to the route.
- `plan/ImportExtension.kt` — the shared import bookkeeping: the marker rows
  already claimed, the import feeds to trim, the pinned marker tiles and the
  pole/compaction west bounds.
- `plumbing/FluidFeeder.kt` — supplies every fluid. It first lets
  `plumbing/FluidLaneFeed.kt` place the lane of every row that gets one (north and
  south side), records the port tiles those lanes reach and leaves them alone, then
  reserves the remaining machine edges (`fluidEdge`) and grows one routed network
  per fluid from its marker (`pipeAllowed`, `pipeRoute`). The lane is primary; the
  A* path is only the fallback for a side port, a machine without a port table or a
  shaft that found no room. It runs after `RowPlacer.placeAll()`, so the belts and
  inserters a shaft buries itself under already stand (see `decisions.md`,
  2026-10-23).
- `routing/ImportTrimmer.kt` — moves every import marker to one common column and
  removes the straight belt prefix in front of it (`lastStraightEast`). A **fluid**
  lane marker moves into that same column and joins its lane head with
  pipe-to-ground runs along the lane row (`FluidFeed` in
  `plan/ImportExtension.kt`); the marker, link and head are pinned so compaction
  shifts them as one group.
- `compaction/CompactionPass.kt` — the seam-compaction loop; it re-stretches the
  underground runs after each corridor and shifts the import pins and bounds.
  The solid and fluid import markers, their links and the lane head are pinned
  (`ImportExtension.pinnedImportTiles`, passed to `nextCompaction` as `pinned`),
  so compaction moves them as one column; `fluidPorts` stays read-only (the
  stretcher uses it to avoid swallowing a port).
- `compaction/LayoutCompaction.kt` — seam compaction over a `List<LayoutEntity>`;
  `SeamGrid.kt` owns the removable-run grid and its `SeamSearch` dynamic program, and
  `CorridorShift` cuts one corridor and moves the far side.
- `special/PowerPlacer.kt` — pole coverage and pole-wire neighbours over the
  final geometry.
- `beltend/BeltEndConnector.kt` — the label, infinity-chest and chest belt ends,
  their connector width/offset, and the chest circuit wires.
- `routing/DeliveryRouter.kt` — orders the solid deliveries and places the
  target's output connector; `DeliveryPreparation` reserves the sinks and sources,
  `SplitDeliveryRouter` inserts the branch splitters, `DeliveryNetworks` remembers
  the produced trunks and `BeltRouteRules` is the belt-tile predicate shared by
  both routes.
- `RowLayout` keeps the ordered `build()` pipeline; the `RowPlacer`,
  `ImportExtension`, `FluidFeeder`, `ImportTrimmer` and `CompactionPass` stages own
  the rest.

`common/util/Graph.kt` holds `connectedComponents`, used by pole wiring and the
layout test fixtures instead of a per-suite breadth-first walk.

`special/DirectFeed.kt` (pure, commonMain) decides which recipes a plan builds as
a **direct-feed band**: a solid material whose producer and consumer stand in a
small whole-machine cycle (`cycleRatio`, per-machine rates with productivity) is
inserted machine to machine, the recipes become one interleaved row, and
`BlueprintPlan.bands` (`PlannedBand`/`PlannedLink`) is the single source of truth
for it. `band/BandSearch` finds the shortest window (`FeedWindow` checks that every
consumer is covered and every producer feeds someone). `RowPlacer` places a band with `placeBand`; only belted allocations feed
belts, lanes and sinks, so a band's internal material never gets a belt. See
`decisions.md` (2026-10-18) for the rules and the open fluid/line-breaking
follow-ups.

A solid belt's end is a `BeltEnd` (`model/BeltEnd.kt`): the constant-combinator
label (default), the infinity chest, or a chest the inserters feed
(`buffer`/`storage`/`wooden`/`steel`). `placeSolidConnector` places it and
`connectorWidth`/`connectorLeft` keep the routing corridors aligned; a fluid belt
always keeps the combinator/pipe label. `blueprint/json/BlueprintJson.kt` writes the
blueprint envelope (`label`, `icons`, `entities`, top-level `wires`);
`blueprint/json/BlueprintEntityJson.kt` writes each entity's position and optional
`request_filters`, `bar`, `mirror`, `control_behavior` (circuit condition),
`infinity_settings` and `neighbours` fields. See `decisions.md` (2026-10-18) for
the geometry and `pitfalls.md` for the wire and pole traps.

The layout tests are grouped by scenario (`BlueprintLayoutTest`,
`BlueprintImportTest`, `BlueprintFluidLayoutTest`, `BlueprintBandTest`,
`BlueprintBeltEndTest`), and `BlueprintLayoutSnapshotTest` pins the exact entity
order for representative plans while the layout is being split into stages. The
JVM integration suite is split by plan, layout, power, fluid, belt end and
serialization. `./gradlew check` runs Detekt (see `conventions.md`): the plain
`detekt` task over all of `src` plus the type-resolved `detektMainJvm`/
`detektTestJvm`; there are no committed baselines, so every finding fails.


## Generated data — do not hand-edit

- `src/commonMain/resources/prototypes/{base,space-age}.json` are `data.raw`
  dumps from `factorio --dump-data` for the base game and base + Space Age DLC.
  Regenerate with `./gradlew dumpPrototypes` (needs a Factorio Steam install).
  Because the game resolves the whole data stage, the dump already contains every
  `data-updates.lua`/`data-final-fixes.lua` change; the parser reads only the
  prototype types it needs. `RecipeLoader` (`production/`) caches parsed results; the UI and
  CLI use the base dump, or the Space Age dump when Space Age is active.
- `src/jsMain/resources/icons/*.png` are 120x64 crops from a Linux Factorio
  Steam install. `./gradlew updateImages` copies and optimizes only icons whose
  top-left 64x64 region changed significantly (needs `imagemagick optipng
  pngquant`). Do not add icons by hand.

## Prerequisites

- JDK 25 is required. `gradle.properties` sets
  `org.gradle.java.installations.auto-download=false`, so a local JDK 25 must
  exist or the build fails with a cryptic error (only the installed version
  string). CI image:
  `registry.gitlab.com/bjrke/docker-gradle-chrome:latest` (Gradle JDK 25 plus
  Google Chrome for `jsBrowserTest`).

## Related tooling

- `buildSrc/` — Gradle build logic (`dumpPrototypes`, `sanitizePrototypes`,
  `updateImages` are the `factorio` group tasks).
- `.opencode/skills/` — the Factorio blueprint and optimizer skills.
