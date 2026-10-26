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
    entry points (UI, CLI) and the tests use it; the layout never sees a URL.
  - `production` — reads the prototype dump: `recipeParser.kt` orchestrates it,
    `RecipeLoader` (the cached per-mods parse), `prototypeDump.kt` (the
    `loadPrototypeDump` `expect` declaration), `RecipePrototype.kt`,
    `JsonFields.kt`, `resourceRecipes.kt`, `customRecipes.kt`
    (`ALL_SCIENCE_PACKS`), `spoilageRecipes.kt`, `utils.kt` (normalization) and
    `prototypeStats.kt` (`machineStats`/`logisticsStats`).
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
  - `routing` — implicit delivery (`Delivery*`, `Import*`, `FluidFeedLinker`,
    `SplitDeliveryRouter`) and `BeltRouteRules`; `plumbing` — the fluid feed
    (`FluidFeeder`, `FluidLaneFeed`);
    `special` — the path, power and material-search pass; `compaction` —
    `LayoutCompaction`, `CompactionPass`, `SeamGrid`, `UndergroundStretcher`.
  - `blueprint/model` — the wire DTO: `Blueprint`, `BlueprintEntity`,
    `BlueprintWire`. It imports only `model` and `layout.entity`, so the encoders
    and the assembly can share it without a cycle.
  - `blueprint` — the assembly: `BlueprintLayout.kt` (`fromPlaced`, the ordered
    stage pipeline). `blueprint/planning` turns an `OptimizationResult` into a plan
    (`blueprintPlan`, `includedRecipes`, `plannedSources`); `blueprint/output`
    encodes it (`BlueprintString.kt`, `FactorioUrl.kt`, `toBlueprintEntity` in
    `LayoutSerialization.kt`, `chestWires`); `blueprint/inspection` renders the
    ASCII layout and the dump.
  - `blueprint/json` — `Blueprint.toJson`: the wire JSON, a leaf nothing else may
    import, so the encoder stays out of every stage.
  - plus the dumped `prototypes/` resources.
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, the TanStack router
  `tanstack.react.router`, `emotion-react`); entry `js/index.kt`;
  `common/production/loadRecipe.kt` pulls the dumps via `@JsModule` and the
  webpack config in `webpack.config.d/`.
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

Package sizes (files, verified 2026-10-26). **Files** are the budget: past 10 is a
signal to split, 15 the ceiling. Nothing automates it — the file-count test was
removed, so review keeps it (see `decisions.md`, 2026-10-22). There is no line
budget; `LargeClass` caps a class at 500 lines. Recheck a count with
`find src/commonMain/…/<package> -maxdepth 1 -name '*.kt' | wc -l`.

| package | files |
| --- | --- |
| band | 1 |
| beltend | 1 |
| blueprint | 1 |
| blueprint/inspection | 3 |
| blueprint/json | 2 |
| blueprint/model | 1 |
| blueprint/output | 4 |
| blueprint/planning | 2 |
| compaction | 4 |
| layout | 8 |
| layout/entity | 4 |
| layout/validation | 4 |
| model | 4 |
| plan | 5 |
| planning | 9 |
| plumbing | 2 |
| production | 10 |
| prototype | 5 |
| prototype/selection | 3 |
| route | 3 |
| routing | 7 |
| service | 4 |
| special | 4 |
| util | 2 |

`production` is at 10 files — the warn line itself, so split it before adding a
file; `planning` (9) is next.

## Package dependencies

Each package imports the others by name (no project wildcards; the
`WildcardImport` rule in `config/detekt/detekt.yml` allows only `de.bjrke.test.*`
from this repository), and a symbol no other package needs is `internal`: the
compiler then rejects a cross-package use. That is the whole enforcement: **no test
checks these edges** — a hand-written guard test does not belong in the app suites
(see `decisions.md`, 2026-10-22). The table below is the intent; keep a change to it
deliberate and reviewable, never widen a row to make an existing edge legal.

The arrows point one way:

```
model < prototype < production < service < plan, route, layout <
beltend, band < planning < routing, plumbing, special, compaction <
blueprint.model < blueprint.output, blueprint.json < blueprint
blueprint.planning, blueprint.inspection < blueprint
```

`blueprint.model` is the DTO leaf the encoders share; `blueprint` is the assembly
root and imports `blueprint.planning`, `blueprint.output` (`toBlueprintEntity`,
`chestWires`), `blueprint.inspection` (`LayoutDump`) and `blueprint.model`. Only
the entry points (UI, CLI) and the tests import `blueprint` itself, for
`fromPlaced`.

| package | may use | may not reach |
| --- | --- | --- |
| `model` | nothing | everything else |
| `prototype` | `model` | `production`, `service`, … |
| `prototype.selection` | `model`, `prototype`, `layout` | `production` and above (the URL codecs must not load the dump) |
| `production` | `model`, `prototype` | `service` and above |
| `service` | `model`, `prototype` | `production` (the dump loader), `layout`, `blueprint`, the URL codecs |
| `util` | nothing | every project package |
| `plan` | `model`, `prototype`, `service` | `layout`, `blueprint`, the stages |
| `band` | `plan`, `prototype`, `service` | the layout |
| `route` | `model`, `prototype`, `util`, `layout` | `beltend`, `routing`, `planning`, `blueprint` |
| `layout` | `model`, `prototype`, `layout.entity` | `special`, `beltend`, `blueprint` |
| `layout.entity` | `model` | everything else |
| `layout.validation` | `model`, `layout`, `layout.entity` | `beltend` and above |
| `beltend` | `model`, `layout`, `layout.entity` | `blueprint` |
| `compaction` | `model`, `layout`, `layout.entity`, `layout.validation`, `plan` | `beltend`, `routing`, `blueprint` |
| `planning` | `model`, `prototype`, `plan`, `layout`, `layout.entity`, `route` | `special`, `routing`, `blueprint` |
| `special` | `model`, `prototype`, `plan`, `band`, `service`, `util`, `layout`, `layout.entity` | `routing`, `beltend`, `blueprint` |
| `plumbing` | `model`, `prototype`, `plan`, `planning`, `route`, `special`, `layout`, `layout.entity` | `routing`, `beltend`, `blueprint` |
| `routing` | `model`, `prototype`, `plan`, `planning`, `route`, `special`, `beltend`, `layout`, `layout.entity`, `layout.validation` | `compaction`, `blueprint` |
| `blueprint` | the stage packages it drives, plus `blueprint.planning`, `blueprint.model`, `blueprint.output`, `blueprint.inspection` (the assembly root) | `blueprint.json` |
| `blueprint.model` | `model`, `layout.entity` | everything above (the DTO leaf) |
| `blueprint.planning` | `model`, `prototype`, `production`, `service`, `plan`, `special` | `layout` (it builds a plan, not a grid), `band` directly |
| `blueprint.output` | `model`, `production`, `layout.entity`, `blueprint.model` | `blueprint`, `blueprint.json` |
| `blueprint.inspection` | `model`, `layout.entity` | `blueprint` and above (it only reads a finished layout) |
| `blueprint.json` | `model`, `layout.entity`, `blueprint.model` | — and **nothing may import it**: it is a leaf writer |

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
optimizer's `OptimizationResult`; `blueprint/BlueprintLayout.kt` turns a
`BlueprintPlan` plus a `LogisticsCalculator` into a `Blueprint`
(`blueprint/model/Blueprint.kt`). `fromPlaced(plan, logistics, beltEnd)` runs
`RowLayout` in ordered passes — recipe rows, target output, import preparation,
fluid feeds (the lane first, then the routed fallback), solid deliveries, the
fluid target output, trim import prefixes, stretch underground runs, compaction,
poles (which place their wires) — and returns a `Blueprint` whose `entities` are
numbered by list position and whose `wires` are the chest-connector wires from
`blueprint/output/ChestWires.kt`. The order is load-bearing: pipes go before
belts, the import trim before the underground stretch, compaction before the
poles.
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
- `planning/RowPlanner.kt` — the plan-level row order: the depth of every row
  (a DFS over `BlueprintPlan.rows` with a recipe path as the cycle guard), the
  `RowUnit(row, depth)` order and the pure sizing helpers (`inserterPattern`).
- `planning/RowPlacer.kt` — places the machine rows (`Row` = `PlannedRow` +
  `RowBox` + `RowLanes` + machines, declared in `Row.kt`), the bands, lanes and
  product, and owns the ordered `placedRows` list and the lane direction. A
  `PlacedMachine` carries its own `plannedRecipe`; `producerRow(material)` scans
  `placedRows` for the row whose `plannedRow` equals
  `BlueprintPlan.producerOf(material)`.
  `MachineInserterPlacer` places the machine-edge inserters and
  `BandInterfacePlacer` the gap inserters.
- `planning/FluidLane.kt` — the board-free lane shape: a plain junction pipe above
  every port, a horizontal pipe-to-ground run across every gap (a pair of adjacent
  ends in the two-tile gap between touching machines) and one vertical shaft per
  port, whose ends sit one row inside the lane and on the port tile.
  `planning/RowFluidLane.kt` assigns the recipe's fluids to the machine ports in
  recipe order, decides which side that puts the lane on and the lane row (two rows
  beyond the outermost belt).
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
  shaft that found no room. A fluid target whose output port is on a side edge is
  routed to a marker by the same path (`connectTargetOutput`), not left as a
  stub. It runs after `RowPlacer.placeAll()`, so the belts and inserters a shaft
  buries itself under already stand (see `decisions.md`, 2026-10-26).
- `routing/ImportTrimmer.kt` — moves every import marker to one common column and
  removes the straight belt prefix in front of it (`lastStraightEast`). A **fluid**
  lane marker moves into that same column; `routing/FluidFeedLinker.kt` joins its
  lane head with pipe-to-ground runs along the lane row (`FluidFeed` in
  `plan/ImportExtension.kt`). The marker, link and head are pinned so compaction
  shifts them as one group.
- `compaction/CompactionPass.kt` — the seam-compaction loop; it re-stretches the
  underground runs after each corridor and shifts the import pins and bounds.
  The solid and fluid import markers, their links and the lane head are pinned
  (`ImportExtension.pinnedImportTiles`, passed to `nextCompaction` as `pinned`),
  so compaction moves them as one column; `fluidPorts` stays read-only (the
  stretcher uses it to avoid swallowing a port).
- `compaction/LayoutCompaction.kt` — seam compaction over a `List<LayoutEntity>`;
  `SeamGrid.kt` owns the removable-run grid and its `SeamSearch` dynamic program;
  `CorridorShift` (in `LayoutCompaction.kt`) cuts one corridor and moves the far
  side.
- `special/PowerPlacer.kt` — pole coverage and pole-wire neighbours over the
  final geometry.
- `beltend/BeltEndConnector.kt` — the label, infinity-chest and chest belt ends,
  their connector width/offset, and the chest circuit wires.
- `routing/DeliveryRouter.kt` — orders the solid deliveries and places the
  target's output connector; `DeliveryPreparation` reserves the sinks and sources,
  `SplitDeliveryRouter` inserts the branch splitters, `DeliveryNetworks` remembers
  the produced trunks and `BeltRouteRules` is the belt-tile predicate shared by
  both routes. `routing/FluidFeedLinker.kt` links a trimmed fluid marker to its
  lane head.
- `RowLayout` keeps the ordered `build()` pipeline; the `RowPlacer`,
  `ImportExtension`, `FluidFeeder`, `ImportTrimmer` and `CompactionPass` stages own
  the rest.

`common/util/Graph.kt` holds `connectedComponents`, used by pole wiring and the
layout test fixtures instead of a per-suite breadth-first walk.

`special/DirectFeed.kt` (pure, commonMain) decides which recipes a plan builds as
a **direct-feed band**: a solid material whose producer and consumer stand in a
small whole-machine cycle (`cycleRatio`, per-machine rates with productivity) is
inserted machine to machine, the recipes become one interleaved row, and
`BlueprintPlan.rows` (`PlannedRow`/`PlannedLink`) is the single source of truth
for it. A band is a `PlannedRow` whose `links` are non-empty and whose `machines`
hold one `PlannedRecipe` per machine; a plain row repeats its one recipe once per
machine and has no links. `band/BandSearch` finds the shortest window
(`FeedWindow` checks that every consumer is covered and every producer feeds
someone). `RowPlacer` places a band with `placeBand`; only belted planned recipes
feed belts, lanes and sinks, so a band's internal material never gets a belt. See
`decisions.md` (2026-10-18) for the rules and the open fluid/line-breaking
follow-ups, and (2026-10-24) for why the row is the occurrence.

A solid belt's end is a `BeltEnd` (`layout/BeltEnd.kt`): the constant-combinator
label (default), the infinity chest, or a chest the inserters feed
(`buffer`/`storage`/`wooden`/`steel`). `placeSolidConnector` places it;
`BeltEndConnector.width`/`left` keep the routing corridors aligned; a fluid belt
always keeps the combinator/pipe label. `blueprint/json/BlueprintJson.kt` writes the
blueprint envelope (`label`, `icons`, `entities`, top-level `wires`);
`blueprint/json/BlueprintEntityJson.kt` writes each entity's position and optional
`request_filters`, `bar`, `mirror`, `control_behavior` (circuit condition),
`infinity_settings` and `neighbours` fields. An infinity pipe writes
`infinity_settings` only as a source; a sink is unfiltered and writes none, so
the game deletes whatever reaches it. See `decisions.md` (2026-10-18) for the
geometry and `pitfalls.md` for the wire and pole traps.

The layout tests are grouped by scenario (`BlueprintLayoutTest`,
`BlueprintImportTest`, `BlueprintFluidLayoutTest`, `BlueprintBandTest`,
`BlueprintBeltEndTest`); `BlueprintLayoutSnapshotTest` hashes the canonical
entity/wire list of representative plans, so any geometry or entity-order change
is deliberate. The JVM integration suite is split by plan, layout, power, fluid,
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
  prototype types it needs. `RecipeLoader` (`production/`) caches parsed results; the UI and
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
