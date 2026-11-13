# Blueprint layout

The ordered pipeline that turns a `BlueprintPlan` into a `Blueprint`, and the
grid, routing, compaction and printing machinery it runs. Load this file when you
change a layout stage, a route, compaction, poles or the blueprint output; belt
lanes and fluid lanes are in `lanes-fluids.md`.

## Pipeline

`blueprint/planning/blueprintPlan` (`BlueprintPlan.kt`) builds the plan from the
optimizer's `OptimizationResult`; `blueprint/RowLayout.kt` turns a
`BlueprintPlan` plus a `LogisticsCalculator` into a `Blueprint`
(`blueprint/printing/model/Blueprint.kt`). `RowLayout(plan, logistics, beltEnd).blueprint()`
runs the ordered passes — recipe rows, the product-row reservation, import preparation,
fluid lanes and source markers, solid import deliveries, the routed fluid
fallback, produced solid deliveries, the drain/feed lane link, the fluid target
output, trim import prefixes, stretch underground runs, compaction, the solid
target output connector, poles (which place their wires) — and returns a
`Blueprint` whose `entities` are numbered by
list position and whose `wires` are the chest-connector wires from
`blueprint/printing/output/ChestWires.kt`. The order is load-bearing: fluid lanes go
before solid belts, solid imports go before produced deliveries, the lane link
after the other routes so it can go around them, the import trim before the
underground stretch, compaction before the poles. `blueprint/layout/validation` also
requires every routed underground belt end to connect through a same-material
belt or the previous run (an output end may feed the next input directly), every
splitter to be fed on its back face, and no belt to circle back into itself
(`LayoutValidationFlow.kt`); compaction rejects a corridor that would leave an
entrance or exit orphaned (connector ends are recognized by their 2/4-tile partner
pattern).
`RowLayout.layoutDump()` is the one read-back of a finished layout
(`LayoutDump`: placed `LayoutEntity`s plus failure strings); row-budget and target-output
failures, route failures and any `validateLayout` errors of the finished layout share that list,
so a pre-existing error that switches compaction off is reported instead of silently shipping an
uncompacted layout. The CLI's `--layout` renders it and the `jvmTest` suites assert
on it through the `Layout` fixture in
`jvmTest/…/common/blueprint/printing/fixtures/BlueprintIntegrationFixtures.kt`, so `RowLayout`
is `internal` and keeps its accessors private.

The layout is split into a small mutable kernel plus stages, and every stage takes
one `blueprint/planning/row/LayoutStage.kt` context (`board`, `plan`, `logistics`,
`planner`, `rowPlacer`, `router`) instead of repeating those collaborators. The
belt-row and fluid-lane stages are in `lanes-fluids.md`.

- `blueprint/layout/LayoutBoard.kt` — the tile grid, the placed entities, route bounds and
  the reserve/place/remove primitives, including the `reserveRow` section that makes a
  product row a wall for surface routes (a crossing tunnels under it). Single-tile entity placement lives in
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
  caller's check (`FluidFeeder.pipeAllowed` via `edgeFluidAt`). `reserve(…, approach =
  true)` marks the tile a solid feed's belt takes from (`feed - heading.step`) for that
  feed's material without blocking its own route; `reservedMaterialAt` reads it, and
  `BeltRouteRules` lets only that material use it.
- `blueprint/placing/route/LayoutRouter.kt` — the belt/pipe profiles and the routing entry points;
  `blueprint/placing/route/RouteProfile.kt` groups the costs (`RouteCosts`) and tunnel rules (`TunnelRules`) with
  the `RouteRequest`, and `blueprint/placing/route/RouteSearch.kt` owns the weighted A* frontier and node
  expansion. The belt profile prices a bend above a straight tile and a tunnel entry at seven tiles
  (`RouteCosts.turnCost`/`tunnelEntry`), so a route runs straight and rounds a belt row instead of
  tunneling under it; a long crossing still tunnels. The search region is everything placed plus
  `ROUTE_MARGIN` (3) tiles on the north, south and east side and the fixed `ROUTE_WEST_BOUND` on the
  west; `LayoutRouter` retries with `WIDE_ROUTE_MARGIN` only when that box has no route, and records
  the retry in the layout's failures.
- `blueprint/placing/special/PathPlacer.kt` — `placePath` and the tile roles for plain and
  underground runs; shared by belt and pipe routes. `placePath` rejects a belt path that uses one
  tile twice (`pathConflicts`, the tiles are returned and nothing is placed) and lets the caller
  re-route; a pipe may revisit a tile, which is a junction. It is the shared
  helper `routing` reaches (the `special` edge in the package dependency table).
  `directionBetween` is the compass helper on the model (`core/model/Tile.kt`).
- `blueprint/placing/compaction/UndergroundStretcher.kt` — grows underground runs along their own
  straight surface run; its `RunGrowth` owns one run's budget, swallow rule and
  end sharing. It never swallows a fluid port or a belt tile an inserter takes from
  or drops onto, so a run cannot strand a row inserter, and it only grows where a
  same-material full belt still faces into the run past the swallowed tile, so a moved
  entrance or exit keeps its feeder or lane.
- `blueprint/planning/row/RowTopology.kt` — the target-centred row topology. It builds the
  producer graph from each row's external solid inputs (`rowInputs` minus
  `fullyInternalInputs`) and `BlueprintPlan.producerOf`, roots it at the target, sends a direct
  producer north when the plan lists it before the target and south when after, and keeps every
  branch contiguous outward. A target with three or more direct producers distributes them across
  both sides when the largest branch is 3-4 rows (largest north, next south, next north, product
  south); a two-branch target keeps the plan-order split. Every other target keeps the compact
  one-sided order: the target sits at the opposite end, the product moves to the empty side and the
  branches are ordered largest-first, so the largest branch is nearest the target. It owns the
  `RowUnit(index, row, side)` order, the preferred input and output sides, the planned outlet
  columns (`outletX`) and the topology-aware test. Graph nodes are plan-row positions, so
  structurally equal rows stay distinct occurrences.
- `blueprint/planning/row/RowPlanner.kt` — delegates to `RowTopology`, exposes the `RowUnit` order,
  side queries and `outletX`, and keeps the pure sizing helpers (`inserterPattern`).
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
  final geometry. The pole comes from `PowerPole.forSelection`: none in space, the small pole for
  an early-game assembling machine 1 on Nauvis, the medium pole otherwise.
- `blueprint/placing/special/BeltEndConnector.kt` — the label, infinity-chest and chest belt ends,
  their connector width/offset, and the chest circuit wires.
- `blueprint/placing/routing/DeliveryRouter.kt` — routes the solid imports first and the produced
  deliveries afterwards, and places the target's output connector (reporting a target without a
  product belt instead of silently skipping it); `reserveTargetOutput` reserves the product row
  from the placed belt east before any route, and `placeTargetOutput` runs after compaction,
  puts the connector at `maxX + 1`, and routes the product belt last with its half lane (a
  crossing belt is tunneled under). A belt still east of the finished connector is a failure;
  `DeliveryPreparation` reserves the sinks and sources (imports first) and each sink's approach
  tile for its material; a mixed
  input belt contributes two sinks (its straight feeder and its U-turn feeder) and
  pins the merge group. A delivery is re-searched with the tiles blocked when its path uses a
  belt tile twice. `SplitDeliveryRouter` inserts the branch splitters,
  `DeliveryNetworks` remembers the produced trunks and `BeltRouteRules` is the
  belt-tile predicate shared by both routes; it never side-loads a mixed or
  half-lane belt and keeps foreign belts off another feed's approach tile.
  `blueprint/placing/routing/FluidFeedLinker.kt` links a trimmed
  fluid marker to its lane head.
- `RowLayout` keeps the ordered `build()` pipeline (idempotent, so `blueprint()`
  and `layoutDump()` can share one instance); the `RowPlacer`,
  `ImportExtension`, `FluidFeeder`, `ImportTrimmer` and `CompactionPass` stages own
  the rest.

`common/core/util/Graph.kt` holds `connectedComponents`, used by pole wiring and the
layout test fixtures instead of a per-suite breadth-first walk.
`common/core/util/MathUtils.kt` holds the generic `countVectors` and
`greatestCommonDivisor` the band search uses.

## Belt ends and printing

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
the game deletes whatever reaches it. See `BeltEndConnector.kt` for the
geometry and `## Traps` below for the wire and pole traps.

## Tests

The layout tests are grouped by scenario (`BlueprintLayoutTest`,
`BlueprintImportTest`, `BlueprintFluidLayoutTest`, `BlueprintBandTest`,
`BlueprintBandFluidTest`, `BlueprintBeltEndTest`); `LaneLoadTest`, `BeltRowsTest`,
`BeltRowLimitTest` and the `aSixInputRowMixesThreeInputPairs` /
`producedInputsPairOnBothFeedSides` cases pin the mixed-belt budget and merge;
`BlueprintLayoutSnapshotTest` hashes the canonical entity/wire list of
representative plans, including `wideLayoutIsStable`'s six-input row, so any
geometry or entity-order change is deliberate. The JVM integration suite is split by plan, layout, power, fluid,
belt end and serialization. `./gradlew check` runs Detekt (see `conventions.md`):
the type-resolved `detektMainJvm`/`detektTestJvm` plus the light
`detektJsMainSourceSet`/`detektJsTestSourceSet`; there are no committed
baselines, so every finding fails.

## Traps

- **A belt tile faces the tile it turns into, so a route must not judge its arrival
  direction.** `BeltRouteRules` used to reject a tile that would side-load a foreign
  belt in the direction it arrived from; at a corner that is not the belt's facing, so
  the reported logistic-science circuit feed jogged two tiles (and turned twice) at the
  row edge before its feed, because a belt of the next row sat under the arrival
  direction. A route only uses free tiles and its belt faces the next route tile, so
  that side-load check never fires correctly; it is gone (keep
  `aCornerMayTurnOverAForeignBeltItDoesNotFeed`). The reserved-feed approach check in
  the same class still reads the arrival direction: the advanced-circuit layout needs
  it to tunnel its circuit feed, so do not "fix" it without re-pinning that layout.
  See `BeltRouteRules`.
- **`LANE_MARGIN` offsets the blueprint, it does not widen it.** The machine rows start at column
  `LANE_MARGIN`, but `ImportTrimmer` moves every connector to the furthest west column that still has
  a straight east feed and deletes the prefix, so the finished layout begins at the connector:
  `advanced-circuit` is 39x22 and `utility-science-pack` 43x105 both with the margin at 40 (the old
  value) and at 10. Only the x coordinates move, by `LANE_MARGIN - 40`. The constant must hold the
  widest connector (five tiles for a chest), the merge column at `LANE_MARGIN - EAST_MERGE_OFFSET`
  and that merge's approach tile, which puts the floor at eight. Tests that pin absolute tiles read
  the `internal` constant, not a literal.
- **A route must not need the wide search box.** `RouteSearch` searches north, south and east only
  `ROUTE_MARGIN` (3) tiles past everything placed — one ring lane for the route and one for its turn.
  `LayoutRouter.routeWide` retries with `WIDE_ROUTE_MARGIN` (24) and still appends
  `route … needed the wide search box` to `failures`, so a layout that depends on the room fails every
  suite that asserts `failures` empty. Recognize it by that message; fix it by re-routing, not by
  widening the compact box without re-pinning the layouts. See `WIDE_ROUTE_MARGIN`
  (`RouteSearch.kt`).
- **The underground stretcher must not swallow a belt an inserter takes from or drops
  onto.** Only fluid ports were protected, so a run could grow under the last lane
  tiles of a row and leave an inserter facing nothing; `inserterTiles` now protects
  the one- or two-tile pickup/drop positions. Evidence: `utility-science-pack`'s
  low-density-structure row after the electric-engine band split
  (`UndergroundStretcherTest`, `realBlueprintsAreValid`).
- **Compaction can orphan a tunnel entrance by removing its feeding turn.** A
  horizontal corridor removes a plain belt in one column but not the next (the
  tunnel end is a wall), so the entrance slides onto the row of a belt that used to
  feed it sideways; the layout still has zero feed failures and the old "back tile
  is occupied" check passes. Recognize it by a tunnel input whose back tile is
  empty, a foreign belt, or a belt not facing into the run — and by a route that was
  valid before compaction. Evidence: the reported uranium-rounds URL had
  `(38,42) EAST -> (39,42) SOUTH -> (39,43) INPUT SOUTH` before compaction and
  `(38,17) EAST | (39,17) INPUT SOUTH` after it. `validateLayout` now rejects it. A
  post-stretch failure must also rule out that seam and continue, not abort the pass:
  the `0.3` uranium variant kept a 13-tile straight piercing belt until the retry was
  added. See `LayoutValidationUnderground.kt`.
- **A corner belt is removable when the belt behind it feeds it straight into a
  perpendicular same-material lane.** The splice rule only accepted a belt with the
  same direction and material on both sides, so the last belt of a feeder before its
  turn blocked the corridor that would have pulled its lane up a row. Dropping the
  corner and shifting the lane up leaves the feeder side-loading the lane, which
  `validateLayout` accepts as a same-material turn. The perpendicular lane is never
  itself in the corridor (it is not removable), so the corner is always at the
  corridor's far or near edge and the shift lands the lane next to the feeder.
  Evidence: `aCornerBeltIsDroppedAndItsFeederSideLoadsTheLane`,
  `theReportedLogisticScienceUrlCompacts`.
- **A grown underground run must leave a belt of its own material beyond the tile it swallows.**
  `UndergroundStretcher.RunGrowth` grows a run by swallowing plain same-material belts, so a moved
  entrance is fed by the belt past the swallowed one and a moved exit pours into the lane past it.
  Checking only that *something* stands there is not enough: the reported rocket-silo layout let a
  copper-cable run swallow the corner belt of its split branch, and the entrance slid onto a tile
  whose east neighbor was a concrete belt, so `validateLayout` reported the entrance as unfed and
  compaction stayed off. Require a same-material full belt facing into the run (`continues`); a
  foreign belt, or no belt at all, stops the growth. Evidence:
  `aRunDoesNotMoveItsEntranceOntoAForeignFeed`,
  `aRunDoesNotGrowItsExitIntoAForeignLane`, `theReportedRocketSiloUrlCompacts`.
- **A buried entrance does not feed the tile it faces.** `validateMaterials`
  treated every conveyor as feeding the tile ahead, so an underground belt input
  looked like it side-loaded a belt standing above its tunnel span. In the reported
  fusion-reactor URL the entrance at `(67,82)` "side-loaded" the plastic belt at
  `(68,82)`; the game keeps them independent. Do not re-add the entrance to
  `checkAhead`, and skip it in the side-load neighbor loop: it dives instead of
  feeding the tile it faces. The router needs the same rule:
  `BeltRouteRules.sideLoaded` casts any `ConveyorEntity`, which includes
  `UndergroundBeltEntity` (`BeltLikeEntity`), so a buried LDS entrance below the
  product connector's feed tile refused the whole product route
  (`utility-science-pack to … (no route)`) although the tile was free. An
  underground **output** does surface and pour onto the next tile, so only an
  input end is exempt. Evidence: `aBuriedEntranceDoesNotFeedTheBeltAboveItsRun`,
  `aBuriedEntranceDoesNotBlockARouteOverItsRun`,
  `theReportedUtilityScienceUrlKeepsTheProductRowClear`,
  `theReportedFusionReactorUrlCompacts`.
- **A splitter only takes items on its back, and a belt that pours into a route
  tile closes a circle.** The reported fusion-reactor layout inserted a circuit
  splitter on the tile where the trunk turned, so the trunk fed its side and the
  branch started with no input; its plastic trunk also descended one tile beside
  the LDS lane's dead end, absorbed that lane, and the lane ran back around the
  splitter into the trunk. `SplitDeliveryRouter` now needs a straight same-material
  feeder behind a splitter candidate (`fedStraight`), and
  `BeltRouteRules.sideLoaded` rejects any belt pouring into a route tile, not only
  a foreign one. `validateBeltFlow` (`LayoutValidationFlow.kt`) reports `splitter
  at … has no input` and `<material> belt loops back at …`; the circle report
  names one tile per circle and ignores a belt merely downstream of it. Evidence:
  `aSplitterWithoutABeltOnItsBackIsReported`,
  `aBeltCircleIsReportedOnceAtItsFirstTile`,
  `theReportedFusionReactorUrlCompacts`. See `LayoutValidationFlow.kt`.
- **Compaction is all-or-nothing on the whole layout.** `CompactionPass` accepts a
  corridor only when `validateLayout(candidate).isEmpty()`; a single pre-existing
  error anywhere rejects every candidate and the pass returns after its first
  iteration, leaving the layout uncompacted. The two reported URLs had exactly that:
  the fusion plan's false entrance side-load and the utility plan's dangling pipe
  left them at 78x184 and 72x131. `RowLayout` now appends `validateLayout` errors to
  the layout's failures, so a silently disabled compaction is visible instead of
  looking like "the layout is just big". Fix the pre-existing error; do not relax
  the gate to an error-count or error-kind comparison. Evidence:
  `theReportedFusionReactorUrlCompacts`, `theReportedUtilityScienceUrlCompacts`.
- **A belt route must stay a simple path; a revisit loses the belt already placed there.** A
  pipe route landed at `(55,124)`, was forced one surface tile west, then reversed onto the
  landing to dive east; a belt route looped four tiles around a 2×2 block to get from a westward
  tunnel landing back into a southbound lane. `PathPlacer.roles` keys one entity per tile, so the
  second use overwrote the first run's belt, leaving either an entrance without a feeder or an
  exit that empties onto the next entrance. `RouteSearch` refuses the direct reverse onto the
  landing, `PathPlacer.pathConflicts` rejects any belt path that uses a tile twice (nothing is
  placed), and `DeliveryRouter` re-searches with those tiles blocked. Pipes are exempt: a pipe tile
  is a junction. Evidence: `aPipeRouteDoesNotDoubleBackOntoItsLanding`,
  `aBeltPathThatRevisitsATileIsRejected`, `theReportedRocketSiloUrlCompacts`. See
  `PathPlacer.pathConflicts`.
- **A feed's approach tile is part of the feed.** A sink reserves its feed tile, but its belt
  takes its items from the tile behind (`feed - heading.step`); when a produced trunk already
  ran south on that tile, the copper-cable lane could never be fed (`copper-cable to … (no
  route)`). `DeliveryPreparation` reserves the approach tile for the feed's material,
  `BeltRouteRules` admits it for that material only and refuses a foreign belt that would pour
  into it. Evidence: `aForeignRouteMayNotTakeTheApproachTileOfAFeed`,
  `theReportedRocketSiloUrlCompacts`. See `BeltRouteRules`.
- **An underground run's far end must be matched by material, not by position.**
  Two runs can tunnel in the same column, so a test that looks for "the next
  underground belt in this direction" finds a *foreign* one and reports a phantom
  violation. `realBlueprintsAreValid` did exactly that for
  `advanced-circuit` ("input (38,22) can still grow back") after the band reshaped
  the layout; the layout's own `undergroundOutputOf` compares the material, and the
  test now does too. Match material whenever you search along a run.
- **`poleWestBound` keeps poles out of the import extension.** The pole pass
  (`bestPoleSpot`, `freeNear`) places a pole only at `x >= poleWestBound`, which
  `importSource`, `ImportTrimmer.trim()` and the compaction shift raise to the import
  connector's east edge. A chest marker's inserters sit *inside* that zone, so the
  bound must be skipped when `beltEnd.isChest` at **every** site
  (`DeliveryPreparation`, `ImportTrimmer`, `CompactionPass`) or
  `assertPolesCoverAndConnect` fails for the connector inserters.
- **A chest needs ground; a space platform allows only the label.**
  `BeltEnd.allowedOn(Surface.SPACE)` is false for every chest. The UI disables those
  radio options and both entry points (`BeltEnd.fromSelection`, the CLI) clamp a
  chest code to `constant-combinator`. See `BeltEnd.allowedOn`.
- **A blueprint does not rebuild circuit wires — store them in `wires`.** The
  top-level `blueprint.wires` array lists `[entity, connector, entity, connector]`
  quadruples; the game draws nothing from proximity. A chest-connector inserter
  needs it: its `circuit_condition` (e.g. `electronic-circuit < 200`) reads the
  chest's contents, which only works with a wire from the inserter to the chest
  (connector `1` on both ends). `blueprint/printing/output/ChestWires.kt` derives them from
  the final placed entities and `Blueprint.toJson()` emits the array; pole copper
  wires are the same idea, stored per pole in `neighbours`. Verified by generating
  and decoding a `buffer-chest` layout: `wires: [[29,1,31,1],[30,1,31,1]]`.
