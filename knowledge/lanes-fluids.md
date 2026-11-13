# Belt lanes and fluid lanes

How a row's belts and pipes are shaped: the lane frame, mixed rows, import
trimming, fluid ports, lane shape and the feeders. Load this file when you change
`RowBeltPlacement`, `RowPlacer`, `RowFluidLane`, `FluidLanePlacer` or
`FluidFeeder`; the pipeline and the routing machinery are in `layout.md`.

## Map

- `blueprint/planning/row/RowBeltPlacement.kt` — assigns every `BeltGroup` to the fixed belt rows:
  a product faces its consumer, an input faces its producer, each side takes at most two rows, and
  an overflowing input shares a capacity-safe mixed belt on its preferred side before moving across.
  A row without a producer/consumer row keeps the legacy rate-ranked layout.
- `blueprint/planning/row/RowPlacer.kt` — places the machine rows (`Row` = `PlannedRow` +
  `Area` + `RowLanes` + machines, declared in `Row.kt`) and the bands, and owns
  the ordered `placedRows` list and the lane direction. It consumes `planner.units` (already
  north to south) and `assignRowBelts`; lane direction sorts by `planner.outletX`, so a producer
  placed after its consumer still has a defined outlet. `RowLanes` holds at most
  `MAX_ROW_BELTS` fixed belt locations as `RowBelt` values (y, `LaneLoad`, direction,
  io and side), including the product; a mixed input belt also carries its `BeltMerge`
  (merge column, above/below, the two feeder materials and the lane direction).
  `LaneLoad` (in `layout/entity`) is the belt's two lanes in its own left/right
  frame: both lanes for a plain input, one material per lane for a mix, and only the
  inserter lane for a product. `beltFor` and `placeLanes` place the lane and its T
  merge; the merge sits outside the pair of lane rows (far north above, near north
  below, near south above, far south below), at `LANE_MARGIN - EAST_MERGE_OFFSET`
  (three columns west of the row, so its turn clears a fluid lane on the turn row)
  for an east-fed lane and `row.right + 8` for a west-fed one. `planning/plan/BeltRows.kt` holds the
  greedy packing (`packRowBelts`, `beltRowBudget`): every capacity-safe input pairs, lowest rate
  first, one material per lane. An import anchors its pair to east so it stays straight; two
  produced feeds from opposite directions stay separate. An over-capacity input only pairs for a
  single-recipe row that would otherwise drop a belt row (or its product), and then accepts the
  smaller lane rate. A `PlacedMachine`
  carries its own `production`; `producerRow(material)` scans `placedRows` for the row whose
  `plannedRow` equals `BlueprintPlan.producerOf(material)`.
  `MachineInserterPlacer` places the machine-edge inserters and
  `BandInterfacePlacer` the gap inserters.
- `blueprint/planning/row/FluidLane.kt` — the board-free lane shape: a plain junction pipe above
  every port, a horizontal pipe-to-ground run across every gap (a pair of adjacent
  ends in the two-tile gap between touching machines) and one vertical shaft per
  port, whose ends sit one row inside the lane and on the port tile.
  `blueprint/planning/row/RowFluidLane.kt` assigns the recipe's fluids to the machine ports in
  `prototypeFluidPorts` order — inputs before outputs, each in recipe order — and
  decides which side that puts a lane on, the lane row (two rows beyond the row's
  outermost belt; a mixed belt's merge and turn are not part of the outline, the
  merge offset keeps them west of the lane head) and its depth: a side places one
  lane per fluid, each two rows further out than the last.
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
  under already stand.
- `blueprint/placing/routing/ImportTrimmer.kt` — moves every import marker to one common column and
  removes the straight belt prefix in front of it (`lastStraightEast`). Only a
  straight run decides the column; a mixed feed that turns before its merge stays
  inline instead of dragging every marker west. `connectorClear` walks that column
  west until the connector replaces only its own marker and straight feed belts, so
  a foreign entity or a tunnel span never ends up under a loader. A **fluid**
  lane marker moves into that same column; `blueprint/placing/routing/FluidFeedLinker.kt` joins its
  lane head with pipe-to-ground runs along the lane row (`FluidFeed` in
  `blueprint/planning/plan/ImportExtension.kt`). Without solid imports the column is the westmost
  inline fluid marker, so the stacked lane markers still share one column. The
  marker, link and head are pinned so compaction shifts them as one group. The
  link is not pre-checked for room: the lane row was laid before any route ran, so
  a link that would overlap a placed tile or touch a foreign fluid is placed,
  rejected by `validateLayout` and rolled back, keeping the inline marker.

## Traps

- **A lane load is in the belt's own left/right frame, not the compass.** `LaneLoad.left` is
  north for an eastbound belt, south for a westbound one; `laneAt(direction, side)` is the one
  place that maps a neighbor side to a lane. A turn keeps the slots, so the merge tile's load is
  also the lane's load. A half product belt fills only the lane farther from its inserters
  (above the machines: the left lane; below them: the right lane); the other side is `null`.
- **The T merge must use the lane mapping, not just the material set.** Above the lane the merge
  tile faces south: the west feeder fills the right lane and the east feeder the left. Below the
  lane it faces north: west fills left, east fills right. The merge sits outside the pair of lane
  rows (far north above, near north below, near south above, far south below), at
  `row.left - 2` for east-fed mixes and `row.right + 8` for west-fed ones. Getting the side wrong
  looks valid to a material-only check but side-loads the wrong lane; `validateLayout` uses
  `LaneLoad.laneAt` and should report a foreign side-load.
- **A mixed feed that turns before its merge must not set the import column.** `ImportTrimmer`
  returns `null` from `lastStraightEast` for it, and only the straight feeds choose the common
  column. Treating the old `start.x - 1` fallback as a real end drags every marker west and the
  layout looks untrimmed.
- **A trimmed import connector may only replace its own marker and its straight feed belts.**
  `ImportTrimmer.connectorClear` walks the common column west until every connector tile is free,
  a feed belt of that material, or one of the marker's own tiles; a foreign entity or a tunnel
  span keeps the column further west, and an unsafe column falls back to the inline marker.
  Without the check a marker moved onto a tunnel span leaves a loader above the run (the reported
  utility-science URL's copper-cable tunnel under the coal import). Evidence:
  `theReportedUtilityScienceUrlKeepsTheProductRowClear`, `realCheatBlueprintsAreValid`
  (`assertNoLoaderOverTunnel`).
- **The target product row is a routing wall from the placed output belt east, but the
  connector itself must not exist while the other feeds route.** `reserveTargetOutput` only
  calls `LayoutBoard.reserveRow`, so a produced feed may still go around the row's west side
  but must tunnel to cross it. The connector is placed after compaction at `board.maxX + 1`,
  then the product belt is routed last (with the half-lane load) and may tunnel under a
  crossing belt. Placing the connector before routing widens the route-search area and pushes
  a feed like the utility-science LDS belt east of the marker; leaving a belt east of the
  finished connector is reported in `failures`. Evidence:
  `theReportedUtilityScienceUrlKeepsTheProductRowClear`.
- **A half output belt may feed a full trunk, but not enter a tunnel or a route side.** Compare
  `materials` for a straight continuation and for a seam splice; use exact full loads for
  `UndergroundStretcher.swallowable`, `BeltRouteRules` and the tunnel-end checks. A mixed belt is
  never swallowed, never spliced into a tunnel and never side-loaded by a route. Evidence:
  `aRunLeavesAHalfBeltLane`.
- **The budget is rows, not materials.** `packRowBelts` pairs only as many inputs as are needed,
  prefers the ones whose rate fits a lane, and never pairs a fluid or an output; `bandBeltFits(plan)`
  must read the arranged machine rates, not `bandBeltMaterials(...).size`. A row that still needs
  more disjoint pairs than its inputs can form reports a failure; silently dropping a belt leaves a
  machine unfed. Evidence: `aSixInputRowMixesThreeInputPairs`,
  `producedInputsPairOnBothFeedSides`, `anOverCapacityInputPairsBeforeTheRowLosesABelt`,
  `aRowThatCannotFitReportsTheMissingMaterials`, `wideLayoutIsStable`. See
  `packRowBelts` (`BeltRows.kt`).
- **A machine's port table is written in the placed orientation, in recipe-assignment order.**
  The sanitized dump has no `pipe_connections`/`fluid_boxes`; `prototypeFluidPorts` is curated
  from the Wube prototypes and already lists the tiles after `blueprintMachineDirection`'s turn,
  so callers use them as-is. Its order is load-bearing: inputs first in the recipe's fluid order,
  then outputs. Do not re-rotate or re-sort the table, and do not infer an input from
  "north/west": the cryogenic plant, foundry and oil refinery take on the north edge and give on
  the south edge (180° placement), and the electromagnetic plant's input boxes face north/south
  (90° clockwise). A pipe on the wrong box shows up as a fluid that never arrives while the
  machine's other fluid works. Evidence: the four reported URLs in
  `BlueprintFluidPortLayoutTest`. See `decisions.md` (2026-10-27).
- **Never merge a row's fluid lanes by material alone.** A fluid can be both an
  input and an output of the same machine (vanilla `coal-liquefaction` takes and
  gives heavy oil), and the input and output ports usually sit on opposite edges.
  The old `RowFluidLane.fluids` took its candidates from `row.machines.first()`
  and returned a material once per occurrence in `inputMaterials +
  outputMaterials`, while `assignedPort` preferred the input port. A both-way
  fluid therefore got two lanes over the *same* input port (the second shaft
  could not fit and was silently skipped) and its output port was never drained;
  a band row got no lane at all (the `interfaces` guard returned early), and a
  row whose first machine takes no fluid missed a later machine's fluid. Keep one
  lane per `(material, flow)` and let `FluidFeeder.connectLanes` join the drain
  to the feed on the row's east side. Evidence:
  `aFluidThatIsBothInputAndOutputJoinsItsTwoLanes` in
  `BlueprintFluidLaneLinkTest`, and `heavy-oil --planet=vulcanus` in the CLI.
- **A pipe-to-ground's blueprint `direction` is the side its surface pipe attaches
  on, not the run's travel direction.** `toBlueprintEntity`
  (`blueprint/printing/output/LayoutSerialization.kt`) flips **only the input end**
  (`Io.INPUT`) and leaves the output end as stored, so the internal convention is
  "store both ends with the run's travel direction". A shaft that dives from a lane
  above a machine therefore stores its **lane-side end as `INPUT`** and its
  port-side end as `OUTPUT`, both with `Direction.SOUTH`; the writer then emits N at
  the lane end and S at the port. Assigning the io the other way round (output at
  the lane, input at the port) serializes **both ends 180° off**, which the user saw
  in game as "tunnel ends are N/S but both turned around". Check the direction in
  the encoded JSON (`--json`: `0` N, `4` E, `8` S, `12` W), not only the internal
  `Io`.
- **There is no free row between the input belts and the machines.** An AM3 row has
  the inserter row on `factoryTop - 1` and its input belts on `factoryTop - 2` and
  `factoryTop - 3`, so a fluid lane placed there would displace a belt — the user
  rejected exactly that twice. The lane belongs **two rows beyond the outermost
  belt** (`min belt - 2`, `max belt + 2`), with the tunnel's lane-side end in the
  single row between (`- 1`/`+ 1`). The lane row has a **junction pipe above every
  port**, joined across each gap by a horizontal pipe-to-ground run (a pair of
  adjacent ends in the two-tile gap between touching machines). Do not replace
  those runs with surface pipes: the surface line walls the row off, while the run
  is what the player walks across. A lane derived from the near belt instead of the
  outermost belt lands **on the far belt row**, `board.isFree` fails on the belt
  tiles and the lane silently falls back to the route (evidence: `lubricant` lane
  planned at `y=95` over belts on `y=95/97`, then `no pipe route`).
- **A fluid lane may use a mixed belt's turn row, but must clear the merge columns.**
  A mixed lane's merge sits one row outside the belt and its turn tile two rows
  outside, at the row's west/east edge; the lane row is read from the belts alone
  (`RowFluidLane.laneRow`), so the lane can land on the turn row. An east-fed merge
  now starts at `LANE_MARGIN - EAST_MERGE_OFFSET`, which puts the turn and approach
  tiles west of the machine column where the lane head starts. The reported
  utility-science battery row's acid lane was dropped when the lane row ran
  through the gear belt's merge and its marker block collided with the merge.
  Do not shrink `EAST_MERGE_OFFSET` without re-checking the lane head: the lane
  must stay east of `merge.turnColumn`. A three-tile marker block reaches further
  west than the lane head, so a plan using the constant-combinator fluid source can
  still fall back to the routed feed on such a row. Evidence:
  `aMixedBeltTurnRowKeepsTheLaneHeadClear`,
  `theReportedUtilityScienceUrlCompacts`.
- **A burner furnace's fuel belt fills the inserter row and leaves the small pole no tile.** A
  stone furnace row with coal and ore on two belt rows packs both machine columns with inserters
  (a short one for the near belt, a long one for the far belt). A small pole then has no free tile
  in its 5x5 supply area, so the reported copper-plate URL shipped 14 unpowered input inserters.
  Merging fuel and ore onto one belt (one material per lane) leaves a single short inserter and a
  free column for the pole. `packRowBelts` now pairs every capacity-safe input, and an import
  anchors its pair east so it stays straight. Evidence:
  `theReportedCopperPlateUrlPowersEveryInserter`,
  `anEarlyStoneFurnaceSharesTheOreBeltWithItsFuel`.
- **Two produced feeds from opposite directions must not share a belt.** An import anchors a pair
  to east and stays straight, so it may pair across directions; two produced feeds may not,
  because one would have to cross the row to reach a merge at the other end. A cross-direction
  produced pair moved a utility-science feed and its turn onto the acid lane's row, leaving
  `sulfuric-acid … (no pipe route)`; `packRowBelts` now leaves those two on separate belts.
  Evidence: `realBlueprintsAreValid` for `utility-science-pack`.
- **A wide band forces belt crossings, and a multi-link band longer than nine machines can leave
  inserters outside pole coverage.** `GROUP_MAX_MACHINES = 10`
  (`blueprint/planning/band/DirectFeedModel.kt`) is a pair's ceiling; a band with more than one link
  stays at `MULTI_LINK_MAX_MACHINES = 9` so its row remains powerable. Rows above a band then have
  to reach past it, and a belt that crosses
  another belt can only tunnel. Evidence: the reported `advanced-circuit` URL's
  copper-cable feed tunnels where its cable row joins a band
  (`advancedCircuitUrlRoutesEveryFeedOnTheSurface` locks that in). Since plastic is
  planned, its extra row also forces the electronic-circuit feed to tunnel; on a plan
  without that row the lane tail beside the machines blocks the straight column and
  the router turns east one column and comes back on the surface. Line breaking a band
  too wide is a deferred follow-up, not a bug in the router. See
  `LayoutRouter.beltProfile` for the route costs.
- **One imported fluid can span more than one pipe network.** `FluidLaneFeed` lays a lane
  per row and each lane gets its own inline source, so a plan that feeds the same fluid to
  two distant rows (the `utility-science-pack` plan's battery row and its processing-unit
  band both take sulfuric acid) has two disconnected networks. Both are fed; do not assert
  that one fluid is one network. `fluidNetworkProblems` instead requires every network to
  reach a source. Evidence: `realBlueprintsHaveValidPlumbingAndConnectedNetworks`
  (`utility-science-pack`).
- **A row has four solid belt locations, so a fifth solid lane only fits as a mixed belt.**
  `RowPlacer.rowShape` ranks inputs and outputs together and zips them onto two top
  and two bottom locations (near/far each), at most `MAX_ROW_BELTS`; every input
  belt is end-fed by `DeliveryPreparation.sinkOf`, so the old below-row
  side-load into the row's product columns is gone. A row that needs more pairs
  than its inputs can form is reported (`row needs N solid belt rows`), and a
  missing target product belt is reported too (`<target> has no output belt on its
  row`); a plain recipe such as `flying-robot-frame` (four solid inputs plus a
  product) fits through one mixed belt. Recognize a genuine routing failure by
  `foreign side-load into …` while the row still has a free input lane, not by
  compaction rejecting every corridor. See `packRowBelts` (`BeltRows.kt`).
- **A fluid marker joins the solid import column unless the source block or a run
  end is taken.** The link (`blueprint/placing/routing/FluidFeedLinker.kt`) buries under a belt,
  underground belt or inserter that crosses the lane row in the west corridor, so a
  solid route crossing the lane no longer keeps the marker out of the column (the
  `--cheats` electric-engine case: the circuit belt at the lane row is passed
  under). A machine or another non-buryable entity at the source tile or a run end,
  or a reserved tile, keeps the inline marker: the fluid is still correct, only the
  shared column is missed.
- **A reserved fluid port must not cost an input inserter.**
  `MachineInserterPlacer` spreads the short/long pattern over the free columns and
  falls back to the machine's last column (normally kept for a pole) when the port
  takes one. The old bug laid the pattern over `0 until width - 1` minus the top
  port columns, then zipped it; an AM3 has exactly three edge columns and the port
  took the middle one, so the long inserter to the far belt fell off the end. The
  symptom is a fluid row whose far input belt is never unloaded although its near
  belt is; the ASCII shows one `^` per machine instead of two, and the blueprint
  JSON has a `fast-inserter` but no `long-handed-inserter` on that row.
- **A long-handed inserter reaches two tiles in both directions.** It picks up
  *and places* items two tiles from its own tile, not one tile behind like a short
  inserter ([Factorio wiki](https://wiki.factorio.com/Long-handed_inserter)).
  `LayoutValidationInserter.checkDrop` therefore uses the same `reach` as
  `checkSource`, and an output belt on the far row (`factoryBottom + 3`) is valid.
  Do not "fix" a far output to the near row or validate its drop at
  `tile - direction.step`.
- **`InserterTier.entries` is not the picker.** The burner and long-handed
  inserters are kept for the dump and the row layout's far-lane inserter but are
  `selectable = false`, so `LogisticsConfig.availableInserters` is the only list the
  UI, the URL decoder and `--inserter=` may use. Their codes still decode and then
  clamp to the default tier.
