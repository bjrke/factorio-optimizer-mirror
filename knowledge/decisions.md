# Decisions

Significant technical and process decisions and their rationale, newest first.
This file records **current constraints only**. Each entry states the decision and
the guardrail that must survive; mechanics belong in `architecture.md` or
`pitfalls.md`, and the pre-change history belongs in the commit message. Keep an
entry under ~20 lines and prepend a new one. A change that supersedes an entry
rewrites or deletes it — never add a "supersedes" note.

## 2026-11-11 — A belt bend costs more than a straight tile, a tunnel more than a detour

**Decision.** Belt routes price a turn above one straight tile (`turnCost = 6` against
`surfaceCost = 4`) and a tunnel entry at seven surface tiles (`tunnelEntry = 28`). A route runs
straight and rounds the end of a belt row instead of diving under it; a crossing no detour can avoid
still tunnels. The reported logistic-science gear belt no longer tunnels under the plate rows (it
rounds them), so the circuit trunk is the layout's only route tunnel and the layout is 21x47. The
reported rocket-silo URL drops nine underground runs in exchange for surface belts (50x113 instead of
47x116).

**Guardrail.** Keep `RouteCosts.turnCost > RouteCosts.surfaceCost`; do not move the tunnel entry
without re-pinning the reported layouts, because the gear belt and the rocket-silo layout sit within
a few tiles of the threshold. A belt tile faces the tile it turns into, so a route predicate must
not judge the arrival direction: the side-load rule asks only whether a tile is free and not poured
into. The reserved-feed approach rule still reads the arrival direction, because the
advanced-circuit layout depends on it. Evidence:
`LayoutRouterTest.aBeltTurnCostsMoreThanAStraightTile`,
`LayoutRouterTest.aCornerMayTurnOverAForeignBeltItDoesNotFeed`,
`theReportedLogisticScienceUrlCompactsBothCorners`, `theReportedRocketSiloUrlCompacts`,
`advancedCircuitUrlOnlyTunnelsForItsBandedFeeds`.

**See.** `blueprint/placing/route/LayoutRouter.kt`,
`blueprint/placing/routing/BeltRouteRules.kt`; `pitfalls.md` §Layout and fluids.

## 2026-11-11 — A belt route is a simple path and keeps its feed approach clear

**Decision.** `PathPlacer` refuses a belt path that uses one tile twice (`pathConflicts`) and
places nothing then; `DeliveryRouter` re-searches with those tiles blocked (bounded) and reports
`no route` when none stays simple. Pipes are exempt: a pipe tile is a junction. A feed's approach
tile (`feed - heading.step`, the tile its belt takes from) is reserved for the feed's material, so
no foreign trunk parks there.

**Guardrail.** A belt tile carries one entity with one facing; never let a tunnel role beat the
belt an earlier run placed on the same tile (that is how an underground exit loses its landing,
and one such error switches off all compaction). Keep a sink's `feed` reserved and its approach
tile reserved for the same material; `BeltRouteRules` admits the approach tile for that material
only. Evidence: `PathPlacerTest`, `aForeignRouteMayNotTakeTheApproachTileOfAFeed`,
`theReportedRocketSiloUrlCompacts`.

**See.** `blueprint/placing/special/PathPlacer.kt`,
`blueprint/placing/routing/{DeliveryRouter,SplitDeliveryRouter,BeltRouteRules,DeliveryPreparation}.kt`,
`blueprint/layout/LayoutBoard.kt`, `pitfalls.md` §Belt lanes and mixed rows.

## 2026-11-11 — Belts flow forward: a splitter needs its back, no belt circles

**Decision.** `validateLayout` also checks the belt flow: every splitter must be
fed straight from behind, and no belt may circle back into itself
(`LayoutValidationFlow.kt`). The routing keeps the two invariants instead of
relying on the check: `SplitDeliveryRouter` inserts a splitter only where a
same-material belt feeds its back straight on (not on a turn), and
`BeltRouteRules` refuses a route tile that any neighboring belt pours into, even a
belt of the same material.

**Guardrail.** Do not allow a same-material merge into a route tile: it absorbs a
lane's dead end and closes a circle (the reported fusion-reactor plastic belt ran
from the LDS lane's west end into the trunk and back around the splitter). Keep
the splitter on the trunk's straight run; a splitter is not a belt tile that a
belt may turn into. Keep both checks inside `validateLayout`: a pre-existing flow
error then surfaces as a failure, and by the all-or-nothing gate compaction stays
off instead of shipping silently. Evidence:
`aSplitterWithoutABeltOnItsBackIsReported`,
`aBeltCircleIsReportedOnceAtItsFirstTile`,
`theReportedFusionReactorUrlCompacts`.

**See.** `blueprint/layout/validation/LayoutValidationFlow.kt`,
`blueprint/placing/routing/{SplitDeliveryRouter,BeltRouteRules}.kt`;
`pitfalls.md` §Layout and fluids.

## 2026-11-11 — A band never needs a belt for its own product

**Decision.** The arrangement search rejects a window where a solid material the
band produces is consumed by a machine of the same band without an incoming
link. Such a band would need an extra belt for a resource it already makes.
Growth may pass through a partial arrangement (`arrange(..., requireComplete =
false)`) so a later link can complete the band; only the finished group is
checked. `RowPlacer.rowShape` keeps a belt input for a partially linked material
as a guard for a hand-built plan, but the planner does not produce one.

**Guardrail.** Keep the check on the finished window, not on every growth step:
the chemical-science chain needs the intermediate partial link before the next
link completes it. The check is per machine consumer and covers produced
materials without a link, not only linked materials. Evidence: the reported
chemical-science URL now keeps cable out of the band entirely;
`theReportedChemicalScienceUrlKeepsCableOutOfTheBand`,
`aCircuitChainWithSharedConsumersBuildsTheTargetOnce`,
`aLinkOnlyRemovesTheInputsItActuallyReaches`.

**See.** `blueprint/planning/band/BandSearch.kt` (`hasInBandBeltInput`),
`blueprint/planning/band/FeedCandidates.kt`,
`blueprint/planning/row/RowPlacer.kt`; `pitfalls.md` §Blueprint planning.

## 2026-11-11 — A band keeps a shared product on the belt

**Decision.** Growth may not turn a product that currently leaves a band over a
belt into an internal link while another plan consumer still needs it. A link
that internalizes a producer whose product has outside consumers is rejected,
and `candidateScore` penalizes every outside consumer plus producer overfeed.
`DirectFeedContext.consumerScope` carries the main plan's consumers into the
belted pass. `plannedSources` covers a solid only through a row's
`beltMaterials` or a link that reaches the consuming machine
(`PlannedRow.internalInputs`); a partially linked row therefore still imports the
material for its other machines, and `DeliveryPreparation` reports a sink with
no producer row as a failure.

**Guardrail.** Keep a shared material such as `electronic-circuit` as the band's
output; do not let the cable/circuit pair be swallowed into an
advanced-circuit/processing-unit band, because that strands the other circuit
consumers. The belted pass must pass the main scope, and `plannedSources` must
not count an internal-only recipe result as produced. Evidence: the reported
utility-science and processing-unit URLs build a cable→circuit band and a
cable→advanced-circuit→processing-unit band; robot frames draw circuit from the
cable/circuit band. `theReportedUtilityScienceUrlsKeepCircuitAsABandOutput`,
`aMaterialOnlyLinkedInsideABandIsASourceForAnotherRow`.

**See.** `blueprint/planning/band/{FeedCandidates,FeedLink,DirectFeedModel,DirectFeed}.kt`,
`blueprint/planning/BlueprintSelection.kt`,
`blueprint/placing/routing/DeliveryPreparation.kt`; `pitfalls.md` §Blueprint
planning.

## 2026-11-11 — Belted rows are banded again

**Decision.** After the main direct-feed bands are chosen, `DirectFeed.build`
copies the remaining belted rows as whole-machine `FactoryProduction` values and
runs the same group search over the links between them at scale 1; the finished
bands replace the plain rows. A recipe may therefore appear in the main band and
in a belted band as two independent productions. The growth pre-filter
(`groupMayFit`) uses the propagated `groupNeeds`, not each recipe's plan machine
count, and `arrangeBest` searches count vectors up to
`maxBandMachines(links.size)`; `MAX_BAND_RECIPES` remains only the recipe-count
ceiling.

**Guardrail.** Keep the belted copies distinct by `amount`, or `nodeCache` and
the `assigned` set treat them as one production. Keep the main band's internal
material off every belt; only the belted band's output is a belt source, so
`BlueprintPlan.producerOf` and the one-belt-source rule stay valid. Evidence: the
reported utility-science and processing-unit URLs now band their belted
cable/circuit pair instead of adding a second plain pair;
`theReportedUtilityScienceUrlsBandTheirBeltedCableAndCircuit`,
`theGrowthBudgetUsesPropagatedNeedsInsteadOfThePlanMachineCounts`.

**See.** `blueprint/planning/band/{DirectFeed,FeedCandidates,BandSearch}.kt`;
`pitfalls.md` §Blueprint planning.

## 2026-11-10 — A wide row mixes inputs two per belt

**Decision.** A row that would need more than `MAX_ROW_BELTS` solid belt rows pairs the
minimum number of inputs needed to get back to four, lowest-rate first. Inputs whose plan rate
fits one belt lane pair first, so sharing costs no throughput; a row that still would not fit
pairs over-capacity inputs anyway and accepts the smaller lane rate, because a belt that cannot
carry the full plan rate is better than a missing belt or a dropped product. Pairs form within
one feed direction first; a remaining pair may straddle directions and then runs east. A row
with four or fewer materials keeps its single belts, and a row that still cannot fit (more
disjoint pairs than inputs) reports a failure instead of silently dropping a material. Outputs
keep their own belt and fill only the inserter lane; input/output mixing with its end filter is
out of scope.

**Guardrail.** `MAX_ROW_BELTS` is still the number of rows, never a material count; keep
`packRowBelts`/`beltRowBudget` the only budget. A mixed belt is modelled by `LaneLoad` in its
own left/right frame, and a turn keeps the slots. Keep the T-merge sides (far north above,
near north below, near south above, far south below), the feeder-side-to-lane mapping, and the
rule that a route never side-loads a mixed or half-lane belt. Pair capacity-safe inputs before
over-capacity ones, never pair a fluid or an output, and keep the failure path for a row that
still needs too many rows. Keep the band fit on the arranged machine rates
(`bandBeltFits(plan)`), not on material count. Evidence:
`anOverCapacityInputPairsBeforeTheRowLosesABelt`, `theReportedRocketSiloUrlCompacts` (five
solid inputs and one product on four belt rows).

**See.** `blueprint/planning/plan/BeltRows.kt`,
`blueprint/planning/row/{Row,RowPlacer,MachineInserterPlacer}.kt`,
`blueprint/placing/routing/{DeliveryPreparation,ImportTrimmer}.kt`,
`pitfalls.md` §Belt lanes and mixed rows.

## 2026-11-10 — Test profiling is JDK Flight Recorder, CPU only

**Decision.** `jvmTestProfile` attaches the JDK's own Flight Recorder to the test
JVM; it does not add async-profiler, a sampling agent or a report generator.
The artifact is one merged `.jfr` per run, read by IntelliJ's JFR viewer, JMC or
the `jfr` CLI. The profile is CPU (self time per method) only; wall-clock
attribution is deliberately not computed, because reconstructing it from
`jdk.ThreadPark`/`jdk.ThreadSleep`/monitor events is lossy and no maintained tool
does it from this recording alone.

**Guardrail.** Do not reintroduce a hand-written HTML/JSON report or a second
profiler for the same task; if wall time becomes a requirement, prefer a tool
that consumes the existing `.jfr` over new build code, and do not download a
native agent without an explicit decision. Keep one fork
(`maxParallelForks = 1`) so the recording stays a single merged profile.

**See.** `architecture.md` §Profiling; `pitfalls.md` §Build, Detekt and
environment (JFR recording traps).

## 2026-11-09 — Direct feed is sized by plan rates and grown maximally

**Decision.** A band is no longer accepted by a per-machine cycle bound. `groupNeeds` starts
from each terminal recipe's fractional `amount * scale` and propagates needs upstream;
`FeedWindow` distributes each link's plan demand over the built consumers. Overfeed is allowed,
underfeed is not, and repeating a pattern may build extra copies to compensate for a worse ratio.
`cycleFit`/`cyclePenalty` rank patterns but never reject a link. `FeedLink.kt` sorts links by belt
volume, bulk gain, solid-ingredient count, partly-used machines, the pair's remaining belt lanes and
cycle quality; `FeedCandidates` grows each group maximally, retries links, swaps once, merges groups
and only accepts a finished group whose solid inputs/results fit `MAX_ROW_BELTS = 4`. Growth may pass
through an over-belt group so an added producer can internalize an ingredient; a seed that never
fits is retried from another link. Every production belongs to at most one band. Bounds are
`GROUP_MAX_MACHINES = 10` (9 for a multi-link band) and `MAX_BAND_RECIPES = 8`.

**Guardrail.** Do not reintroduce a hard `ioRatio` gate or a whole-machine `ceil` before the
window; the window's plan-rate flow is the only feasibility check. Keep one band per production,
keep each band's internal material off every belt, and never build a band needing more than
`MAX_ROW_BELTS` solid lanes. The 6:4 cable/circuit case must stay the repeated 3:2 window
`C E C E C C E C E C`, not two independent bands.

**See.** `blueprint/planning/band/{DirectFeed,FeedLink,FeedCandidates,BandSearch}.kt`;
`FractionalDirectFeedTest`, `DirectFeedTest`; `architecture.md` §Blueprint layout.

## 2026-11-08 — Recipe exclusions are explicit names, not whole categories

**Decision.** `buildable` keeps only the category rules (extraction, oil processing, recycling,
crushing, smelting, spoiling); it no longer excludes `ORGANIC`, `CRYOGENICS` or `CENTRIFUGING`.
The optimizer's `unplannedRecipes(surface)` discards the cycle names
(`game/prototype/RecipePolicy.kt`: `fish-breeding`, `nutrients-from-fish`,
`kovarex-enrichment-process`), the surface's solid-fuel alternatives and its coal/carbon recipe,
including when the target is one of their products: kovarex is never selected, so uranium-235 is
built by `uranium-processing`. Centrifuging and the simple chemistry recipes (plastic, sulfur,
battery, ...) are planned.

**Guardrail.** One owner: `unplannedRecipes` is the only cycle gate, and `buildable` does not
re-check it because a plan can never contain one of those recipes. A new real solid cycle gets a
name there, never a category ban or a path-local guard; do not add a target exemption for it.
Evidence: `RecipePolicyTest`, `bannedRecipesAreNeverPlanned`, `nutrientsAreNotBredFromFish`,
`uranium235IsBuiltByProcessingNotKovarex`, `advancedCircuitsBuildTheirPlasticAndCopperCable`.

**See.** `game/prototype/RecipePolicy.kt`, `blueprint/planning/band/RecipeFilter.kt`,
`optimize/Optimizer.kt`, `pitfalls.md` §Blueprint planning.

## 2026-11-07 — Stone is always an import, and solid-fuel recipes are surface-specific

**Decision.** `DirectFeedContext.producers` excludes `stone` on every surface, so it is always
reported as a planned source instead of being direct-fed from one of Vulcanus' lava byproducts.
`ParsedRecipe.surface` selects one solid-fuel recipe per surface: `solid-fuel-from-light-oil`
elsewhere, `solid-fuel-from-heavy-oil` on Fulgora (its local oil resource) and
`solid-fuel-from-ammonia` on Aquilo. The other solid-fuel recipes are excluded.

**Guardrail.** Keep both policies explicit. `FeedNode.ingredients` links every in-plan producer of
a solid, so a material with two producers is direct-fed from whichever link the walk sees first
(the others are dropped by `maximalGroups`); a new multi-producer solid therefore needs an explicit
recipe or import policy, not a single-producer link filter. Keep one solid-fuel recipe per surface
so a blueprint never needs two producers of the same material. Evidence:
`vulcanusStoneIsImported`, `rocketFuelUsesTheLightOilSolidFuelRecipe`,
`solidFuelUsesTheSurfaceRecipe` and `aquiloSolidFuelUsesAmmonia`.

**See.** `game/selection/SurfaceSelection.kt`,
`blueprint/planning/band/DirectFeedModel.kt`, `optimize/Optimizer.kt`,
`pitfalls.md` §Blueprint planning.

## 2026-11-06 — Direct feed is a production-node graph with feed links

**Decision.** The direct-feed search walks the plan from the target.
`blueprint/planning/band/DirectFeedModel.FeedNode` is one production and owns the
lazy `ingredients` (links to the producers of its inputs) and `consumers` (links
that take its product); a production may have several consumers. `FeedLink` is the
edge `(producer, consumer, material)` with the per-machine rates, so a production
and an edge exist once no matter how many paths reach them.
`DirectFeedContext` owns the target, the included productions, the scale, the
logistics, the per-material producer index and the lazy `nodeOf` lookup.
`FeedNode.traverse()` keeps a visited-node set, so a shared producer is expanded only
once; there is no cycle-specific state because the optimizer's `unplannedRecipes` keeps
every real solid cycle out of the plan. `BandSearch` is a set of extensions on the
context, and the count-vector, permutation and GCD helpers are generic functions
in `core/util/MathUtils.kt`. Per-machine rates are
`FactoryProduction.inputPerMachine`/`outputPerMachine`; productivity counts for
outputs because that is what a direct-fed consumer receives.

**Guardrail.** Keep one node per production and one edge per
`(producer, consumer, material)`; never reintroduce a `consumer: FeedLink?` parent
or path-local copies. A material with several in-plan producers must be kept out of the
direct feed by an explicit recipe or import policy (stone, solid fuel), never by
arbitrarily picking one link. Fluids are never links. Keep the `visited` set in
`traverse()`. `FeedCandidates` assigns every production to at most one band. Keep the
`countVectors` empty-list guard.

**See.** `architecture.md` §Blueprint layout; `blueprint/planning/band/DirectFeed.kt`,
`blueprint/planning/band/DirectFeedModel.kt`, `blueprint/planning/band/FeedLink.kt`,
`blueprint/planning/band/FeedCandidates.kt`, `blueprint/planning/band/BandSearch.kt`,
`core/util/MathUtils.kt`, `pitfalls.md` §Blueprint planning.

## 2026-11-06 — Imports are a recipe category, and sources follow the placed rows

**Decision.** Synthetic import recipes (`Optimizer.freeSource`) carry `Category.IMPORT`;
`buildable` decides from the recipe, not the machine, and treats `IMPORT` like an
extraction category. A recipe may produce one solid result plus any number of fluid
results, because fluids leave a row through their own lanes; two solids still need a
mixed output belt and stay unbuildable. `BlueprintPlan.sources` is derived from the
rows that were actually placed (`plannedSources(rows, logistics, scale)`), and the
layout's solid-import classification reads `BlueprintPlan.sourceMaterials` instead of
recomputing producers. Surface-specific imports are a separate explicit policy; stone
on Vulcanus is covered by the 2026-11-07 entry.

**Guardrail.** Never derive sources from `DirectFeedContext.included`: it contains
productions the planner drops (a fluid maker whose solid byproduct is not consumed),
so the reported imports would disagree with the layout. Keep one source definition:
`plannedSources` for the plan and `sourceMaterials` for `DeliveryPreparation`.

**See.** `blueprint/planning/BlueprintSelection.kt`,
`blueprint/planning/plan/BlueprintPlan.kt`,
`blueprint/placing/routing/DeliveryPreparation.kt`,
`blueprint/planning/band/RecipeFilter.kt`, `pitfalls.md` §Blueprint planning.

## 2026-11-05 — Result models live outside the optimizer; the pipeline root sits at blueprint

**Decision.** The optimizer's output models `FactoryProduction`, `OptimizationResult`
and `factoryProductionComparator` move out of `optimize` into a new shared `model`
package (which imports only `core/model` and `game/prototype`). The
`Optimizer`, `MachineCatalog` and `LinearProgram` stay in `optimize`. The pipeline
root (`RowLayout`) moves out of the `blueprint.printing` subpackage up to the
`blueprint` root.

**Guardrail.** The blueprint planner reaches an optimizer result through `model`,
never by importing `optimize` — the plan packages and `optimize` must not depend on
each other (both depend on `model`). Keep `blueprint/RowLayout.kt` as the single
ordered `build()` at the `blueprint` root; do not bury it under `blueprint.printing`
again.

**See.** `architecture.md` §Source layout and §Package dependencies.

## 2026-11-02 — Band machines may take or give a fluid

**Decision.** Any machine in a direct-feed band may take a fluid ingredient or
give a fluid output; the band row gets the same fluid lanes as a plain row
(`RowFluidLane.fluids` walks every machine), so a port on the row's north or
south edge is fed (or drained) by the lane. Only the *link* material must be
solid, because direct feeding a fluid buys nothing: there are no inserters to
save, and a pipe already moves a fluid at high throughput and holds a large
buffer. The lane serves a band machine's port like any other, so neither a
producer's nor a consumer's fluid need keeps the recipe belted.

**Guardrail.** Keep the two halves in step: a band machine may use a fluid only
because `FluidLaneFeed` can serve its port. Never drop the
`!logistics.isFluid(material)` test in `FeedNode.ingredients`; a fluid is never a
link. A fluid that is both an input and an output gets one feed lane and one
drain lane (keyed by material **and** flow, never merged by material);
`FluidFeeder.connectLanes` joins the drain to the feed east of the row.

**See.** `architecture.md` §Blueprint layout; `pitfalls.md` §Layout and fluids.

## 2026-10-30 — One fixture reads a layout back for every suite

**Decision.** `RowLayout` is `internal` and owns the ordered pipeline through
`blueprint()` and `layoutDump()`. Its build is idempotent, so both read-backs share
one instance instead of rebuilding the layout. The `jvmTest` `Layout` fixture wraps
those two calls and exposes the facts the suites assert on (entities, failures,
machines, plumbing, belt materials, port pipes); `blueprint/printing/fixtures` lives in
`jvmTest` with the layout suites that read it, while material values stay in
`commonTest/common/fixtures` for `jsTest`.

**Guardrail.** Do not reintroduce top-level `fromPlaced`/`blueprintLayoutDump`
wrappers, and do not make `blueprint()` or `layoutDump()` run the pipeline again
after it has built. A new layout fact gets a property on the `Layout` fixture, not a
second dump function or a widened production accessor.

**See.** `architecture.md` §Blueprint layout; `conventions.md` §Testing and
§Behavior-preserving refactors.

## 2026-10-27 — Port tables are written in the placed orientation

**Decision.** `prototypeFluidPorts` lists each machine's `fluid_boxes` already turned into the
orientation `blueprintMachineDirection` places the machine in, so callers use the tiles as-is.
The order is load-bearing: the inputs come first in the order the recipe's fluid ingredients take
them, then the outputs in result order. A lane's side comes from the port the recipe assigns to
the fluid, in that order (never alphabetical); `inserterPortColumns` reserves only the ports the
recipe's fluids use. A fluid target with a side output port is routed to a marker like an input,
not left as a pipe stub.

**Guardrail.** Never re-rotate or coordinate-sort the table: rotating and sorting it is what
produced wrong port assignments for the cryogenic plant, foundry and oil refinery. Never infer an
input from "north/west" or sort fluids by name; a wrong box shows up as one fluid that never
arrives while the other works. The four reported URLs are pinned in
`BlueprintFluidPortLayoutTest`.

**See.** `architecture.md` §Blueprint layout; `pitfalls.md` "Layout and fluids";
`blueprint/planning/plan/BlueprintMachine.kt`.

## 2026-10-27 — Every fluid port on an edge gets its own lane

**Decision.** A row side places one lane per fluid that has a port there, in recipe order, each two
rows further out than the last. The outer lanes' shafts dive under the inner lane rows to reach
their own ports, and every lane marker joins one west column (the westmost inline fluid marker when
the plan has no solid import). There is no route for a second fluid on the same edge.

**Guardrail.** A pipe-to-ground end joins a surface pipe only on its mouth, so two fluids may share
a wall. `touchesForeign` and `validateLayout` must read the mouth (`Plumbing.joinsAt`), or a lane's
port pipe looks like a two-fluid merge. A shaft may only bury a pipe-to-ground end that runs on the
other axis.

**See.** `blueprint/planning/row/RowFluidLane.kt`, `blueprint/planning/row/FluidLanePlacer.kt`, `blueprint/placing/plumbing/FluidLaneFeed.kt`,
`blueprint/layout/LayoutPipes.kt`, `blueprint/layout/validation/LayoutValidationTransport.kt`, `blueprint/placing/routing/ImportTrimmer.kt`.

## 2026-10-27 — A routed fluid marker stays on its port's side

**Decision.** The routed fluid fallback places its marker just west of the first sink and searches
the marker row **outward from the port's edge**: north for a north port, south for a south port,
south for a side port. The scan skips only rows a previous marker claimed or rows whose pipe block
is blocked.

**Guardrail.** Never scan a single direction. A north-edge second fluid (the chemical plant's
second top corner) used to be marked below the whole row, and its feed then looped around the
factory; the reported light-oil URL now marks heavy oil above the row, beside the water lane.

**See.** `blueprint/placing/plumbing/FluidFeeder.kt` (`fluidMarker`, `portEdge`), `BlueprintFluidLayoutTest`,
`BlueprintFluidPortLayoutTest`.

## 2026-10-26 — Import belts are routed before produced deliveries

**Decision.** The solid import routes are laid after the fluid lanes and their source markers, and
before the routed fluid fallback and the produced solid deliveries. All solid sources are still
reserved and placed up front, imports first, so an early route cannot take a later source or
marker. An import therefore keeps the direct path along its marker row; a produced trunk tunnels
under it instead of forcing the import to turn. The target output keeps its existing placement.

**Guardrail.** Do not route produced deliveries before imports, and do not run the solid imports
before the fluid lanes/markers: the pipe lane geometry is fixed first and only the routed fluid
fallback yields to an import. A single turn in an import makes `lastStraightEast` collapse, so the
trimmer cannot move all markers to one column; keep the import's run straight east from its
connector to the lane. The reported uranium-rounds URL pins the straight uranium-238 import.

**See.** `blueprint/placing/routing/DeliveryPreparation.kt`, `blueprint/placing/routing/DeliveryRouter.kt`,
`blueprint/placing/routing/ImportTrimmer.kt`, `blueprint/placing/plumbing/FluidFeeder.kt`, `BlueprintLayoutIntegrationTest`.

## 2026-10-26 — A routed tunnel end connects through a belt

**Decision.** `validateLayout` requires the back tile of every routed underground belt input to
hold a same-material conveyor (or loader output) facing into the run, and the front tile of every
routed output to hold a same-material conveyor (or loader input). A tunnel end may not feed another
tunnel end directly. Connector ends are the exception, and are recognized by their 2-tile label or
4-tile chest partner with the marker/chest/inserter halfway between the two ends.

**Guardrail.** A corridor may not cut a belt that feeds a tunnel entrance. Evidence: the reported
uranium-rounds URL, where compaction removed `(39,42) SOUTH` and left the input at `(39,17) SOUTH`
beside `(39,16) EAST`; the router was correct before compaction. A candidate that only fails after
stretching is ruled out by its seam path and the pass tries the next corridor — aborting the whole
pass leaves a long straight belt uncollapsed (the `0.3` uranium variant). Do not weaken the check
to "the back tile is occupied", and do not exempt a route end merely because some connector is
adjacent — `(38,17) SOUTH` can sit beside another import's loader.

**See.** `blueprint/layout/validation/LayoutValidationUnderground.kt`,
`blueprint/placing/compaction/CompactionPass.kt`, `BlueprintLayoutAssertions.kt`, `LayoutCompactionTest`.

## 2026-10-26 — Fluid feeding: one lane per row, the route is the fallback

**Decision.** A row with a fluid port on its north or south edge gets one lane two
rows beyond its outermost belt (there is no free row between belts and machines):
a junction pipe above every port, a horizontal pipe-to-ground run across every gap
and one vertical shaft per port that buries itself under belts and inserters. The
lane is primary; the A* feed only serves a side port, a machine without a port
table or a shaft that found none. Fluid outputs mirror the inputs, machines touch
in a fluid row, and a reserved port never costs an input inserter. The marker sits
in the common import column, pinned with its link and lane head.

**Guardrail.** Do not put a pipe or an inserter on the inserter row, and do not
spend an input inserter on a port. A stage places fluid sources only through
`LayoutBoard.placeFluidSource`, which owns the cheat/marker split; `FluidFeed`
carries the sink role so the trimmer cannot turn a drain into a supply.

**See.** `architecture.md` §Blueprint layout; `blueprint/planning/row/FluidLane.kt`,
`blueprint/planning/row/RowFluidLane.kt`, `blueprint/planning/row/FluidLanePlacer.kt`,
`blueprint/placing/plumbing/FluidLaneFeed.kt`, `blueprint/placing/routing/FluidFeedLinker.kt`.

## 2026-10-24 — The row is the occurrence, not the recipe

**Decision.** A plan occurrence is a plain-data `PlannedRow`: `machines` left to
right plus the band `links`. A plain row repeats its production once per machine; a
band is its machine sequence plus links. Band membership and order are the
`rows`/`machines` lists themselves, and each machine is a bare `FactoryProduction`
(no wrapper type). `BlueprintPlan` keeps no derived recipe list: the flat view the
suites need is the `commonTest` extension `BlueprintPlan.recipes`. No `Map` or
`Set` is keyed by a production or a row: producer lookup scans `plan.rows` and
compares `row.plannedRow == production` by value.

**Guardrail.** Do not reintroduce an allocation index, reference identity or a
recipe-keyed map: structurally identical rows must compare equal and stay
interchangeable. A band is bounded by `GROUP_MAX_MACHINES` and a row by
`MAX_COPIES`, so the scans stay smaller than the hash tables they replace.

**See.** `architecture.md` §Blueprint layout; `blueprint/planning/plan/BlueprintPlan.kt`,
`blueprint/planning/row/Row.kt`.

## 2026-10-22 — The package tree is the domain model

**Decision.** Packages are domains and the arrows point one way, from the grid
vocabulary up to the artifact (`architecture.md` "Package dependencies"). A symbol
no other package needs is `internal`; every cross-package reference is an explicit
import of the names used. Enforcement is the compiler plus review — no app test,
and `ForbiddenImport` cannot express the edges. The package budget is the file
count (past 10 files is a signal to split, 15 the ceiling); there is no line
budget beyond `LargeClass` (500).

**Guardrail.** Never widen a row or add a package edge to silence a failure; a new
edge is a deliberate table change. A future automated guard belongs at the
Gradle/build level and must be proven by injecting the violation it rejects.

**See.** `architecture.md` §Source layout and §Package dependencies;
`pitfalls.md` for moving a file.

## 2026-10-20 — The layout is explicit stages with one ordered `build()`

**Decision.** The layout splits by ownership of state, not by line count: the
board and its primitives, the router, the stretcher, the compaction and the row
planner. Every stage takes one `blueprint/planning/row/LayoutStage` context; shared mutable
state lives in one small holder. There is no stage interface and no DI container,
and no KMP module split — it would duplicate `expect`/`actual`, resources and test
targets for ~7k lines of `commonMain`.

**Guardrail.** The `build()` order is load-bearing: pipes before belts, the import
trim before the underground stretch, compaction before the poles. Any further
split must prove itself neutral against `BlueprintLayoutSnapshotTest` at the
pre-change commit.

**See.** `architecture.md` §Blueprint layout;
`jvmTest/…/blueprint/printing/BlueprintLayoutSnapshotTest.kt`.

## 2026-10-20 — Detekt is the quality gate, without baselines

**Decision.** Detekt is the gate, configured through `buildUponDefaultConfig` plus
`config/detekt/detekt.yml`: `LargeClass` 500, `MagicNumber` off, guard clauses
excluded from `ReturnCount`, ≤ 3 loop jumps and ≤ 4 destructured names. `check`
runs the type-resolved JVM tasks and the light JS source-set tasks; the whole-tree
`detekt` task stays available for one-off runs. There are **no committed
baselines**.

**Guardrail.** Every finding fails `check`. Fix it by extracting a named
responsibility; suppress only a proven false positive with a written reason.

**See.** `config/detekt/detekt.yml`; `conventions.md` §Code size and file layout.

## 2026-10-19 — No per-task checkpoint layer

**Decision.** There is no `knowledge/tasks/`, no task IDs and no checkpoints.
`knowledge/` holds the durable record and commit messages hold the per-change
detail; `/handoff` is the explicit gesture that promotes findings and states the
next action. The agent never compacts on its own; OpenCode's `compaction.auto`
decides when.

**Guardrail.** Do not reintroduce a task file or a checkpoint mechanism to carry
state between turns.

## 2026-10-18 — Belt-fed productions are sized by plan demand

**Decision.** A belt-fed production is sized by the plan's own rate demand,
not by whole consumer machines:

`machines = max(ceil(beltedRate ÷ outputPerMachine) − bandOutputMachines,
need − builtMachines)`, capped at `MAX_COPIES`, and built only when at least one
consumer is not direct-fed. `outputPerMachine` here is the recipe's single
non-fluid result (`beltOutputPerMachine`), because the plan may label a
multi-output recipe with one of its fluids. A band's inserted (internal) link
never counts as belt output, even when a consumer outside the band still draws
the material: the row keeps no lane for a material it inserts (`RowPlacer.rowShape`
subtracts link materials, and `PlannedRow.exports` is false for a linked
material), so the band's surplus cannot leave the row. Only a production with no
link of that material exports it, and the belted row still covers every outside
consumer.

**Guardrail.** Keep one belt source per material; a fully inserted recipe gets no
belt row. `realBlueprintsPutEveryMaterialOnAtMostOneBelt` (in
`BlueprintLayoutIntegrationTest`) asserts the invariant.

**See.** `blueprint/planning/band/DirectFeed.kt` (`belted`); `architecture.md` §Blueprint layout.

## 2026-10-18 — Belt end is a `BeltEnd` selection

**Decision.** The solid belt's end marker is a `BeltEnd` value
(`constant-combinator` by default, `infinity-chest` = the old `X`/`--cheats` mode,
`buffer-`, `storage-`, `wooden-` or `steel-chest`), selected in `Selection` and
encoded in the URL. A chest marker is the five-tile
`UG-in · inserter · chest · inserter · UG-out` connector; plain chests buffer both
ways, infinity chests source/sink. A space platform offers only the label and
clamps every chest code to it.

**Guardrail.** Keep the `X` code and `--cheats` alias working; connector inserters
are powered, so the pole bound that keeps poles out of the import extension is
skipped for a chest marker.

**See.** `architecture.md` §Blueprint layout; `blueprint/layout/BeltEnd.kt`,
`blueprint/placing/special/BeltEndConnector.kt`.

## 2026-10-18 — Durable knowledge lives in a visible `knowledge/` directory

**Decision.** Durable knowledge lives in the visible top-level `knowledge/`
directory (`index.md`, `architecture.md`, `conventions.md`, `pitfalls.md`,
`decisions.md`); `AGENTS.md` keeps only always-on rules and points here. Agents
load `index.md` first, then only the files the work needs.

**Guardrail.** Promote only reusable, stable and verified findings; keep per-change
detail in the commit message and delete a decision that no longer applies instead
of archiving it.
