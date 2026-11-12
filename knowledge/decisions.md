# Decisions

Significant technical and process decisions and their rationale, newest first.
This file records **current constraints only**. Each entry states the decision and
the guardrail that must survive; mechanics belong in `architecture.md` or
`pitfalls.md`, and the pre-change history belongs in the commit message. Keep an
entry under ~20 lines and prepend a new one. A change that supersedes an entry
rewrites or deletes it — never add a "supersedes" note.

## 2026-11-11 — A row faces its consumer; small producer branches distribute

**Decision.** Row order is no longer depth-sorted: `RowTopology` roots the producer graph at the
target. Three or more direct producers distribute across both sides when the largest branch is 3-4
rows; a two-branch target keeps the plan-order split; every other target stays compact and one-sided
with the largest branch nearest the target. A product belt faces the consumer that placed the row,
an input belt faces its producer, and each side holds two belt rows (a capacity-safe overflow pairs
into a mixed lane before crossing).

**Guardrail.** Never reintroduce a depth-only order, place a row before its branch side is known, or
distribute a large branch set (above four rows it strands shared producers, disables compaction and
doubles the layout). Keep `RowTopology` pure, the belt-side allocation in `RowBeltPlacement`, and
graph nodes as plan-row positions — never a map keyed by a `PlannedRow`. A row with no
producer/consumer row keeps the legacy rate-ranked layout.

**See.** `blueprint/planning/row/{RowTopology,RowBeltPlacement,RowPlacer}.kt`;
`BlueprintLayoutIntegrationTest` (`aTargetSitsBetweenItsProducerBranches`,
`aFullSidePairsTwoInputsIntoAMixedBelt`). Pinned layouts: production-science 40x81 (modules north),
utility-science 54x131 (robot frames first), logistic-science 22x43, advanced-circuit 40x41,
rocket-silo 48x119.

## 2026-11-11 — A route box is three tiles, and a wider search is a failure

**Decision.** `RouteSearch` looks at most `ROUTE_MARGIN = 3` tiles past everything placed on the
north, south and east side; the west keeps `ROUTE_WEST_BOUND = 2`, so no route runs around the
import lanes. `LayoutRouter` retries with `WIDE_ROUTE_MARGIN` (24) when the compact box finds
nothing, but appends `route … needed the wide search box` to the layout's failures. A 24-tile ring
made the A* fill the permitted region (its heuristic prices a tile at 3, a straight surface tile
costs 4), so the route search was 47 % of the layout suite's CPU; shrinking the box removed 65 %.

**Guardrail.** Do not widen the compact box silently, and treat the wide retry as a failure, not as
normal: every layout test asserts `failures` is empty. Read `LayoutBoard`'s incremental
`minX/maxX/minY/maxY`; never recompute them by scanning, and never re-add a west margin.

**See.** `blueprint/placing/route/{RouteSearch,LayoutRouter}.kt`;
`LayoutRouterTest.aRoutePastTheCompactBoxIsFoundButReported`.

## 2026-11-11 — A belt bend costs more than a straight tile, a tunnel more than a detour

**Decision.** Belt routes price a turn above a straight tile (`turnCost = 6` against
`surfaceCost = 4`) and a tunnel entry at seven surface tiles (`tunnelEntry = 28`), so a route runs
straight and rounds a belt row instead of diving under it; a crossing no detour can avoid still
tunnels. The reported logistic-science layout rounds the plate rows (21x47) and the rocket-silo URL
trades nine underground runs for surface belts (50x113 instead of 47x116).

**Guardrail.** Keep `turnCost > surfaceCost`; do not move the tunnel entry without re-pinning the
reported layouts (both sit within a few tiles of the threshold). A belt tile faces the tile it turns
into, so a route predicate must not judge the arrival direction; the reserved-feed approach rule
still reads it, because the advanced-circuit layout depends on that.

**See.** `blueprint/placing/route/LayoutRouter.kt`,
`blueprint/placing/routing/BeltRouteRules.kt`; `LayoutRouterTest.aBeltTurnCostsMoreThanAStraightTile`,
`theReportedLogisticScienceUrlCompactsBothCorners`, `theReportedRocketSiloUrlCompacts`.

## 2026-11-11 — A belt route is a simple path and keeps its feed approach clear

**Decision.** `PathPlacer` refuses a belt path that uses one tile twice (`pathConflicts`) and places
nothing; `DeliveryRouter` re-searches with those tiles blocked and reports `no route` when none
stays simple. Pipes are exempt: a pipe tile is a junction. A feed's approach tile
(`feed - heading.step`) is reserved for the feed's material, so no foreign trunk parks there.

**Guardrail.** A belt tile carries one entity with one facing; never let a tunnel role beat the belt
an earlier run placed there (an underground exit loses its landing, and one such error switches off
all compaction). Keep a sink's `feed` and its approach tile reserved for the same material;
`BeltRouteRules` admits the approach tile for that material only.

**See.** `blueprint/placing/special/PathPlacer.kt`,
`blueprint/placing/routing/{DeliveryRouter,SplitDeliveryRouter,BeltRouteRules}.kt`;
`aForeignRouteMayNotTakeTheApproachTileOfAFeed`, `theReportedRocketSiloUrlCompacts`.

## 2026-11-11 — Belts flow forward: a splitter needs its back, no belt circles

**Decision.** `validateLayout` checks belt flow: every splitter is fed straight from behind, and no
belt circles back into itself (`LayoutValidationFlow.kt`). Routing keeps both invariants:
`SplitDeliveryRouter` inserts a splitter only where a same-material belt feeds its back straight on,
and `BeltRouteRules` refuses a route tile that any neighboring belt pours into.

**Guardrail.** Do not allow a same-material merge into a route tile: it absorbs a lane's dead end and
closes a circle (the fusion-reactor plastic belt ran from the LDS lane's west end into the trunk and
back around the splitter). Keep both checks inside `validateLayout`, so a pre-existing flow error
surfaces as a failure and the all-or-nothing compaction gate stays off.

**See.** `blueprint/layout/validation/LayoutValidationFlow.kt`,
`blueprint/placing/routing/{SplitDeliveryRouter,BeltRouteRules}.kt`;
`aSplitterWithoutABeltOnItsBackIsReported`, `theReportedFusionReactorUrlCompacts`.

## 2026-11-11 — A band never needs a belt for its own product

**Decision.** The arrangement search rejects a finished window where a solid the band produces is
consumed by an in-band machine without an incoming link; such a band would need an extra belt for a
resource it already makes. Growth may pass through a partial arrangement
(`arrange(..., requireComplete = false)`) so a later link can complete it; only the finished group is
checked. `RowPlacer.rowShape` keeps a partial input lane as a guard for a hand-built plan.

**Guardrail.** Keep the check on the finished window: the chemical-science chain needs the
intermediate partial link before the next link completes it. Check per machine consumer and cover
produced materials without a link, not only linked materials.

**See.** `blueprint/planning/band/BandSearch.kt` (`hasInBandBeltInput`),
`blueprint/planning/band/FeedCandidates.kt`; `theReportedChemicalScienceUrlKeepsCableOutOfTheBand`,
`aCircuitChainWithSharedConsumersBuildsTheTargetOnce`.

## 2026-11-11 — A band keeps a shared product on the belt

**Decision.** Growth may not internalize a product that leaves a band over a belt while another plan
consumer still needs it; `internalizesSharedOutput` rejects the link, and `candidateScore` penalizes
every outside consumer plus producer overfeed. `DirectFeedContext.consumerScope` carries the main
plan's consumers into the belted pass. `plannedSources` covers a solid only through a row's
`beltMaterials` or a link that reaches the consuming machine (`PlannedRow.internalInputs`), so a
partially linked row still imports the material for its other machines.

**Guardrail.** Keep a shared material such as `electronic-circuit` as the band's output; swallowing
the cable/circuit pair into an advanced-circuit/processing-unit band strands the other circuit
consumers. Pass the main scope into the belted pass, and never count an internal-only recipe result
as produced.

**See.** `blueprint/planning/band/{FeedCandidates,FeedLink,DirectFeedModel,DirectFeed}.kt`,
`blueprint/planning/BlueprintSelection.kt`;
`theReportedUtilityScienceUrlsKeepCircuitAsABandOutput`,
`aMaterialOnlyLinkedInsideABandIsASourceForAnotherRow`.

## 2026-11-11 — Belted rows are banded again

**Decision.** After the main bands, `DirectFeed.build` copies the remaining belted rows as
whole-machine `FactoryProduction` values and runs the same group search over their links at scale 1;
the finished bands replace the plain rows. A recipe may therefore appear in the main band and in a
belted band as two independent productions. The growth pre-filter (`groupMayFit`) uses the propagated
`groupNeeds`, not the plan machine count, and `arrangeBest` searches count vectors up to
`maxBandMachines(links.size)`; `MAX_BAND_RECIPES` is only the recipe ceiling.

**Guardrail.** Keep the belted copies distinct by `amount`, or `nodeCache` and `assigned` treat them
as one production. Keep the main band's internal material off every belt; only the belted band's
output is a source, so `BlueprintPlan.producerOf` and the one-belt-source rule stay valid.

**See.** `blueprint/planning/band/{DirectFeed,FeedCandidates,BandSearch}.kt`;
`theReportedUtilityScienceUrlCompacts`,
`theGrowthBudgetUsesPropagatedNeedsInsteadOfThePlanMachineCounts`.

## 2026-11-10 — A wide row mixes inputs two per belt

**Decision.** A row that would need more than `MAX_ROW_BELTS` solid belt rows pairs the minimum
number of inputs needed to get back to four, lowest rate first. Inputs whose plan rate fits one lane
pair go first, so sharing costs no throughput; a row that still would not fit pairs over-capacity
inputs anyway and accepts the smaller rate (half a lane beats no belt). Pairs form within one feed
direction first; a remaining pair may straddle directions and run east. Outputs keep their own belt
and fill only the inserter lane. A row that still cannot fit reports a failure instead of dropping a
material.

**Guardrail.** `MAX_ROW_BELTS` is a row count, never a material count; keep
`packRowBelts`/`beltRowBudget` the only budget and `bandBeltFits(plan)` on the arranged machine
rates. Keep `LaneLoad` in the belt's own left/right frame, the T-merge sides, and the rule that a
route never side-loads a mixed or half-lane belt; never pair a fluid or an output.

**See.** `blueprint/planning/plan/BeltRows.kt`,
`blueprint/planning/row/{Row,RowPlacer,MachineInserterPlacer}.kt`;
`anOverCapacityInputPairsBeforeTheRowLosesABelt`, `aSixInputRowMixesThreeInputPairs`.

## 2026-11-10 — Test profiling is JDK Flight Recorder, CPU only

**Decision.** `jvmTestProfile` attaches the JDK's own Flight Recorder to the test JVM; no
async-profiler, sampling agent or report generator. The artifact is one merged `.jfr` per run, read
by IntelliJ's JFR viewer, JMC or the `jfr` CLI. The profile is CPU (self time per method) only;
wall-clock attribution is deliberately not computed, because reconstructing it from
`jdk.ThreadPark`/`jdk.ThreadSleep`/monitor events is lossy.

**Guardrail.** Do not reintroduce a hand-written HTML/JSON report or a second profiler; if wall time
becomes a requirement, prefer a tool that consumes the existing `.jfr`. Keep
`maxParallelForks = 1` so the recording stays one merged profile.

**See.** `architecture.md` §Profiling; `pitfalls.md` §Build, Detekt and environment.

## 2026-11-09 — Direct feed is sized by plan rates and grown maximally

**Decision.** A band is no longer accepted by a per-machine cycle bound. `groupNeeds` starts from
each terminal recipe's fractional `amount * scale` and propagates upstream; `FeedWindow` distributes
each link's plan demand over the built consumers. Overfeed is allowed, underfeed is not, and
repeating a pattern may build extra copies. `cycleFit`/`cyclePenalty` rank patterns but never reject
a link. `FeedCandidates` grows each group maximally, retries links, swaps once, merges groups and
accepts only a group whose solid inputs/results fit `MAX_ROW_BELTS = 4`. Bounds:
`GROUP_MAX_MACHINES = 10` (9 for a multi-link band) and `MAX_BAND_RECIPES = 8`.

**Guardrail.** Do not reintroduce a hard `ioRatio` gate or a whole-machine `ceil` before the window;
the window's plan-rate flow is the only feasibility check. Keep one band per production, each band's
internal material off every belt, and the 6:4 cable/circuit case as the repeated 3:2 window
`C E C E C C E C E C`.

**See.** `blueprint/planning/band/{DirectFeed,FeedLink,FeedCandidates,BandSearch}.kt`;
`FractionalDirectFeedTest`, `DirectFeedTest`.

## 2026-11-08 — Recipe exclusions are explicit names, not whole categories

**Decision.** `buildable` keeps only the category rules (extraction, oil processing, recycling,
crushing, smelting, spoiling); it no longer excludes `ORGANIC`, `CRYOGENICS` or `CENTRIFUGING`.
`unplannedRecipes(surface)` (`game/prototype/RecipePolicy.kt`) discards the cycle names
(`fish-breeding`, `nutrients-from-fish`, `kovarex-enrichment-process`), the surface's solid-fuel
alternatives and its coal/carbon recipe, including when the target is one of their products — so
kovarex is never selected and uranium-235 is built by `uranium-processing`. Centrifuging and the
simple chemistry recipes (plastic, sulfur, battery, ...) are planned.

**Guardrail.** One owner: `unplannedRecipes` is the only cycle gate, and `buildable` does not
re-check it. A new real solid cycle gets a name there, never a category ban or a path-local guard,
and no target exemption.

**See.** `game/prototype/RecipePolicy.kt`, `blueprint/planning/band/RecipeFilter.kt`;
`RecipePolicyTest`, `bannedRecipesAreNeverPlanned`, `uranium235IsBuiltByProcessingNotKovarex`.

## 2026-11-07 — Stone is always an import, and solid-fuel recipes are surface-specific

**Decision.** `DirectFeedContext.producers` excludes `stone` on every surface, so it is always a
planned source instead of a direct-fed Vulcanus lava byproduct. `ParsedRecipe.surface` selects one
solid-fuel recipe per surface: light oil elsewhere, heavy oil on Fulgora (its local oil) and ammonia
on Aquilo; the other solid-fuel recipes are excluded.

**Guardrail.** Keep both policies explicit. `FeedNode.ingredients` links every in-plan producer, so a
multi-producer solid is direct-fed from whichever link the walk sees first; a new one needs an
explicit recipe or import policy, never a single-producer filter. Keep one solid-fuel recipe per
surface so a blueprint never needs two producers of the same material.

**See.** `game/prototype/RecipePolicy.kt`, `blueprint/planning/band/DirectFeedModel.kt`;
`vulcanusStoneIsImported`, `solidFuelUsesTheSurfaceRecipe`, `aquiloSolidFuelUsesAmmonia`.

## 2026-11-06 — Direct feed is a production-node graph with feed links

**Decision.** The direct-feed search walks the plan from the target as a production-node graph:
`FeedNode` is one production with lazy `ingredients` and `consumers`, so a production may have
several consumers. `FeedLink` is the edge `(producer, consumer, material)` with per-machine rates, so
a production and an edge exist once no matter how many paths reach them. `DirectFeedContext` owns the
target, included productions, scale, logistics, per-material producer index and lazy `nodeOf`.
`FeedNode.traverse()` keeps a visited set, so a shared producer expands once; there is no
cycle-specific state because `unplannedRecipes` keeps real solid cycles out. Per-machine rates are
`FactoryProduction.inputPerMachine`/`outputPerMachine`; productivity counts for outputs, because
that is what a direct-fed consumer receives.

**Guardrail.** Keep one node per production and one edge per `(producer, consumer, material)`; never
reintroduce a `consumer: FeedLink?` parent or path-local copies. A multi-producer material stays out
of the direct feed by an explicit policy, never by picking one link. Fluids are never links, and
`countVectors` keeps its empty-list guard.

**See.** `blueprint/planning/band/{DirectFeed,DirectFeedModel,FeedLink,FeedCandidates}.kt`,
`core/util/MathUtils.kt`; `DirectFeedTest`.

## 2026-11-06 — Imports are a recipe category, and sources follow the placed rows

**Decision.** Synthetic import recipes (`Optimizer.freeSource`) carry `Category.IMPORT`; `buildable`
decides from the recipe, not the machine, and treats `IMPORT` like extraction. A recipe may produce
one solid result plus any number of fluid results; two solids still need a mixed output belt and stay
unbuildable. `BlueprintPlan.sources` is derived from the rows actually placed
(`plannedSources(rows, logistics, scale)`), and the layout's solid-import classification reads
`BlueprintPlan.sourceMaterials`.

**Guardrail.** Never derive sources from `DirectFeedContext.included`: it contains productions the
planner drops, so the reported imports would disagree with the layout. Keep one source definition:
`plannedSources` for the plan, `sourceMaterials` for `DeliveryPreparation`.

**See.** `blueprint/planning/BlueprintSelection.kt`,
`blueprint/planning/plan/BlueprintPlan.kt`,
`blueprint/placing/routing/DeliveryPreparation.kt`.

## 2026-11-02 — Band machines may take or give a fluid

**Decision.** Any machine in a direct-feed band may take a fluid ingredient or give a fluid output;
the band row gets the same fluid lanes as a plain row (`RowFluidLane.fluids` walks every machine), so
a north/south port is fed or drained by the lane. Only the *link* material must be solid: direct
feeding a fluid buys nothing, because a pipe already moves it at high throughput with a large buffer.

**Guardrail.** Keep the two halves in step: a band machine may use a fluid only because
`FluidLaneFeed` can serve its port. Never drop the `!logistics.isFluid(material)` test in
`FeedNode.ingredients`. A fluid that is both an input and an output gets one feed lane and one drain
lane, keyed by material **and** flow; `FluidFeeder.connectLanes` joins the drain to the feed east of
the row.

**See.** `architecture.md` §Blueprint layout; `pitfalls.md` §Layout and fluids.

## 2026-10-27 — Port tables are written in the placed orientation

**Decision.** `prototypeFluidPorts` lists each machine's `fluid_boxes` already turned into the
orientation `blueprintMachineDirection` places the machine in, so callers use the tiles as-is. The
order is load-bearing: inputs first in the recipe's fluid order, then outputs. A lane's side comes
from the port the recipe assigns (never alphabetical); `inserterPortColumns` reserves only the ports
the recipe's fluids use. A fluid target with a side output port is routed to a marker, not left as a
pipe stub.

**Guardrail.** Never re-rotate or coordinate-sort the table: that produced wrong assignments for the
cryogenic plant, foundry and oil refinery. Never infer an input from "north/west" or sort fluids by
name; a wrong box shows up as one fluid that never arrives while the other works. The four reported
URLs are pinned in `BlueprintFluidPortLayoutTest`.

**See.** `blueprint/planning/plan/BlueprintMachine.kt`; `pitfalls.md` §Layout and fluids.

## 2026-10-27 — Every fluid port on an edge gets its own lane

**Decision.** A row side places one lane per fluid that has a port there, in recipe order, each two
rows further out than the last. Outer lanes' shafts dive under inner lane rows to reach their own
ports, and every lane marker joins one west column (the westmost inline fluid marker when the plan
has no solid import). There is no route for a second fluid on the same edge.

**Guardrail.** A pipe-to-ground end joins a surface pipe only on its mouth, so two fluids may share a
wall. `touchesForeign` and `validateLayout` must read the mouth (`Plumbing.joinsAt`), or a lane's
port pipe looks like a two-fluid merge. A shaft may only bury a pipe-to-ground end that runs on the
other axis.

**See.** `blueprint/planning/row/{RowFluidLane,FluidLanePlacer}.kt`,
`blueprint/placing/plumbing/FluidLaneFeed.kt`, `blueprint/layout/LayoutPipes.kt`.

## 2026-10-27 — A routed fluid marker stays on its port's side

**Decision.** The routed fluid fallback places its marker just west of the first sink and searches
the marker row **outward from the port's edge**: north for a north port, south for a south port, south
for a side port. The scan skips only rows a previous marker claimed or whose pipe block is blocked.

**Guardrail.** Never scan a single direction. A north-edge second fluid (the chemical plant's second
top corner) used to be marked below the whole row, and its feed then looped around the factory; the
reported light-oil URL now marks heavy oil above the row, beside the water lane.

**See.** `blueprint/placing/plumbing/FluidFeeder.kt` (`fluidMarker`, `portEdge`),
`BlueprintFluidLayoutTest`, `BlueprintFluidPortLayoutTest`.

## 2026-10-26 — Import belts are routed before produced deliveries

**Decision.** The solid import routes are laid after the fluid lanes and their source markers, and
before the routed fluid fallback and the produced solid deliveries. All solid sources are still
reserved and placed up front, imports first, so an early route cannot take a later source or marker.
An import therefore keeps the direct path along its marker row; a produced trunk tunnels under it
instead of forcing the import to turn. The target output keeps its existing placement.

**Guardrail.** Do not route produced deliveries before imports, and do not run the solid imports
before the fluid lanes/markers: the pipe lane geometry is fixed first and only the routed fluid
fallback yields to an import. A single turn in an import collapses `lastStraightEast`, so keep the
import's run straight east from its connector to the lane; the reported uranium-rounds URL pins the
straight uranium-238 import.

**See.** `blueprint/placing/routing/{DeliveryPreparation,DeliveryRouter,ImportTrimmer}.kt`,
`blueprint/placing/plumbing/FluidFeeder.kt`, `BlueprintLayoutIntegrationTest`.

## 2026-10-26 — A routed tunnel end connects through a belt

**Decision.** `validateLayout` requires the back tile of every routed underground belt input to hold
a same-material conveyor (or loader output) facing into the run, and the front tile of every routed
output to hold a same-material conveyor (or loader input). A tunnel end may not feed another tunnel
end directly. Connector ends are the exception, recognized by their 2-tile label or 4-tile chest
partner with the marker/chest/inserter halfway between the two ends.

**Guardrail.** A corridor may not cut a belt that feeds a tunnel entrance. A candidate that only
fails after stretching is ruled out by its seam path and the pass tries the next corridor — aborting
the whole pass leaves a long straight belt uncollapsed. Do not weaken the check to "the back tile is
occupied", and do not exempt a route end merely because a connector is adjacent. Evidence: the
reported uranium-rounds URL, where compaction removed `(39,42) SOUTH` and left the input at
`(39,17) SOUTH` beside `(39,16) EAST`.

**See.** `blueprint/layout/validation/LayoutValidationUnderground.kt`,
`blueprint/placing/compaction/CompactionPass.kt`, `LayoutCompactionTest`.

## 2026-10-26 — Fluid feeding: one lane per row, the route is the fallback

**Decision.** A row with a fluid port on its north or south edge gets one lane two rows beyond its
outermost belt (there is no free row between belts and machines): a junction pipe above every port, a
horizontal pipe-to-ground run across every gap and one vertical shaft per port that buries itself
under belts and inserters. The lane is primary; the A* feed only serves a side port, a machine
without a port table or a shaft that found none. Fluid outputs mirror the inputs, machines touch in a
fluid row, and a reserved port never costs an input inserter. The marker sits in the common import
column, pinned with its link and lane head.

**Guardrail.** Do not put a pipe or an inserter on the inserter row, and do not spend an input
inserter on a port. A stage places fluid sources only through `LayoutBoard.placeFluidSource`, which
owns the cheat/marker split; `FluidFeed` carries the sink role so the trimmer cannot turn a drain into
a supply.

**See.** `blueprint/planning/row/{FluidLane,RowFluidLane,FluidLanePlacer}.kt`,
`blueprint/placing/plumbing/FluidLaneFeed.kt`, `blueprint/placing/routing/FluidFeedLinker.kt`.

## 2026-10-24 — The row is the occurrence, not the recipe

**Decision.** A plan occurrence is a plain-data `PlannedRow`: `machines` left to right plus the band
`links`. A plain row repeats its production once per machine; a band is its machine sequence plus
links. Band membership and order are the `rows`/`machines` lists themselves, and each machine is a
bare `FactoryProduction` (no wrapper type). `BlueprintPlan` keeps no derived recipe list: the flat
view the suites need is the `commonTest` extension `BlueprintPlan.recipes`. No `Map` or `Set` is
keyed by a production or a row: producer lookup scans `plan.rows` and compares
`row.plannedRow == production` by value.

**Guardrail.** Do not reintroduce an allocation index, reference identity or a recipe-keyed map:
structurally identical rows must compare equal and stay interchangeable. A band is bounded by
`GROUP_MAX_MACHINES` and a row by `MAX_COPIES`, so the scans stay smaller than the hash tables they
replace.

**See.** `blueprint/planning/plan/BlueprintPlan.kt`, `blueprint/planning/row/Row.kt`.

## 2026-10-22 — The package tree is the domain model

**Decision.** Packages are domains and the arrows point one way, from the grid vocabulary up to the
artifact (`architecture.md` "Package dependencies"). A symbol no other package needs is `internal`;
every cross-package reference is an explicit import of the names used. Enforcement is the compiler
plus review — no app test, and `ForbiddenImport` cannot express the edges. The package budget is the
file count (past 10 is a signal to split, 15 the ceiling); there is no line budget beyond
`LargeClass` (500).

**Guardrail.** Never widen a row or add a package edge to silence a failure; a new edge is a
deliberate table change. A future automated guard belongs at the Gradle/build level and must be
proven by injecting the violation it rejects.

**See.** `architecture.md` §Source layout and §Package dependencies; `pitfalls.md` for moving a file.

## 2026-10-18 — Belt-fed productions are sized by plan demand

**Decision.** A belt-fed production is sized by the plan's own rate demand, not by whole consumer
machines:

`machines = max(ceil(beltedRate ÷ outputPerMachine) − bandOutputMachines,
need − builtMachines)`, capped at `MAX_COPIES`, and built only when at least one consumer is not
direct-fed. `outputPerMachine` is the recipe's single non-fluid result (`beltOutputPerMachine`),
because the plan may label a multi-output recipe with one of its fluids. A band's inserted (internal)
link never counts as belt output: the row keeps no lane for a material it inserts
(`RowPlacer.rowShape` subtracts link materials, and `PlannedRow.exports` is false for a linked
material), so the band's surplus cannot leave the row.

**Guardrail.** Keep one belt source per material; a fully inserted recipe gets no belt row, and the
belted row still covers every outside consumer. `realBlueprintsAreValid` asserts the invariant.

**See.** `blueprint/planning/band/DirectFeed.kt` (`belted`);
`BlueprintLayoutIntegrationTest.realBlueprintsAreValid`.

## 2026-10-18 — Belt end is a `BeltEnd` selection

**Decision.** The solid belt's end marker is a `BeltEnd` value (`constant-combinator` by default,
`infinity-chest` = the old `X`/`--cheats` mode, `buffer-`, `storage-`, `wooden-` or `steel-chest`),
selected in `Selection` and encoded in the URL. A chest marker is the five-tile
`UG-in · inserter · chest · inserter · UG-out` connector; plain chests buffer both ways, infinity
chests source/sink. A space platform offers only the label and clamps every chest code to it.

**Guardrail.** Keep the `X` code and `--cheats` alias working; connector inserters are powered, so the
pole bound that keeps poles out of the import extension is skipped for a chest marker.

**See.** `blueprint/layout/BeltEnd.kt`, `blueprint/placing/special/BeltEndConnector.kt`.
