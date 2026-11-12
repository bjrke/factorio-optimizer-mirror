# Decisions

Significant technical and process decisions and their rationale, newest first.

An entry here holds a constraint that **no single owner file can carry**: violating it would show
up somewhere other than the file that documents the rule. Everything else lives where it acts. The
mechanism and the reason for a value belong in the KDoc of the constant, function or class it
governs, because that is what the next editor reads; the workflow belongs in `conventions.md`, the
shape of the pipeline in `architecture.md`, and the measurements, reported URLs and before/after
sizes in the KDoc of the choice they justify.

An entry states the constraint, one sentence of why and the guardrail that must survive, then
points at the owning symbol instead of restating it. No constant values, no layout dimensions, no
test-function names. Keep an entry under ~10 lines and prepend a new one. A change that supersedes
an entry rewrites or deletes it — never add a "supersedes" note.

## 2026-11-11 — A band keeps a shared product on the belt

**Decision.** Growth may not internalize a product that leaves a band over a belt while another
plan consumer still needs it (`FeedCandidates.internalizesSharedOutput`); `candidateScore`
penalizes every outside consumer and producer overfeed, and `DirectFeedContext.consumerScope`
carries the main plan's productions into the belted pass.

**Guardrail.** Keep a shared material such as `electronic-circuit` as the band's output: swallowing
the cable/circuit pair into an advanced-circuit/processing-unit band strands the other consumers.
Never count an internal-only recipe result as produced — a row's sources are its `beltMaterials`
plus the links that reach the consuming machine (`PlannedRow.internalInputs`), so a partially
linked row still imports the material for its other machines.

**See.** `blueprint/planning/band/{FeedCandidates,FeedLink,DirectFeedModel}.kt`,
`blueprint/planning/BlueprintSelection.kt`.

## 2026-11-06 — Direct feed is a production-node graph with feed links

**Decision.** `FeedNode` is one production with lazy `ingredients`/`consumers` and `FeedLink` the
edge `(producer, consumer, material)` with per-machine rates, so a production and an edge exist
once however many paths reach them.

**Guardrail.** Keep exactly one node per production and one edge per triple. A `consumer: FeedLink?`
parent or a path-local copy expands and sizes a shared producer once per path. Fluids are never
links, and a multi-producer material stays out of the direct feed by an explicit policy — stone on
every surface, one solid-fuel recipe per surface — never by picking one of its links.

**See.** `blueprint/planning/band/{DirectFeedModel,FeedLink,DirectFeed,FeedCandidates}.kt`,
`game/prototype/RecipePolicy.kt`.

## 2026-11-06 — Sources follow the placed rows

**Decision.** `BlueprintPlan.sources` comes from `plannedSources(rows, logistics, scale)` — the
rows actually placed — and a synthetic import is a `Category.IMPORT` recipe, so `buildable`
decides from the recipe rather than from the machine.

**Guardrail.** Never derive the imports from `DirectFeedContext.included`: it keeps productions
the planner drops, so the reported imports would disagree with the layout. Keep one definition —
`plannedSources` for the plan, `sourceMaterials` for `DeliveryPreparation`.

**See.** `blueprint/planning/{BlueprintSelection,plan/BlueprintPlan}.kt`,
`blueprint/placing/routing/DeliveryPreparation.kt`.

## 2026-10-27 — The fluid port table and the lanes read one assignment

**Decision.** `prototypeFluidPorts` is the single assignment of a machine's fluids to port tiles:
inputs first in recipe order, then outputs, already in the placed orientation. The lane side, the
reserved inserter columns and the routed fallback all read that assignment.

**Guardrail.** Never re-rotate or coordinate-sort the table, and never infer an input from
"north/west": a wrong box shows up as one fluid that never arrives while the other works. A port
the table assigns is never taken by the routed fallback, and a port never costs an input inserter.

**See.** `blueprint/planning/plan/BlueprintMachine.kt` (`prototypeFluidPorts`),
`blueprint/planning/row/{RowFluidLane,FluidLane}.kt`,
`blueprint/placing/plumbing/{FluidLaneFeed,FluidFeeder}.kt`.

## 2026-10-24 — The row is the occurrence, not the recipe

**Decision.** A plan occurrence is a plain-data `PlannedRow`: `machines` left to right plus the
band `links`. Band membership and order are the `rows`/`machines` lists themselves, each machine a
bare `FactoryProduction`, and `BlueprintPlan` keeps no derived recipe list. Producer lookup scans
`plan.rows` and compares by value.

**Guardrail.** Never key a `Map` or `Set` by a production or a row, and never add an allocation
index: structurally identical rows are separate occurrences, must compare equal and stay
interchangeable, or two copies of one recipe collapse into one.

**See.** `blueprint/planning/plan/BlueprintPlan.kt`, `blueprint/planning/row/{Row,RowTopology}.kt`.

## 2026-10-18 — Belt-fed productions are sized by plan demand, one belt source per material

**Decision.** A belt-fed production is sized by the plan's own rate demand, not by whole consumer
machines (the `belted` KDoc holds the formula), and only when at least one consumer is not
direct-fed.

**Guardrail.** One belt source per material. A band's inserted link never counts as belt output and
a fully inserted recipe gets no belt row, so its surplus cannot leave the row; the belted row still
covers every outside consumer.

**See.** `blueprint/planning/band/DirectFeed.kt` (`belted`),
`blueprint/planning/row/RowPlacer.kt` (`rowShape`),
`blueprint/planning/plan/BlueprintPlan.kt` (`producerOf`).
