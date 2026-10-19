---
task: 20261018-direct-insertion-recipe-groups-y5og
title: "Direct-insertion recipe groups"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/direct-insertion-groups
issue: -
completed: 2026-10-18
---

## Objective

Lay out recipes whose intermediate is **direct-inserted** as one interleaved band
("group") instead of two separate rows joined by a routed belt: e.g.
electronic-circuit + copper-cable. The band is routed like a single recipe — its
external inputs and outputs are the union of its members. Sub-recipes are
recalculated per consumer first, so a material with several consumers (copper
cable for advanced-circuit + electronic-circuit) is split into whole machines per
consumer.

## Acceptance criteria

- [x] A pair whose producer:consumer machine ratio is a small integer ratio is
      built as one interleaved band with machine-to-machine inserters and no belt
      for the intermediate (AM3 electronic-circuit: 3 cable + 2 EC per cycle).
      CLI-verified on `#/15/electronic-circuit/CFSXbr/1/1`: one interleaved band
      of 9 cable + 6 EC machines and no `copper-cable` belt.
- [x] 3-recipe groups (chain, star, palindrome, repetitions) are arranged
      (`DirectFeedTest.aWindowIsCutSoThatNoConsumerStarves`: the 1:2:1 palindrome).
- [x] Always on when the ratio fits; otherwise the current belt layout is kept
      unchanged (no regression).
- [x] Ratio rule: candidate `p:q` with `p+q <= 6`, accepted when the needed ratio
      `r` satisfies `p/q in [r/1.05, r*1.25]`; smallest `p+q`, then closest to `r`.
- [x] Sub-recipes are allocated per consumer in **whole machines**; a recipe may
      appear in more than one band (one per consumer), each band with exactly one
      output role.
- [x] Allocation is `ceil` per consumer and the sum is built (over-supply rather
      than starving a consumer).
- [x] Interface inserters: as many as the flow needs (no rejection because the
      rate cannot be reached); `direction` is the pickup side.
- [x] A band holds at most `GROUP_MAX_MACHINES = 18` machines (`k` reduced when
      the cycle would exceed it; fluids are not a grouping criterion).
- [ ] **Deviation:** a recipe that takes a fluid is not grouped at all (neither as
      producer nor consumer) until the fluid feed is reworked — a band would put
      its port between two machines, where `feedFluids` cannot reach it. Verified
      green: the acid/pipe integration invariants pass again with this rule.
- [x] Every existing `BlueprintIntegrationTest` invariant stays green (no
      overlap, no dangling inserter, connected underground belts, poles cover and
      connect, compaction / `validateLayout`).
- [x] `./gradlew spotlessApply check` passes; the two electronic-circuit URLs below
      are in the tests as fixtures (`BlueprintIntegrationTest` band invariants).

## Current state

- **Increment 1 is implemented and green** (2026-10-18). Branch
  `bjrke/direct-insertion-groups`; nothing committed yet at the time of writing.
- Files: `DirectFeed.kt` (new, commonMain), `BlueprintPlan.kt` (`MAX_COPIES`,
  `plannedMachines`, `PlannedBand`/`PlannedLink`, `BlueprintPlan.bands`,
  `directFeed` wiring, `PlannedRecipe.directInsert` removed), `BlueprintLayout.kt`
  (band refactor: `Row` = machines + interfaces, `rows` by allocation index,
  `placeBand`, per-machine fluids/inserters), `DirectFeedTest` (new, 8 tests),
  `BlueprintLayoutTest` (band test + helper), `BlueprintPlanTest`,
  `BlueprintExportTest`, `BlueprintIntegrationTest`.
- `./gradlew spotlessApply check` green (jvmTest 316 tests + jsBrowserTest).
- CLI-verified: both electronic-circuit URLs build one band with no cable belt;
  `advanced-circuit` and the URL fixture still replay without feed failures.

## Verified findings

- `PlannedRecipe.directInsert` is **write-only**: computed in
  `BlueprintPlan.kt:259` and asserted in tests, but
  `grep -c directInsert BlueprintLayout.kt` = **0** and
  `git log -S"directInsert" -- src/commonMain` lists only `285587d` (the commit
  that added it). The layout never direct-inserts today.
- Ratios come from per-machine rates (recipe + factory), independent of the
  requested rate. `./gradlew -q run --args='<url> --listing'`:
  - `#/15/electronic-circuit/CFSXbr/1/1` (AM3): 0.6 cable : 0.4 EC = **3:2**
    (cable 5/s per machine, EC 7.5/s per machine); scaled x15 -> 9 + 6 machines.
  - `#/15/electronic-circuit/CFSXbmr/1/1` (EM plant): 0.167 : 0.167 = **1:1**
    (12/s both); scaled x15 -> 2.5 + 2.5 machines (ceil -> 3 + 3).
  - `advanced-circuit`: cable 0.444, EC 0.222, AC 2.0 machines; cable output
    5.333/s is consumed **50/50** by EC (2.667/s) and AC (2.667/s) -> the split
    case. cable->AC per machine is 12:1.333 = **9:1** -> not groupable.
  - `engine-unit` (base game): gear 0.4 : pipe 0.8 : engine 8.0 -> 1:20 / 1:10
    links -> correctly **rejected**, keeps the belt layout.
- Current layout: one `Row` per recipe (`Row.recipe`, `copies`, `stride`, lane
  rows), `rows: MutableMap<Recipe, Row>`; the intermediate is a routed belt
  trunk with a splitter per further consumer. Verified with
  `./gradlew -q run --args='<url> --layout'` (see `tmp/plan-ec-*.out`).
- **Inserter direction = pickup side**, the drop is the opposite tile:
  `placeRow`'s lane feeder sits above the machine and is `NORTH` ("reaches the
  near lane one tile up"); `validateLayout` reads the source at `+direction` and
  the drop at `-direction`; the chest connector reproduces pasted blueprints as
  `UG-in · inserter(EAST) · chest · inserter(WEST, mirror) · UG-out`. A
  machine-to-machine inserter between A (west) and B (east) therefore sits in the
  gap column with `direction = WEST`.
- `inserterThroughput = rotationSpeed * 60 * handSize`: fast **2.4/s**, bulk
  **4.8/s** (default hand 2), stack **14.4/s** (hand 6). A machine-to-machine
  interface has at most the machine height (3 for 3x3) minus port/reserved tiles:
  AM3 cable->EC needs 3 inserters at 5/s and 2 at 2.5/s -> fits; EM plant
  cable->EC is 12/s -> 5 fast / 3 bulk inserters -> only 1 stack inserter fits.
- Rounding today: `copiesOf` = `ceil(machines).coerceIn(1, MAX_COPIES)` with
  `MAX_COPIES = 9`, and `MAX_COPIES` is per recipe (at `--normalize=15` the AM3
  case lands exactly on 9 cable / 6 EC).
- `validateLayout` accepts a `MachineEntity` as inserter source *and* drop, so
  machine-to-machine insertion is not rejected by compaction.

## Hypotheses / unverified

- Poles may all fit outside a dense band (above/below) and still cover + connect;
  needs verification on the 15-machine AM3 band.
- The compaction passes leave a band intact (they work on entities), but the
  `Row` bookkeeping is not re-derived after `compact()` — only `placePoles`
  runs later, so this should be safe.
- Behaviour change if `jsTest`/`jsMain` expectations embed generated blueprint
  geometry (to check before switching anything on).

## Decisions

- **Always on** when the ratio fits; belt fallback otherwise (user, 2026-10-18).
- **Increment 1 = pairs + 3-recipe groups** (chain, star, palindrome,
  repetitions), found by a bounded cycle search (user, 2026-10-18).
- **Ratio rule** (user confirmed): candidate `p:q` with `p+q <= 6`; accept when
  `r/1.05 <= p/q <= r*1.25` (<=5% under-supply, <=25% over-supply); pick the
  smallest `p+q`, then the closest to `r`. Verified against the user's examples:
  0.8:1 -> 1:1 OK, 1.05:1 -> 1:1 OK, 1.1:1 -> rejected.
- **Whole machines, never fractions**: the ratio is taken from the scaled
  (whole) machine counts; complete machines are built (user, 2026-10-18).
- **Interface inserters = as many as fit**; do not reject a group because the
  interface cannot carry the full rate — direct machine-to-machine is one handoff
  instead of machine -> belt -> machine and is never worse (user, 2026-10-18).
- **Sub-recipes are recalculated per consumer** (user, 2026-10-18): the
  producer's machines are allocated to its consumers in whole machines, so a
  recipe can appear in several bands (one per consumer).
- **One band per (producer share, consumer)**, each band with exactly one output
  role — a direct-fed intermediate never leaves the band, a belted one is the
  band's product (user, 2026-10-18). `rows`/`producerOf` are therefore keyed by
  an *allocation* (recipe + whole machines + consumer), not by `Recipe`, and
  `blueprintPlan` emits one `PlannedRecipe` per allocation.
- **Allocation rounding**: `ceil` per consumer, build the sum; every consumer is
  served even when that exceeds the optimizer's total (user, 2026-10-18).
- **Order of the rules**: a grouped link's **cycle pattern** `p:q` comes from the
  *rates* (within the tolerance, smallest `p+q`, then closest) — that is what
  `0.8:1 -> 1:1` means. The band is that pattern repeated and cut to the smallest
  window that covers **every** member's whole plan count, so an intermediate's
  built count can exceed its own plan ceiling (the "recalculate the sub-recipe"
  step). A pattern outside the tolerance, or `p+q > 6` (engine-unit `1:20`), or one
  whose cycle cannot be arranged, means **no group** (belt fallback). Belted
  allocations keep their plan counts.
- **Do not gate group membership on fluids** (user, 2026-10-18): the fluid feed
  is known to be improper and will be reworked in a separate session, so this
  task leaves the fluid code untouched. A grouped recipe that takes a fluid may
  therefore report a `no fluid port` failure — that is a known, accepted
  limitation, not a reason to reject the group.
- **The band is a *window* of the periodic pattern** (user correction,
  2026-10-18): the pattern is the repeat unit from the rates, the band is that
  pattern repeated and cut at both ends to the smallest window covering every
  member's need. `A:B:C:B:A` (2:2:1, a finite band) and `A:B:C:B:A:B:C...`
  (1:2:1, pattern `A B C B`, ends cut) are therefore two different bands.
- **A cut must never starve a consumer** (user, 2026-10-18): the window is grown
  to the smallest length at which every consumer keeps enough producers — for a
  1:2:1 need that is `A B C B A` (2:2:1), the extra producer only over-supplying.
  That is how the user's palindrome is derived. Still bounded by
  `GROUP_MAX_MACHINES`: if even the smallest safe window exceeds it, the link
  stays belted.
- **Band cap: `GROUP_MAX_MACHINES = 18`** (user, 2026-10-18): the consumer's
  allocation keeps `MAX_COPIES = 9`, the producer share follows the ratio, and
  `k` is reduced when `n_p + n_c` would exceed 18 (the 1:2 case would otherwise
  reach 27). Line breaking a long band (wrapping it into several rows) and the
  fluid rework are explicitly deferred to later sessions.

## Attempts and outcomes

- Reproduced both electronic-circuit URLs and the `advanced-circuit` and
  `engine-unit` plans with the CLI (`--listing`, `--layout`) to pin the numbers
  above; outputs kept in `tmp/plan-ec-*.out`, `tmp/plan-list-*.out`.

## Failed approaches (do not repeat without new evidence)

- Deciding eligibility from machine **counts** alone: `BlueprintLayoutTest`'s
  synthetic plan has 1 smelter + 1 gear machine (ratio 1:1 by count) but the
  rates do not balance (0.5/s vs 1.0/s). Eligibility must use the per-machine
  rates.

## Design detail

### Allocation (whole machines, per consumer)

- Start from the plan's whole machine count per recipe (`ceil(machines * scale)`,
  capped) — that is each allocation's *need*. The target's count is its plan
  count. A *belted* allocation of `m` for a consumer needing `n_c` gets
  `ceil(n_c * rate_c / rate_p)` machines (summed when several belted consumers
  share the band).
- A *grouped* link does not use that ceiling: its counts are its cycle pattern
  (`k` repetitions, `k` covering every member's need), which is the point of
  "recalculating the sub-recipe" — so a grouped producer's count can differ from
  its own plan ceiling, while the consumer's need is always covered.
- Per material, the **belted** allocations merge back into one band (same
  recipe, no direct feed) so the existing splitter trunk keeps working;
  **grouped** allocations stay separate bands. So `producerOf: Material ->
  Allocation` stays a single-valued lookup.
- Worked `advanced-circuit` at `--normalize=15` (AC 30 -> capped 9, EC 3.33 -> 4,
  cable 6.67 -> 7): EC group = 4 cable + 4 EC; AC's cable = `ceil(9 * 1.333/12)`
  = 1 cable machine, belted; total 5 cable machines (< the 7 scaled), and every
  consumer is served.

### Band geometry (tile rules)

- All members share the footprint (v1), so one `factoryTop`/`factoryBottom`;
  `stride = width + 1`, the gap column carries the interface inserters.
- Interface inserters: in the gap column, rows `factoryTop..factoryBottom` minus
  the **port rows of either neighbour**, top-down, `min(needed, free)`; material =
  the internal one; `direction = WEST` (pickup side). No rejection on rate.
- External solids: `solids` = union over members minus internal materials, sorted;
  1 -> near lane (`factoryTop - 2`), 2 -> near + far (`factoryTop - 3`), 3 -> also
  below (`factoryBottom + 3`), more -> side-load the below lane at the band's left
  (the existing rule). Per machine, only *its* ingredients get inserters, on that
  material's lane row (near = short, far/below = long), counts from
  `insertersFor(machine, listOf(material))`.
- Keep the current "no inserter above the machine's last column" rule per machine:
  that tile stays free and is the pole spot for the band (one per machine, the
  pole pass picks them) — this is why the dense band should still cover + connect.
- Product: only machines with an **external** output get a product inserter
  (`factoryBottom + 1`, `NORTH`) onto the output lane (`factoryBottom + 2`); the
  output belt is laid when the band has an external product; `placeTargetOutput`
  and the sink/delivery logic are unchanged (they use `row.right`/`row.left`).
- Internal materials never appear in `solids`, so no lane, sink, delivery or belt
  is created for them.

### Multi-link groups (chain, star, palindrome, repetition)

- **Graph.** After the allocation every node (an allocation) has exactly one
  consumer, so a group is a **tree rooted at its final consumer**; two links join
  a group when they share a node — a chain via the middle node (`A->B`, `B->C`),
  a star via the hub (`A->C`, `B->C`). A material with two consumers was already
  split into two allocations, so a diamond is two groups, never one.
- **Pattern vs band (corrected by the user, 2026-10-18).** `A:B:C:B:A` (ratio
  2:2:1) and `A:B:C:B:A:B:C...` (ratio 1:2:1, *needs to cycle, ends cut off*) are
  **two different things**, not one shape written two ways: the first is a finite
  band, the second is the repeating pattern seen over more than one cycle. So the
  model is a **window of a periodic arrangement**, not a count vector:
  - the **pattern** is the repeat unit — for the star `A B C B` (1 A : 2 B : 1 C),
    found as a *cycle* by the DFS over the pattern multiset (the wrap edge is a
    real adjacency in the infinite repetition);
  - the **band** is the pattern repeated with both ends cut to the smallest window
    that (a) covers every member's plan need and (b) does not starve a consumer at
    a cut: a cut on a producer only over-supplies, a cut on a consumer takes away
    one of its two feeds. `A B C B` alone starves its last `B` (it loses the `A`),
    so the finite band for a 1:2:1 need is `A B C B A` (2:2:1) — its cuts land on
    `A`, and it is exactly the user's palindrome.
- **Search.** DFS over the pattern multiset (bound <= 8 machines, <= 3 links;
  8! = 40320 worst case, still cheap), pruning as soon as a producer can no longer
  get a consumer neighbour. Each complete pattern is validated **cyclically**
  (adjacency + flow of the wrap edge included); the band is then a window of that
  pattern — start offset and length enumerated, smallest first:
  1. **adjacency** — every producer of a link has at least one adjacent consumer
     of that link and vice versa (the wrap edge of the pattern counts, an edge cut
     by the band window does not);
  2. **flow** — greedy left-to-right across the window: each consumer's unmet
     demand is taken from its left neighbour first, then its right; a consumer
     with a single live neighbour is fine (only ever over-supplied), a consumer
     whose demand cannot be met must not appear in the band; no producer should
     end with unused output (same -5%/+25% band as the ratio rule).
- **Score.** Smallest window length, then fewest total interface inserters, then
  the lexicographically smallest recipe sequence — deterministic, so a layout is
  reproducible.
- **Inserters.** One interface per adjacent producer/consumer pair; count
  `min(ceil(inserterCount(flow)), free gap rows)`, material = the link's material,
  `direction = WEST` when the producer is on the left, `EAST` when it is on the
  right. Same recipe adjacent (e.g. at a junction) needs none.
- **Component selection.** Eligible links are processed deterministically (by
  material name); a link joins the group sharing its node only when the merged
  cycle stays inside the bounds, otherwise the link stays belted and its
  allocation merges back into the belted band of that material.

Worked shapes, all found by the same search:

| local link ratio | pattern (repeat unit) | band for that need |
| --- | --- | --- |
| 1:1 | `A B` | `A B` |
| 2:1 | `A B A` | `A B A` |
| 1:2 | `B A B` | `B A B` (the producer in the middle splits into both) |
| 3:2 | `A B A B A` | the pattern x3 for the 9 cable : 6 EC need |
| star `1 A : 2 B : 1 C` | `A B C B` | `A B C B A` = **2:2:1** (the user's palindrome; `A B C B` alone starves its last `B`) |
| chain, all links 1:1 | `A B C` | `A B C`, repeated for the need |
| 3:1 / 1:3 | — | **none** — a machine has at most 2 neighbours, so the third producer (or consumer) can never be reached; belt fallback |

Real data checked with the CLI (`fast-transport-belt --listing`, 1/s): the belt
foundry makes 23.8/s and the fast-belt foundry eats 8/s, so the needed pattern is
`1:3` — geometrically impossible, and `1:1` would be 198% over-supply, outside the
tolerance. Both ways it is correctly rejected and stays belted, even though a
belt has no problem with it.

## Implementation design (code level, verified against the sources 2026-10-18)

### Types

`BlueprintPlan.kt` (the shared home, so `MAX_COPIES` has one definition):

- `internal const val MAX_COPIES = 9` (moved out of `BlueprintLayout.kt`, which keeps using it).
- `internal fun plannedMachines(production, scale): Int` = `ceil(amount * scale - 1e-6).toInt().coerceIn(1, MAX_COPIES)`.
- `data class PlannedRecipe(production, machines: Double)` — `directInsert` **removed**.
- `data class PlannedBand(val machines: List<Int>, val links: List<PlannedLink>)` — `machines` are
  indices into `BlueprintPlan.recipes`, left to right.
- `data class PlannedLink(val gap: Int, val material: Material, val flow: Double, val producerIsLeft: Boolean)`.
- `BlueprintPlan(target, recipes, bands = emptyList(), sources, scale, label)`.

### `DirectFeed.kt` (new, commonMain, pure)

- `internal fun inputPerMachine(production, material)` = `speed * items / time` (the layout's
  `perMachineRate`, moved here).
- `internal fun outputPerMachine(production, material)` =
  `speed * (1 + productivity) * items / time` (productivity matters for the ratio!).
- `cycleRatio(r)`: smallest `p+q <= 6` (then closest, relative) with `r/1.05 <= p/q <= r*1.25`.
- `directFeed(target, included, scale, isFluid): DirectFeed(recipes, bands)`, steps:
  1. `need(recipe)` = `plannedMachines` (the plan's whole count).
  2. candidates: each solid in-plan input `m` of a consumer with a single in-plan producer, where
     `cycleRatio(inputPerMachine(C,m) / outputPerMachine(P,m))` exists.
  3. components of the candidate graph (shared recipes), dropped when > 3 links.
  4. per component: root = the node with no in-group consumer; needs: root = plan need, others =
     sum over their in-group consumers of `ceil(need * r)`; search the cycle (DFS over the group's
     recipes, length 2..8, each node present, counts gcd == 1, every link's *reduced count* ratio
     `p+q <= 6` and within tolerance) and then the window (rotation offset, length from
     `k * cycleLength` up to `GROUP_MAX_MACHINES`, `k = max ceil(need/count)`) validated by a
     left-to-right greedy flow: no consumer starves, every producer has a consumer neighbour;
     score = smallest window, then fewest interface inserters, then lexicographic.
  5. allocations: the target (or any recipe) gets a *belted* allocation of `need`; a group member
     gets `k * cycleCount` machines; for a material with belted consumers the belted allocation is
     `max(sum over belted consumers of ceil(need * r), planNeed - groupShare)` so the plan's total is
     respected and nobody starves. The bands carry the windows and the per-gap flows.
- Determinism: recipes and materials sorted by name everywhere, windows by offset ascending.

### `BlueprintLayout.kt`

- `Row` becomes a band: `machines: List<PlacedMachine(allocation: Int, left: Int)>`, `interfaces:
  List<PlannedLink>`, `allocation` for a single-kind row; `copies = machines.size`.
- `rows: MutableMap<Int, Row>` keyed by the allocation index; `producerOf: MutableMap<Material, Int>`
  holds **belted allocations only** (so an internal material gets no lane, sink, delivery or belt).
- `placeRows()` walks bands + standalone allocations ordered by `(depth, band label, recipe name)`;
  `placeBand(band, bandTop)` places the machines with `stride = width + 1` (the gap column) and
  `placeRow(allocation, bandTop)` is the old single-recipe body with `copies` machines.
- Per machine: lane inserters for *its own* external solids (near = short, far = long, the third
  solid's long inserter facing SOUTH at `factoryBottom + 1`), product inserter only on machines
  whose product is not internal to the band; keep the "no inserter above the machine's last column"
  pole tile.
- Interface inserters: `x = left + gap * stride + width`, rows `factoryTop..factoryBottom` minus the
  port rows of either neighbour (only when that machine uses fluids), count
  `min(max(1, ceil(inserterCount(flow))), freeRows)`, `direction = WEST` iff `producerIsLeft`.
- `feedFluids`/`fluidEdge`/`placeTargetFluidOutput` iterate `row.machines` (fluid behaviour is
  otherwise untouched, per the decision).
- `computeDepth` uses a material → producer map that **includes** the internal links (ordering only);
  `laneDirection`/`outletX`/`prepareSolidDeliveries` use the belted `producerOf`.
- `copiesOf` stays as-is (ceil + cap) so `BlueprintLayoutTest`'s `machines = 2.5 -> 3` keeps working;
  band members bypass it via the explicit machine list.

### Verified facts this design rests on

- `validateLayout` accepts a `MachineEntity` as inserter source *and* drop, and only compares
  materials when the neighbour is a `TransportEntity` → a machine-to-machine inserter validates.
- `direction` = pickup side: `WEST` picks the west neighbour (`topLeft + (-1,0)`), drops east.
- The gap column of machine *i* is exactly `left + i*stride + width`, i.e. machine *i*'s east port
  tile and machine *i+1*'s west port tile — hence the port-row exclusion.
- AM3 cable/EC are both 3x3 (same footprint), so one `factoryTop`/`factoryBottom` works.

### Tests to write

- `DirectFeedTest` (commonTest): the ratio table incl. the 0.8 / 1.05 / 1.1 boundaries and 1:20;
  the patterns `A B`, `A B A`, `B A B`, `A B A B A`, the star 1:2:1 window `A B C B A` (2:2:1), the
  chain `A B C`, and 3:1 / 1:3 → no band; the split (group share + belted remainder) and the
  18-machine cap.
- `BlueprintPlanTest`: assert the band/links for a grouped plan instead of `directInsert`.
- `BlueprintLayoutTest`: add a band test (interleaved machines, interface inserters, no belt for the
  internal material); `planned(...)`'s `directInsert` parameter is dropped.
- `BlueprintExportTest`: drop `directInsert = emptySet()`.
- `BlueprintIntegrationTest` (jvmTest): the real AM3 and EM-plant electronic-circuit URLs as fixtures
  with the existing invariants, asserting the band shape.

### Verification targets

- `--layout` on `#/15/electronic-circuit/CFSXbr/1/1`: one interleaved band, **no copper-cable belt**;
  `--layout` on `#/15/electronic-circuit/CFSXbmr/1/1`: 1:1 band.
- `advanced-circuit --listing/--layout`: 4 cable + 4 EC band and a separate 1-machine cable row for
  the AC.
- `engine-unit` and `fast-transport-belt`: byte-identical layout to before (belt fallback).

## Open questions

- none open. (Earlier: allocation location, over-build, two outputs per band,
  fluids, band size — all decided above. The whole-machine step and the
  allocation live in the plan, `RowLayout` keeps only geometry.)
- Deferred, not blocking: the fluid feed rework (user, separate session) and line
  breaking a band wider than the 18-machine cap (separate session).

## Remaining tasks

- [x] `DirectFeed.kt` + `DirectFeedTest`: the ratio rule, link candidates, groups, the cycle/window
      search, the split and the band cap (see the code-level design above).
- [x] `BlueprintPlan.kt`: `MAX_COPIES`, `plannedMachines`, `PlannedRecipe` without `directInsert`,
      `PlannedBand`/`PlannedLink`, `BlueprintPlan.bands`, wire `directFeed` (allocations + sources).
- [x] `BlueprintLayout.kt`: `Row` → machines + interfaces, `rows`/`producerOf` by allocation index,
      `placeBand`, per-machine lane/product inserters, the interface inserters, fluids per machine,
      `computeDepth`.
- [x] Update `BlueprintPlanTest`, `BlueprintLayoutTest`, `BlueprintExportTest`; add the two URLs to
      `BlueprintIntegrationTest`.
- [x] `./gradlew jvmTest` while iterating, `./gradlew spotlessApply check` at the end, CLI
      `--layout`/`--listing` before/after on both URLs, `advanced-circuit`, `engine-unit`,
      `fast-transport-belt`.
- [x] `decisions.md` entry (ratio rule + always-on + per-consumer allocation + band cap).
- [x] Record the two deferred follow-ups for later sessions: the fluid feed rework (the user has a
      design in mind) and line breaking a band that is too wide.
- [x] Commit on `bjrke/direct-insertion-groups` (no push) and `agents complete` this task.

## Tests and verification

- `./gradlew spotlessApply check` green: jvmTest 316 tests (incl. the new `DirectFeedTest`), the
  `jvmTest` integration invariants and `jsBrowserTest`.
- Three failures were diagnosed on the way here and are resolved:
  - `realFluidNetworksAreConnected` (processing-unit / utility-science-pack): a band around a
    fluid-consuming recipe pushed its port into a gap column the fluid feed cannot reach, splitting
    the acid network. Fixed by not grouping fluid-touching recipes at all (documented deviation, to
    be revisited with the fluid feed rework).
  - `realBlueprintsStretchUndergroundRuns` (advanced-circuit): the check matched a *foreign* tunnel
    that shares the same column and reported a phantom "can still grow back". The output end is now
    matched by material as the layout does (`undergroundOutputOf`), which makes the test correct
    rather than weaker.
  - `advancedCircuitUrlNeedsNoUndergroundBelts` → replaced by
    `advancedCircuitUrlOnlyTunnelsForItsCableFeed`: the band puts the cable rows in the feed
    corridor west of the machines, so the cable feed needs two crossing tunnels there. The test now
    records that the replay has no failed feed and that no other material needs a tunnel.
- Outcome verification (done, CLI `--layout`):
  - `#/15/electronic-circuit/CFSXbr/1/1`: one band `111<222`, 9 cable + 6 EC machines, no
    `copper-cable` belt.
  - `#/15/electronic-circuit/CFSXbmr/1/1`: 1:1 band (3 cable + 3 EC) with interface inserters.
  - `advanced-circuit` + its URL fixture: no feed failures (`BlueprintIntegrationTest`).
  - `engine-unit`, `fast-transport-belt`: unchanged (belt fallback, byte-identical paths).

## Next action

- Commit the work on `bjrke/direct-insertion-groups` (headline ≤ 50 chars, no push), then
  `agents complete 20261018-direct-insertion-recipe-groups-y5og`. Increment 2 candidates (separate
  sessions): the fluid feed rework (user design pending) and line breaking a band wider than the
  18-machine cap.
