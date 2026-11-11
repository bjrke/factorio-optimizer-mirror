# Pitfalls

Verified traps, non-obvious behavior and diagnostic dead ends, grouped by area.
An entry belongs here only with evidence, and should say how to recognize it.

## Belt lanes and mixed rows

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
- **A half output belt may feed a full trunk, but not enter a tunnel or a route side.** Compare
  `materials` for a straight continuation and for a seam splice; use exact full loads for
  `UndergroundStretcher.swallowable`, `BeltRouteRules` and the tunnel-end checks. A mixed belt is
  never swallowed, never spliced into a tunnel and never side-loaded by a route. Evidence:
  `aRunLeavesAHalfBeltLane`.
- **The budget is rows, not materials.** `packRowBelts` pairs only as many inputs as are needed
  and never a material above one lane, a fluid or an output; `bandBeltFits(plan)` must read the
  arranged machine rates, not `bandBeltMaterials(...).size`. A row that still needs more than
  `MAX_ROW_BELTS` rows has to report a failure; silently dropping the fifth material leaves a
  machine unfed. Evidence: `aSixInputRowMixesThreeInputPairs`,
  `producedInputsPairOnBothFeedSides`, `aRowThatCannotFitReportsTheMissingMaterials`,
  `wideLayoutIsStable`.

## Layout and fluids

- **A machine's port table is written in the placed orientation, in recipe-assignment order.**
  The sanitized dump has no `pipe_connections`/`fluid_boxes`; `prototypeFluidPorts` is curated
  from the Wube prototypes and already lists the tiles after `blueprintMachineDirection`'s turn,
  so callers use them as-is. Its order is load-bearing: inputs first in the recipe's fluid order,
  then outputs. Do not re-rotate or re-sort the table, and do not infer an input from
  "north/west": the cryogenic plant, foundry and oil refinery take on the north edge and give on
  the south edge (180° placement), and the electromagnetic plant's input boxes face north/south
  (90° clockwise). A pipe on the wrong box shows up as a fluid that never arrives while the
  machine's other fluid works. Evidence: the four reported URLs in
  `BlueprintFluidPortLayoutTest`.
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
- **A mixed input belt's merge and turn tiles are part of the row outline.** A
  mixed lane's merge sits one row outside the belt and its turn tile two rows
  outside, at the row's west/east edge. `RowFluidLane.laneRow` chose the lane row
  from the belts alone, so the acid lane of the reported utility-science battery
  row landed on the merge row; its marker block west of the lane head collided
  with the gear belt's merge, `sourceEnd` found no room, the lane was dropped and
  the batteries got no acid. Include every mixed belt's `mergeRow`/`turnRow` in
  the outline before applying the two-row gap. Evidence:
  `aMixedBeltMergePushesTheLaneBeyondItsTurnTile`,
  `theReportedUtilityScienceUrlCompacts`.
- **A wide band forces belt crossings, and a multi-link band longer than nine machines can leave
  inserters outside pole coverage.** `GROUP_MAX_MACHINES = 10`
  (`blueprint/planning/band/DirectFeedModel.kt`) is a pair's ceiling; a band with more than one link
  stays at `MULTI_LINK_MAX_MACHINES = 9` so its row remains powerable. Rows above a band then have
  to reach past it, and a belt that crosses
  another belt can only tunnel. Evidence: the reported `advanced-circuit` URL's
  copper-cable feed tunnels where its cable row joins a band
  (`advancedCircuitUrlOnlyTunnelsForItsBandedFeeds` locks that in). Since plastic is
  planned, its extra row also forces the electronic-circuit feed to tunnel; on a plan
  without that row the lane tail beside the machines blocks the straight column and
  the router turns east one column and comes back on the surface.
  Two things make that work: the tile beside the goal must be judged by the direction
  **into** the goal, not by the direction it arrived from (a corner belt faces its next
  tile), and the belt tunnel entry costs 16 (four surface tiles), so a short detour wins
  over an unnecessary tunnel. Line breaking a band too wide is a deferred follow-up, not
  a bug in the router.
- **Recipe cycles are excluded by the optimizer, not by `buildable`.**
  `game/prototype/RecipePolicy.kt` lists `fish-breeding`, `nutrients-from-fish` and
  `kovarex-enrichment-process` in `unplannedRecipes` and discards them even when one produces the
  target, so a plan can never contain a cycle. `buildable` deliberately does not re-check the set;
  uranium-235 is built by `uranium-processing`, and centrifuging and the simple chemistry recipes
  (plastic, sulfur, battery, ...) are otherwise planned. `FeedNode.traverse()` keeps a
  visited-node set so a shared producer is expanded once; it does **not** filter the
  closing edge of a cycle (the edge is yielded before the visited check), so an unforeseen
  cycle would still reach `maximalGroups`.
  Evidence: `bannedRecipesAreNeverPlanned`, `nutrientsAreNotBredFromFish` and
  `uranium235IsBuiltByProcessingNotKovarex`. Do not
  reintroduce path-local cycle state; a future cycle needs a name in `unplannedRecipes`.
- **One imported fluid can span more than one pipe network.** `FluidLaneFeed` lays a lane
  per row and each lane gets its own inline source, so a plan that feeds the same fluid to
  two distant rows (the `utility-science-pack` plan's battery row and its processing-unit
  band both take sulfuric acid) has two disconnected networks. Both are fed; do not assert
  that one fluid is one network. `fluidNetworkProblems` instead requires every network to
  reach a source. Evidence: `realFluidNetworksAreConnected` (`utility-science-pack`).
- **A row has four solid belt locations, so a recipe needing a fifth solid lane is
  out of scope.** `RowPlacer.rowShape` ranks inputs and outputs together and zips
  them onto two top and two bottom locations (near/far each), at most
  `MAX_ROW_BELTS`; every input belt is end-fed by `DeliveryPreparation.sinkOf`, so
  the old below-row
  side-load into the row's product columns is gone. A **band** that would need more
  is now filtered out (`bandBeltMaterials`, `bandBeltFits`), because its product or
  an input would silently lose its lane; a plain recipe such as `flying-robot-frame`
  (four solid inputs plus a product) still needs a **mixed belt** in a
  single-material layout, which is out of scope and not a router bug. Recognize a
  genuine routing failure by `foreign side-load into …` while the row still has a
  free input lane, not by compaction rejecting every corridor.
- **The underground stretcher must not swallow a belt an inserter takes from or drops
  onto.** Only fluid ports were protected, so a run could grow under the last lane
  tiles of a row and leave an inserter facing nothing; `inserterTiles` now protects
  the one- or two-tile pickup/drop positions. Evidence: `utility-science-pack`'s
  low-density-structure row after the electric-engine band split
  (`UndergroundStretcherTest`, `realBlueprintsHaveNoDanglingInserter`).
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
- **Compaction can orphan a tunnel entrance by removing its feeding turn.** A
  horizontal corridor removes a plain belt in one column but not the next (the
  tunnel end is a wall), so the entrance slides onto the row of a belt that used to
  feed it sideways; the layout still has zero feed failures and the old "back tile
  is occupied" check passes. Recognize it by a tunnel input whose back tile is
  empty, a foreign belt, or a belt not facing into the run — and by a route that was
  valid before compaction. Evidence: the reported uranium-rounds URL had
  `(38,42) EAST -> (39,42) SOUTH -> (39,43) INPUT SOUTH` before compaction and
  `(38,17) EAST | (39,17) INPUT SOUTH` after it. `validateLayout` now rejects it;
  do not weaken that back to an occupancy check, and do not exempt a route end just
  because some import connector is adjacent (another import's loader can sit
  beside it). A post-stretch failure must also rule out that seam and continue, not
  abort the pass: the `0.3` uranium variant kept a 13-tile straight piercing belt
  until the retry was added.
- **A buried entrance does not feed the tile it faces.** `validateMaterials`
  treated every conveyor as feeding the tile ahead, so an underground belt input
  looked like it side-loaded a belt standing above its tunnel span. In the reported
  fusion-reactor URL the entrance at `(67,82)` "side-loaded" the plastic belt at
  `(68,82)`; the game keeps them independent. Do not re-add the entrance to
  `checkAhead`, and skip it in the side-load neighbor loop: it dives instead of
  feeding the tile it faces. Evidence: `aBuriedEntranceDoesNotFeedTheBeltAboveItsRun`,
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
  names one tile per circle and ignores a belt merely downstream of it. Do not
  treat a splitter as a belt tile a belt may turn into. Evidence:
  `aSplitterWithoutABeltOnItsBackIsReported`,
  `aBeltCircleIsReportedOnceAtItsFirstTile`,
  `theReportedFusionReactorUrlCompacts`.
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
- **A route must not double back onto its tunnel landing.** A pipe route landed at
  `(55,124)`, was forced one surface tile west to `(54,124)`, then reversed onto the
  landing to dive east; `PathPlacer.roles` keys one entity per tile, so the second
  run overwrote the first exit and left the entrance at `(62,124)` unpaired. The
  same tile cannot be both the exit of one run and the entrance of the next.
  `RouteSearch` now refuses a reverse back onto the tile it just surfaced from.
  Evidence: `aPipeRouteDoesNotDoubleBackOntoItsLanding`,
  `theReportedUtilityScienceUrlCompacts`.
- **An underground run's far end must be matched by material, not by position.**
  Two runs can tunnel in the same column, so a test that looks for "the next
  underground belt in this direction" finds a *foreign* one and reports a phantom
  violation. `realBlueprintsStretchUndergroundRuns` did exactly that for
  `advanced-circuit` ("input (38,22) can still grow back") after the band reshaped
  the layout; the layout's own `undergroundOutputOf` compares the material, and the
  test now does too. Match material (or `blueprintTransportMaterials`' map)
  whenever you search along a run.
- **`poleWestBound` keeps poles out of the import extension.** The pole pass
  (`bestPoleSpot`, `freeNear`) places a pole only at `x >= poleWestBound`, which
  `importSource`, `trimImportPrefixes` and the compaction shift raise to the import
  connector's east edge. A chest marker's inserters sit *inside* that zone, so the
  bound must be skipped when `beltEnd.isChest` at **every** site
  (`DeliveryPreparation`, `ImportTrimmer`, `CompactionPass`) or
  `assertPolesCoverAndConnect` fails for the connector inserters.
- **A chest needs ground; a space platform allows only the label.**
  `BeltEnd.allowedOn(Surface.SPACE)` is false for every chest. The UI disables those
  radio options and both entry points (`BeltEnd.fromSelection`, the CLI) clamp a
  chest code to `constant-combinator`; do not special-case the label again
  elsewhere.
- **A blueprint does not rebuild circuit wires — store them in `wires`.** The
  top-level `blueprint.wires` array lists `[entity, connector, entity, connector]`
  quadruples; the game draws nothing from proximity. A chest-connector inserter
  needs it: its `circuit_condition` (e.g. `electronic-circuit < 200`) reads the
  chest's contents, which only works with a wire from the inserter to the chest
  (connector `1` on both ends). `blueprint/printing/output/ChestWires.kt` derives them from
  the final placed entities and `Blueprint.toJson()` emits the array; pole copper
  wires are the same idea, stored per pole in `neighbours`. Verified by generating
  and decoding a `buffer-chest` layout: `wires: [[29,1,31,1],[30,1,31,1]]`.

## Blueprint planning

- **A shared product stays the band's output.** `electronic-circuit` feeds the
  advanced circuit, the processing unit and the flying robot frame. Internalizing
  it into one of them leaves the other two with a placed but unfed input belt.
  `FeedCandidates.internalizesSharedOutput` rejects that growth, and
  `FeedLink.candidateScore` penalizes outside consumers and producer overfeed;
  the belted pass must pass the main `consumerScope`. Test the guard on the group
  **without** the candidate: once the candidate is in the group its producer
  counts as internal and the guard always returns false, so the swap path must
  pass `group.filterNot { it == weakest }`. Evidence:
  `theReportedUtilityScienceUrlsKeepCircuitAsABandOutput`,
  `aSharedProductCannotBeInternalizedWhileAnotherConsumerNeedsIt`.
- **A band must not need a belt for a material it produces.**
  `BandSearch.hasInBandBeltInput` rejects a window where a solid the band makes
  is consumed by an in-band machine without an incoming link; otherwise the row
  needs an extra belt for a resource it already produces. Growth may pass
  through a partial arrangement (`arrange(..., requireComplete = false)`) so a
  later link can complete it; only the finished group is checked. `RowPlacer`
  keeps a partial input lane as a guard for hand-built plans. Evidence: the
  reported chemical-science URL keeps cable entirely out of the band;
  `theReportedChemicalScienceUrlKeepsCableOutOfTheBand`,
  `aCircuitChainWithSharedConsumersBuildsTheTargetOnce`.
- **A recipe result is not automatically a source.** `plannedSources` must read
  `beltMaterials` plus the **consuming machine's** link, not the row's whole link
  set: a link only covers the machine it reaches, so a partially linked material
  still imports the rate of the machines that take it from a belt. Share
  `PlannedRow.internalInputs`/`fullyInternalInputs` with `RowPlacer`; an
  internal-only result is not produced for another row. `DeliveryPreparation`
  reports a sink with no producer row so a missing feed cannot stay silent.
  Evidence: `aMaterialOnlyLinkedInsideABandIsASourceForAnotherRow`,
  `aPartialLinkStillImportsTheMaterialItsOtherMachinesNeed`.
- **`MAX_BAND_RECIPES` is a recipe cap, not the window length.** `arrangeBest`
  must search count vectors to `maxBandMachines(links.size)`; using
  `MAX_BAND_RECIPES` there silently caps a multi-link band at eight machines.
  The growth pre-filter must likewise use the propagated `groupNeeds`, not
  `ceil(production.amount * scale)`: the plan count includes consumers a band
  internalizes and can reject a feasible group. The reported utility-science
  four-recipe chain sums to ten plan machines but nine propagated ones.
  Evidence: `theGrowthBudgetUsesPropagatedNeedsInsteadOfThePlanMachineCounts`.
- **A belted row can be re-banded, but only as a copy.** `DirectFeed.beltedBands`
  copies the belted `FactoryProduction` with `amount = machines` and plans at
  scale 1; the copies must differ by `amount`, or `nodeCache` and the `assigned`
  set treat them as the main band's production. The belted band's internal
  material still stays off the belt; only its output is a source. Evidence:
  `theReportedUtilityScienceUrlsBandTheirBeltedCableAndCircuit`.
- **A production belongs to exactly one band.** The seed loop only picks an
  unassigned link, but the pool keeps links that touch a production another seed
  already assigned, and `growGroup` could add one. The same
  flying-robot-frame/utility-science production then sat in two bands: the first
  band internalized battery/electric-engine/engine/pipe, the second imported
  them, and the reported utility-science plan imported battery,
  electric-engine-unit, LDS and processing-unit while building them. Skip a
  growth candidate whose productions include an assigned one outside the group;
  the second consumer gets a belted row instead. Evidence:
  `assertNoPlannedMaterialIsImported` on `theReportedUtilityScienceUrlCompacts`,
  `theReportedUtilityScienceUrlsKeepCircuitAsABandOutput`.
- **The target production must be seeded into `DirectFeedContext.included`.** Deriving
  `included` only from the materials with a single buildable producer (`producers.values`)
  drops a target that is not in the per-material producer index, so the plan has no target row
  and `plannedSources` sees none of its consumers. Keep the seed separate from `buildable`:
  indexing the target in `producers` again can make a self-consuming target plan a second,
  belted row. `DirectFeed.belted` also skips the target production, so `targetRow` is the only
  owner of the target row even when another included recipe consumes it. Evidence:
  `theTargetStaysIncludedWhenItsMaterialHasTwoProducers`,
  `aTargetThatAnotherIncludedRecipeConsumesGetsOneRow`.
- **Belted machine counts must round with `MACHINE_COUNT_EPSILON`, not `FEED_EPSILON`.**
  `DirectFeed.belted` turns a plan rate into whole machines, so it needs the same `1e-6` slack as
  `plannedMachines`; the `1e-9` feed slack rounds a rate that is only floating-point noise above a
  whole number up to an extra machine. Evidence:
  `aBeltedProductionDoesNotRoundUpFloatingPointNoise`.
- **Stone and solid fuel must stay out of the multi-producer direct feed.** `FeedNode.ingredients`
  links every in-plan producer, so a solid with two producers is direct-fed from whichever link
  the walk sees first; `maximalGroups` drops the others. `DirectFeedContext.producers` excludes
  `stone` on every surface, and `Optimizer` plans one solid-fuel recipe per surface: light oil
  elsewhere, heavy oil on Fulgora and ammonia on Aquilo. Keep these policies explicit; do not infer
  them from recipe result order or reintroduce a single-producer link filter. Evidence:
  `vulcanusStoneIsImported`, `rocketFuelUsesTheLightOilSolidFuelRecipe`,
  `solidFuelUsesTheSurfaceRecipe` and `aquiloSolidFuelUsesAmmonia`.
- **A multi-output recipe drains only through its machine's horizontal fluid ports.**
  `buildable` accepts one solid result plus any number of fluid results, so `quantum-processor`
  (one item plus `fluoroketone-hot`) is built for targets such as `railgun`. The electromagnetic
  plant's only fluid outputs are on side edges, and `RowFluidLane` creates lanes only on
  north/south edges, so no drain is placed for the hot fluoroketone and the machines stall. This
  is a known limitation; the intended fix is to flip every second electromagnetic plant so
  adjacent output ports meet and act as a pipe, then drain the hot fluid.
## Build, Detekt and environment

- **`gradlew` tests run with the project root as the working directory.** Tests
  that shell out can resolve repository files relative to
  `System.getProperty("user.dir")`.
- **`commonTest` cannot walk the source tree.** A test that reads files runs only
  on JVM: put it in `jvmTest` and locate the root by walking up from
  `System.getProperty("user.dir")` (same as `IconCoverageTest`). Prefer a Gradle
  task over such a test for anything that guards the build itself.
- **`detektMainJvm`/`detektTestJvm` report "compiler errors found during analysis"
  although the code compiles.** Verified on 2026-10-24 at `395323f`, both with and
  without working-tree changes: both tasks print `There were 3 compiler errors
  found during analysis. This affects accuracy of reporting.` while `check` (and
  `compileKotlinJvm`) succeed, and no error names a file. detekt analyzes
  `commonMain` and `jvmMain` as one fragment, which is a known detekt limitation
  (issue #9602). Treat it as analysis-only noise unless the count changes or a
  finding looks wrong; prove a real compile failure with `./gradlew jvmTest`, not
  with this line.
- **A package move can crash `detektTestJvm` with a stale-analysis-index
  `FileNotFoundException`.** After moving `Blueprint` to `blueprint/printing/model`, the
  typed task failed resolving a class from
  `build/libs/factorio-optimizer-jvm-1.0.jar` even though the jar already contained
  only the new path; `--rerun-tasks` did not help, `./gradlew --stop` plus a rerun
  did. Recognize it by a `FileNotFoundException` for a jar entry that no longer
  exists — restart the daemon instead of editing code.
- **A declaration-level `@Suppress` may not silence a light-mode finding.**
  `MatchingDeclarationName` is reported on the file and the external-interface
  `UnusedParameter` on the interface/property; `@Suppress` on the class or property
  was ignored, while `@file:Suppress(...)` before the package declaration worked for
  all four React `XProps` files and `BlueprintDialogProps.text`. Re-run the report
  after adding a suppression instead of assuming it took.
- **An `internal` top-level declaration collides with a `private` one of the same
  name in the package.** Graduating a file-private constant to `internal` to share
  it broke `:compileKotlinJvm` with `REDECLARATION … EPSILON` against
  `Optimizer.kt`'s `private const val EPSILON` — a file-private declaration is not
  exempt from the clash. Rename (`FEED_EPSILON`) or keep it private and pass the
  value. Verified splitting `DirectFeed.kt` into `DirectFeedModel.kt`.
- **`LongParameterList` ignores data-class constructors.** Detekt's default
  `ignoreDataClasses: true` means a data class may carry many fields without a
  finding. Use that for a genuine value object (`FactorySpec`, `Selection`,
  `RowLanes`) where the fields belong together; do not convert an
  arbitrary argument tuple into a data class just to silence the rule.
- **A file with one top-level declaration must be named after it.**
  `MatchingDeclarationName` flagged `LayoutTunnels.kt` once `Tunnels` was its only
  type; renaming the file (not suppressing the rule) is the fix. Files with several
  top-level declarations are exempt.
- **An exhaustive `when` over every entity reaches Detekt's complexity limit.**
  The ascii glyph switch over every `LayoutEntity` kind reported
  `CyclomaticComplexMethod`; group first (`ChestLikeEntity`, `Plumbing`) and switch
  over the group in its own function — the dispatch stays exhaustive.
- **Phase one of `LinearProgram` is numerically sensitive.** The `d < -EPS` test on
  the most negative right-hand side is not interchangeable with `d >= 0.0`: the
  zero test pivots in tiny-negative rows, and the optimizer then reports
  "no producer for …"/empty plans while every small `LinearProgramTest` case still
  passes. Keep the `-EPS` form when touching `makeFeasible`, and check the optimizer
  suites (`OptimizerMachineTest`, the blueprint integration suites), not just the LP
  unit test.
- **detekt's `ForbiddenImport` is a flat, global list — it cannot express
  per-package edges.** On 2.0.0-alpha.6 `allowedImports` is one global list, and
  `forbiddenImports` is merged from every entry, so `pkg.**` forbids `pkg` for
  *every* file, not only for the section it was written under. A package can
  therefore be listed only when no other package imports it. `pkg.**` matches the
  members of `pkg` and of every nested package (so `blueprint.**` covers
  `blueprint.inspection.**`); a bare `pkg` matches nothing; `pkg.*` is a regex
  whose dots are wildcards. `DependencyRules` no longer ships in any 2.0.0-alpha.6
  rules jar; a custom rule needing real resolution must implement
  `dev.detekt.api.RequiresAnalysisApi`. `ForbiddenImport` stays empty on purpose:
  the intended edges are the table in `architecture.md`, guarded by the compiler
  (`internal`) and review (see `decisions.md`, 2026-10-22).
- **A cold or stale Gradle daemon can fail build-script compilation with
  `Unresolved reference 'de'`.** After the daemon was stopped, `./gradlew` reported
  the `de.bjrke.factorio.optimizer.buildlogic.*` imports in `build.gradle.kts` as
  unresolved even though `buildSrc/build/libs/buildSrc.jar` contained the classes.
  It is a stale script classpath, not a code error: retry, or rerun the underlying
  task with `--rerun-tasks`; clearing `.gradle/<version>` plus `buildSrc/build`
  rebuilds `buildSrc` and succeeds. Do not edit `build.gradle.kts` over it.
- **Never delete `buildSrc/build/kotlin` while a Kotlin daemon from an earlier run
  is still alive.** A Kotlin compile daemon outlives the Gradle daemon that started
  it (seen: one from 20:02 still writing at 21:13, hours after `--stop`). Removing
  the cache directory underneath it makes the *next* builds fail with
  `Could not close incremental caches … NoSuchFileException` or `Can't write logical
  size … caches-jvm/inputs` / `Failed to clean up output files`, i.e. directories
  vanishing mid-write. Cure, in order: `./gradlew --stop`,
  `pkill -f '[K]otlinCompileDaemon'`, `rm -rf buildSrc/build`, then run again.
- **Host-share phantoms recur and block every build.** On the virtiofs checkout an
  interrupted Gradle run leaves entries that `stat` rejects while `ls` lists them
  as `d?????????`; renaming their *immediate* parent fails too, so detach at the
  shallowest ancestor that accepts the rename (`de` → `de_`, `META-INF` →
  `META-INF_`), then remove the tree. Recognize it by `Failed to clean up output
  files` or `Cannot access output property 'destinationDirectory'`.
- **A `buildSrc` test suite never runs from the root build.** The root build drives
  buildSrc only through `:classes`/`:jar`, so `buildSrc/src/test` is dead unless
  invoked. `tasks.named("jar") { dependsOn(tasks.test) }` fails with a circular
  dependency (`test → testClasses → compileTestKotlin → jar`), and a root
  `GradleBuild` task fails with "build name 'buildSrc' … reserved name". buildSrc
  carries no tests today; wire a CI line plus `./gradlew -p buildSrc test` locally
  if one is added.
- **A JFR recording is per forked JVM and needs an existing output directory.**
  `-XX:StartFlightRecording=filename=…/jvmTest-%p.jfr` expands `%p` to the PID but
  does not create `…/profiles/jvmTest`, so `jvmTestProfile` creates it in
  `doFirst` (and deletes it first, or old PIDs would mix into a "merged" run).
  JFR also truncates stacks at 64 frames by default;
  `-XX:FlightRecorderOptions=stackdepth=1024` is required for deep Kotlin call
  chains, and `jfr view`/JMC show the caller chain only from the stored depth.
  Because recordings are per JVM, `maxParallelForks > 1` would produce several
  files instead of one merged profile.
- **`spotlessApply` after staging leaves the reformat unstaged.** It reorders the
  project imports, so run it before `git add`, or amend after it.
- **A one-line collection assertion becomes a multi-line block and can push a
  test method over `LongMethod` (60).** Converting `assertTrue(x.any { ... })` to
  `assertNotNull(x.firstOrNull { ... })`, or `assertTrue(x.none { ... })` to
  `assertEquals(emptyList(), x.filter { ... })`, adds lines. The assertion cleanup
  grew `aSharedInputSplitsOnItsBeltNotAtTheProducer` from 59 to 65 source lines,
  and `detektTestJvm` failed with `[LongMethod]` although the method passed on
  `main`. Recognize it by a test file touched only by assertion rewrites suddenly
  failing `check`. Build repeated rows up front (`val row1 = ...; rows =
  listOf(row1, row2)`) or move plan construction into a fixture before adding the
  assertion blocks — do not suppress the rule.

## Moving files and architecture tooling

- **Package edges cannot be derived from Kotlin imports or a text scan.** Before
  `4f6c3df` most files imported `common.model.*`, so an import-based checker saw
  almost no real edges; even with explicit imports, an import proves a type
  reference, not which package is allowed to reach which. Text scanning is also
  fragile: the tree has multi-line constructor headers, one-line and multi-line
  KDoc, KDoc references (`[Factory]`), and the same name declared in several
  packages (`material`, `recipe`, `name`). A hand-written scanner hit all of these.
  If an edge checker is ever automated, read the compiled constant pool rather than
  the sources, or resolve from the compiler's unresolved-reference list.
- **An architecture guard must read the build output, not the source tree.** The
  removed `PackageEdgesTest` first walked the source dirs and parsed `*.class` names
  inside the `.kt` files, so it found zero class files and passed vacuously; the
  injected `service -> blueprint` probe only failed after pointing it at
  `build/classes/kotlin/jvm/main/...`. If a Gradle-level check is added, prove it by
  injecting the violation it should reject.
- **Classycle, Konture and a bespoke scanner were all evaluated and rejected.**
  Classycle analyzes Java class files, its Gradle plugins are unmaintained
  (2015–2018), and it expects Java naming, so Kotlin edges get silently dropped.
  Konture 1.0.2 (`io.github.baole.konture`) reads the KMP sources exactly but
  mislabels local, private and library types (a reference to `private class
  CorridorShift` and even `Collection<FactoryProduction>` was labeled
  `common.model.CorridorShift` / `common.model.Collection`) and cannot express
  allowed symbols or standard-library packages; applying it in
  `settings.gradle.kts` also breaks the root script (`Unresolved reference 'de'`),
  while the root `build.gradle.kts` `plugins {}` block works. Recognize a wrong
  report by a violation naming a symbol under a package that does not declare it.
- **A `sed`/`python` import rewrite is only a hint; the compiler is the arbiter.**
  Moving a file and rewriting imports textually re-pointed `adjacent` (a `model`
  extension) at a same-named private helper in `band`, and mapped
  `ChestLikeEntity`/`Plumbing` to the wrong package. Run
  `./gradlew compileKotlinJvm compileTestKotlinJvm` after every move and fix only
  what it names; a bulk `git checkout -- src` also reverts the *staged* moves'
  content, so the package line of every moved file must be checked again
  afterwards.
- **Do not rebuild an import block from a name-based usage scan.** A helper that
  inserted `import <newPackage>.<Symbol>` for every symbol found in the body
  silently corrupted ~80 files: it truncated `…common.prototype.Recipe` to
  `…common.prototype` (a package, so `Packages cannot be imported`), inserted into
  a block it then re-sorted without `import `, and added imports of *members* and
  *parameter names* (`production`, `testRecipe`) that are only importable as
  declarations. Recover with `git show HEAD:<path>` plus matching each file's
  original import symbols against its body — never a name-to-package guess. The
  reliable recipe is: `git mv`, `sed` the `package` line, then add the import the
  *compiler* asks for, one symbol at a time.
- **A moved symbol needs an explicit import at every use site.** Kotlin resolves
  names from the file's own package only: after a file moves, each file that used
  one of its symbols must `import` it by name, even though it compiled before.
  `expect`/`actual` pairs must move together (same package, all source sets).
- **A moved `expect`/`actual` pair needs its source-set directories created too.**
  Moving `common/game/production/loadRecipe.kt` also means
  `jsMain`/`jvmMain` `common/game/production/loadRecipe.kt`; the JS one is also the
  `@JsModule` target the webpack config resolves, so renaming the file without
  updating the `@JsModule` path breaks only the browser bundle, not
  `compileKotlinJvm`.
- **A package that is both a directory and a symbol prefix needs care in
  scanners.** `common.prototype` and `common.prototype.selection` are separate
  packages, so a resolver that stops at the longest known *prefix* attributes
  `…prototype.selection.X` to `prototype`. Resolve from the full dotted path (or
  the declared package), never from a prefix match — the package dependency table
  in `architecture.md` treats the nested package as its own unit.
