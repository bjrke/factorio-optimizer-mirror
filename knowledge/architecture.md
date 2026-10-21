# Architecture

Verified facts about how this repository is put together. Keep this a map, not a
tutorial: it should say where things live and how data flows, with paths an agent
can open. `AGENTS.md` holds the always-on summary.

## Project

Factorio-Optimizer is a Kotlin Multiplatform app (Gradle, Kotlin) that computes
layout-optimizing Factorio blueprints. It compiles to a React web app (JS) and a
CLI (JVM) from one shared core.

## Source layout

- `src/commonMain` — shared logic: recipe parsing (`recipeParser.kt` orchestrates
  the dump, `RecipePrototype.kt` reads one recipe, `JsonFields.kt` holds the JSON
  accessors, `prototypeStats.kt` the machine/logistics stats and
  `resourceRecipes.kt` the map extraction recipes), optimizer (`Optimizer.kt`, with
  `MachineCatalog` for the machine choice and rates), the model, and the dumped
  `prototypes/` data.
- `src/jsMain` — React web UI (kotlin-wrappers: `react`, `styled-next`,
  `react-router-dom`); entry `js/index.kt`; `js/business/loadRecipe.kt` pulls the
  dumps via `@JsModule` and the webpack config in `webpack.config.d/`.
- `src/jvmMain` — CLI entry `jvm/CliOptimizer.kt`, argument parsing
  `jvm/CliArgsParser.kt`; `jvm/business/loadRecipe.kt`
  loads recipes from a classpath resource.
- `src/commonTest` — shared kotlin-test suites, run by both `jvmTest` and
  `jsBrowserTest`; must stay platform-neutral.
- `src/jvmTest` / `src/jsTest` — platform-specific tests.

`commonMain` compiles to both targets, so platform differences belong in the
target-specific `loadRecipe.kt`/`index.kt` files.

## Blueprint layout

`business/BlueprintLayout.kt` turns a `BlueprintPlan` plus a `LogisticsCalculator`
into a `Blueprint` (`model/Blueprint.kt`). `buildBlueprint(plan, logistics,
beltEnd)` runs `RowLayout` in ordered passes — recipe rows, target output, imports,
routes, fluid feeds, trim import prefixes, stretch underground runs, compaction,
poles, pole wires — and returns a `Blueprint` whose `entities` become the
`entities` array (numbered by list position) and whose `wires` become the
top-level `wires` array. The order is load-bearing: pipes go before belts, the
import trim before the underground stretch, compaction before the poles.
`blueprintLayoutDump` is the one read-back of a finished layout (`LayoutDump`:
placed `LayoutEntity`s plus failure strings); the CLI's `--layout` renders it and
the test suites assert on it via `commonTest/…/business/fixtures/BlueprintLayoutAccess.kt`,
so `RowLayout` and its accessors stay private.

The layout is split into a small mutable kernel plus stages, and every stage takes
one `business/LayoutStage.kt` context (`board`, `plan`, `logistics`, `planner`,
`rowPlacer`, `router`) instead of repeating those collaborators:

- `business/LayoutBoard.kt` — the tile grid, the placed entities, route bounds and
  the reserve/place/remove primitives. Entity placement lives in
  `LayoutPlacement.kt`, the tunnel index in `Tunnels.kt` and the underground queries
  in `LayoutUnderground.kt`, all as board extensions.
- `business/LayoutRouter.kt` — the belt/pipe profiles and the routing entry points;
  `RouteProfile.kt` groups the costs (`RouteCosts`) and tunnel rules (`TunnelRules`) with
  the `RouteRequest`, and `RouteSearch.kt` owns the weighted A* frontier and node
  expansion.
- `business/PathPlacer.kt` — `placePath`, the tile roles for plain and underground
  runs, and `directionBetween`; shared by belt and pipe routes.
- `business/UndergroundStretcher.kt` — grows underground runs along their own
  straight surface run; its `RunGrowth` owns one run's budget, swallow rule and
  end sharing.
- `business/RowPlanner.kt` — the plan-level row order: the band-aware producer
  map, allocation depth, row units and the pure sizing helpers (`copiesOf`,
  `inserterPattern`).
- `business/RowPlacer.kt` — places the machine rows (`Row` = `RowBox` +
  `RowLanes` + machines), the bands, lanes and product, and owns the
  allocation-to-row index and the lane direction. `MachineInserterPlacer` places
  the machine-edge inserters and `BandInterfacePlacer` the gap inserters.
- `business/ImportExtension.kt` — the shared import bookkeeping: the marker rows
  already claimed, the import feeds to trim, the pinned marker tiles and the
  pole/compaction west bounds.
- `business/FluidFeeder.kt` — routes every fluid: the reserved machine-edge pipes,
  one marker per fluid and the pipe network grown from it (`pipeAllowed`,
  `fluidEdge`).
- `business/ImportTrimmer.kt` — moves every import marker to one common column
  and removes the straight belt prefix in front of it (`lastStraightEast`).
- `business/CompactionPass.kt` — the seam-compaction loop; it re-stretches the
  underground runs after each corridor and shifts the import pins and bounds.
- `business/LayoutCompaction.kt` — seam compaction over a `List<LayoutEntity>`;
  `SeamGrid.kt` owns the removable-run grid and its `SeamSearch` dynamic program, and
  `CorridorShift` cuts one corridor and moves the far side.
- `business/PowerPlacer.kt` — pole coverage and pole-wire neighbours over the
  final geometry.
- `business/BeltEndConnector.kt` — the label, infinity-chest and chest belt ends,
  their connector width/offset, and the chest circuit wires.
- `business/DeliveryRouter.kt` — orders the solid deliveries and places the
  target's output connector; `DeliveryPreparation` reserves the sinks and sources,
  `SplitDeliveryRouter` inserts the branch splitters, `DeliveryNetworks` remembers
  the produced trunks and `BeltRouteRules` is the belt-tile predicate shared by
  both routes.
- `RowLayout` keeps the ordered `build()` pipeline; the `RowPlacer`,
  `ImportExtension`, `FluidFeeder`, `ImportTrimmer` and `CompactionPass` stages own
  the rest.

`common/util/Graph.kt` holds `connectedComponents`, used by pole wiring and the
layout test fixtures instead of a per-suite breadth-first walk.

`business/DirectFeed.kt` (pure, commonMain) decides which recipes a plan builds as
a **direct-feed band**: a solid material whose producer and consumer stand in a
small whole-machine cycle (`cycleRatio`, per-machine rates with productivity) is
inserted machine to machine, the recipes become one interleaved row, and
`BlueprintPlan.bands` (`PlannedBand`/`PlannedLink`) is the single source of truth
for it. `BandSearch` finds the shortest window (`FeedWindow` checks that every
consumer is covered and every producer feeds someone). `RowPlacer` places a band with `placeBand`; only belted allocations feed
belts, lanes and sinks, so a band's internal material never gets a belt. See
`decisions.md` (2026-10-18) for the rules and the open fluid/line-breaking
follow-ups.

A solid belt's end is a `BeltEnd` (`model/BeltEnd.kt`): the constant-combinator
label (default), the infinity chest, or a chest the inserters feed
(`buffer`/`storage`/`wooden`/`steel`). `placeSolidConnector` places it and
`connectorWidth`/`connectorLeft` keep the routing corridors aligned; a fluid belt
always keeps the combinator/pipe label. `model/BlueprintJson.kt` writes the
blueprint envelope (`label`, `icons`, `entities`, top-level `wires`);
`model/BlueprintEntityJson.kt` writes each entity's position and optional
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
`detektTestJvm`, each gated by a committed baseline so only new findings fail.


## Generated data — do not hand-edit

- `src/commonMain/resources/prototypes/{base,space-age}.json` are `data.raw`
  dumps from `factorio --dump-data` for the base game and base + Space Age DLC.
  Regenerate with `./gradlew dumpPrototypes` (needs a Factorio Steam install).
  Because the game resolves the whole data stage, the dump already contains every
  `data-updates.lua`/`data-final-fixes.lua` change; the parser reads only the
  prototype types it needs. `RecipeProvider` caches parsed results; the UI and
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
  `.opencode/plugins/compaction.js` steers the compaction summary; `/handoff` is
  the manual counterpart.
