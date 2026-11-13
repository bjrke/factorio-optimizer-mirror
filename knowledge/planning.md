# Planning

How the optimizer selects recipes and how a plan becomes direct-feed bands. Load
this file when you change `Optimizer`, `BlueprintSelection`, `DirectFeed` or the
band search; the layout that consumes the plan is in `layout.md`.

## Direct feed and bands

`blueprint/planning/band/DirectFeed.kt` (pure, commonMain) decides which recipes a plan builds as
a **direct-feed band**: a solid material whose producer and consumer can share one interleaved row
is inserted machine to machine. `BlueprintPlan.rows` (`PlannedRow`/`PlannedLink`) is the single
source of truth: a band is a `PlannedRow` whose `links` are non-empty and whose `machines` hold one
`FactoryProduction` per machine; a plain row repeats its one production once per machine and has no
links.

The search is a graph walk from the target. `DirectFeedModel.FeedNode` is one production; its lazy
`ingredients` are the links to the producers of its inputs and its lazy `consumers` the links that
take its product, so a production may have several consumers. `FeedLink` is the edge (producer,
consumer, material); `FeedNode.traverse()` walks the ingredients once with a visited set, so a
shared production is not expanded twice. `DirectFeedContext` owns the target, the included
productions, the scale, the logistics, the per-material producer index (with `stone` always
excluded), the lazy `nodeOf` lookup and the `consumerScope` (the main plan's productions, so the
belted pass sees a shared product's consumers). `FeedLink.kt` holds the edge, its `candidateScore`
and the two orders: `linkComparator` ranks links for the seed/growth walk, while the canonical
`linkNameComparator` / `linkKey` is the order the arrangement cache keys a link set by.
`FeedCandidates.kt` grows each group maximally from the best remaining seed, retries links after
every growth, tries one bounded swap and merges groups that meet; a production belongs to exactly
one band. `BandSearch.kt` arranges a group into the shortest window that satisfies the plan rates:
`groupNeeds` propagates fractional needs upstream, `FeedWindow` distributes each link's demand over
the built consumer machines, and `arrange` keeps the best plan and passes its machine count as an
upper bound to `window`; it walks the cycles with a DFS that cuts a branch as soon as an uncovered
link can no longer become adjacent, counting the cycle's wrap boundary. `BandPlan` caches its ratio
penalty, and `FeedWindow` uses arrays instead of maps/sets.

A band is bounded by `GROUP_MAX_MACHINES = 10` (9 when it has more than one link, so the row stays
powerable) and `MAX_BAND_RECIPES = 8`; a finished group must fit `MAX_ROW_BELTS = 4` belt rows
(`bandBeltFits(plan)` over `beltRowBudget`). `bandBeltMayFit`/`bandBeltRows` are the capacity-blind
pre-filter and ranking used during growth, while `bandBeltFits(plan)` reads the arranged machine
rates. A multi-recipe band counts only capacity-safe pairs there, so one that would need the
single-recipe over-capacity rescue is rejected and its recipes become their own rows. After the main bands, `DirectFeed.build` runs the same search over the belted rows: it copies
them as whole-machine `FactoryProduction` values, plans them at scale 1, and replaces the plain rows
with the finished bands. A recipe may therefore sit in the main band and in a belted band as two
independent productions, but each value belongs to one row.

A solid row's product belt carries **one lane** (`RowPlacer.productLoad` fills the lane away from
the inserters). `DirectFeed.beltRates` therefore reports the rate each solid output must deliver at
the drawn scale — the target's external rate plus every consumer outside a direct link — and
`blueprintPlan` shrinks the whole plan when that rate exceeds `oneLaneCapacity()`. The rate is
limited by what the built rows can make: a row that cannot even reach the demand is underbuilt (for
example by `MAX_COPIES`), not over capacity. Before shrinking, a material with exactly one consumer
is forced into a direct-feed band: `maximalGroups` sorts `forcedMaterials` links ahead of the score
order, and a forced link that cannot arrange or fit is still dropped. A shared product cannot be
internalized (`FeedCandidates.internalizesSharedOutput`), so it shrinks instead. `planLabel` reports
the covered share and how many copies reach the requested rate.

`RowPlacer` places every row through one `placeRow`: a plain row repeats its production with
touching machines, while a row with links leaves a gap column after each machine that has a
`PlannedLink` and touches everywhere else. `assignRowBelts` gives every belt group its side: a
product faces the consumer that placed the row, an input faces its producer, and a full side pairs
a capacity-safe input into a mixed lane before one crosses to the other side. Only belted
productions feed belts and sinks, so a band's internal material never gets a belt and a band
producer's surplus cannot leave the row; the belted row therefore still covers every consumer
outside the links. `belted` sizes a row by the recipe's single non-fluid result
(`beltOutputPerMachine`), so a multi-output recipe the plan labelled with one of its fluids is still
sized by its solid; the row still gets **fluid lanes** for its machines' fluid ports, including a
machine that takes or gives a fluid while its solid link comes from a neighbor; only the link
material must be solid. `plannedSources` covers a solid only when a row exports it (`beltMaterials`)
or a link reaches the consuming machine (`PlannedRow.internalInputs`); a link that reaches only some
machines leaves the others importing the material, an internal-only recipe result is not a source
for another row, and a sink with no producer row is a layout failure. `RowPlacer` uses the same
`internalInputs` through `fullyInternalInputs` to decide whether a link material still needs an
input lane.

The grouping, stone/solid-fuel and fluid-link rules and their guardrails are in
the KDoc of `DirectFeedModel` (`maxBandMachines`, `FeedNode.ingredients`) and
`game/prototype/RecipePolicy.kt`; the shared-product rule is in `decisions.md`
(2026-11-11).

## Traps

- **Recipe cycles are excluded by the optimizer, not by `buildable`.**
  `game/prototype/RecipePolicy.kt` lists `fish-breeding`, `nutrients-from-fish` and
  `kovarex-enrichment-process` in `unplannedRecipes` and discards them even when one produces the
  target, so a plan can never contain a cycle. `FeedNode.traverse()` keeps a
  visited-node set so a shared producer is expanded once; it does **not** filter the
  closing edge of a cycle (the edge is yielded before the visited check), so an unforeseen
  cycle would still reach `maximalGroups`.
  Evidence: `bannedRecipesAreNeverPlanned`, `nutrientsAreNotBredFromFish` and
  `uranium235IsBuiltByProcessingNotKovarex`. See `unplannedRecipes`
  (`RecipePolicy.kt`).
- **A cycle that contains the target must not make the target importable.** `importableMaterials`
  allows a cycle member to be imported so the LP stays feasible, but the target must be built when
  a recipe produces it. Otherwise the LP imports the target, `Optimizer` reports a `freeSource`
  recipe, and `blueprintPlan` says "no buildable factory". The intermediate limit changes the LP
  row order, which exposed this for `light-oil` on Fulgora. Evidence:
  `theLightOilChemicalPlantFeedsWaterFromTheNorthLane`. See `Optimizer.importableMaterials`.
- **A shared product stays the band's output.** `electronic-circuit` feeds the
  advanced circuit, the processing unit and the flying robot frame. Internalizing
  it into one of them leaves the other two with a placed but unfed input belt.
  `FeedCandidates.internalizesSharedOutput` rejects that growth, and
  `FeedLink.candidateScore` penalizes outside consumers and producer overfeed.
  Test the guard on the group **without** the candidate: once the candidate is in
  the group its producer counts as internal and the guard always returns false, so
  the swap path must pass `group.filterNot { it == weakest }`. Evidence:
  `theReportedUtilityScienceUrlsKeepCircuitAsABandOutput`,
  `aSharedProductCannotBeInternalizedWhileAnotherConsumerNeedsIt`. See
  `decisions.md` (2026-11-11).
- **A band must not need a belt for a material it produces.** The symptom is a row
  that belts a material its own machines make; `BandSearch.hasInBandBeltInput`
  rejects that finished window. Growth may pass through a partial arrangement
  (`arrange(..., requireComplete = false)`) so a later link can complete it; only
  the finished group is checked. Evidence: the reported chemical-science URL keeps
  cable entirely out of the band;
  `theReportedChemicalScienceUrlKeepsCableOutOfTheBand`,
  `aCircuitChainWithSharedConsumersBuildsTheTargetOnce`. See
  `BandSearch.hasInBandBeltInput`.
- **A recipe result is not automatically a source.** `plannedSources` must read
  `beltMaterials` plus the **consuming machine's** link, not the row's whole link
  set: a link only covers the machine it reaches, so a partially linked material
  still imports the rate of the machines that take it from a belt. An
  internal-only result is not produced for another row. `DeliveryPreparation`
  reports a sink with no producer row so a missing feed cannot stay silent.
  Evidence: `aMaterialOnlyLinkedInsideABandIsASourceForAnotherRow`,
  `aPartialLinkStillImportsTheMaterialItsOtherMachinesNeed`. See `decisions.md`
  (2026-11-06, 2026-11-11).
- **`MAX_BAND_RECIPES` is a recipe cap, not the window length.** `arrangeBest`
  must search count vectors to `maxBandMachines(links.size)`; using
  `MAX_BAND_RECIPES` there silently caps a multi-link band at eight machines.
  The growth pre-filter must likewise use the propagated `groupNeeds`, not
  `ceil(production.amount * scale)`: the plan count includes consumers a band
  internalizes and can reject a feasible group. The reported utility-science
  four-recipe chain sums to ten plan machines but nine propagated ones.
  Evidence: `theGrowthBudgetUsesPropagatedNeedsInsteadOfThePlanMachineCounts`.
  See `maxBandMachines` (`DirectFeedModel.kt`).
- **A belted row can be re-banded, but only as a copy.** `DirectFeed.beltedBands`
  copies the belted `FactoryProduction` with `amount = machines` and plans at
  scale 1; the copies must differ by `amount`, or `nodeCache` and the `assigned`
  set treat them as the main band's production. Evidence:
  `theReportedUtilityScienceUrlCompacts`. See `DirectFeedContext.beltedBands`.
- **A production belongs to exactly one band.** The seed loop only picks an
  unassigned link, but the pool keeps links that touch a production another seed
  already assigned, and `growGroup` could add one. The same
  flying-robot-frame/utility-science production then sat in two bands: the first
  band internalized battery/electric-engine/engine/pipe, the second imported
  them, and the reported utility-science plan imported battery,
  electric-engine-unit, LDS and processing-unit while building them. Skip a
  growth candidate whose productions include an assigned one outside the group.
  Evidence: `assertNoPlannedMaterialIsImported` on
  `theReportedUtilityScienceUrlCompacts`,
  `theReportedUtilityScienceUrlsKeepCircuitAsABandOutput`. See `FeedCandidates` (the
  `assigned` set).
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
  elsewhere, heavy oil on Fulgora and ammonia on Aquilo. Evidence:
  `vulcanusStoneIsImported`, `rocketFuelUsesTheLightOilSolidFuelRecipe`,
  `solidFuelUsesTheSurfaceRecipe` and `aquiloSolidFuelUsesAmmonia`. See
  `DirectFeedContext.producers` and `RecipePolicy.kt`.
- **A multi-output recipe drains only through its machine's horizontal fluid ports.**
  `buildable` accepts one solid result plus any number of fluid results, so `quantum-processor`
  (one item plus `fluoroketone-hot`) is built for targets such as `railgun`. The electromagnetic
  plant's only fluid outputs are on side edges, and `RowFluidLane` creates lanes only on
  north/south edges, so no drain is placed for the hot fluoroketone and the machines stall. This
  is a known limitation; the intended fix is to flip every second electromagnetic plant so
  adjacent output ports meet and act as a pipe, then drain the hot fluid.
- **Phase one of `LinearProgram` is numerically sensitive.** The `d < -EPS` test on
  the most negative right-hand side is not interchangeable with `d >= 0.0`: the
  zero test pivots in tiny-negative rows, and the optimizer then reports
  "no producer for …"/empty plans while every small `LinearProgramTest` case still
  passes. Keep the `-EPS` form when touching `makeFeasible`, and check the optimizer
  suites (`OptimizerMachineTest`, the blueprint integration suites), not just the LP
  unit test.
- **A product belt carries one lane, and its capacity is judged by demand.** A row's
  `productLoad` fills only the lane away from its inserters, so the product belt can
  deliver `oneLaneCapacity()`. `blueprintPlan` compares the **demand** (`beltRates`:
  target rate plus non-linked consumer rate), not `rowOutputs`: an overproducing machine
  is throttled by its belt, so whole-machine output above the lane is not a failure.
  A row that cannot even make the demand is underbuilt (for example by `MAX_COPIES`),
  not over capacity. When a delivery is over, force a single-consumer link first; a
  shared product or the target shrinks the whole plan instead of thinning one row.
  Evidence: `aSolidTargetShrinksToTheProductLane`,
  `aSingleMachineOverOneLaneDoesNotShrinkBelowItsDemand`,
  `anOverCapacityIntermediateIsDirectFedInsteadOfShrinking`.
- **A source rate is already the whole-machine rate.** `plannedSources` sums one entry
  per built machine, so it must not multiply by the plan scale again; a shrunk plan
  would otherwise report its imports twice over. Evidence:
  `aSolidTargetShrinksToTheProductLane` (its copper plate source).
- **An import-only target must be refused, not laid out.** The optimizer models a target no
  recipe produces as a `Category.IMPORT` pseudo source. `blueprintPlan` must return
  `BlueprintPlanResult.Unavailable` for it: the target production is seeded into
  `DirectFeedContext.included`, so `DirectFeed.targetRow` would place `Factory.NO_FACTORY`
  ("infinity-chest") with the material as its recipe — a `Planned` blueprint with no
  `infinity_settings` and no error. `forSurface` deliberately keeps the dump-wide `materials`
  list, so the picker or a pasted URL may name a material the surface cannot make; the
  planner, not the recipe filter, is the guard. Evidence: `anImportTargetIsUnavailable`,
  `anImportedIngredientDoesNotMakeTheTargetUnavailable`, `anUnavailablePlanRendersNoPanel`.
  See `decisions.md` (2026-11-13).
