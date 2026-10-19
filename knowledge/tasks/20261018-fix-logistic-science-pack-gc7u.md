---
task: 20261018-fix-logistic-science-pack-gc7u
title: "Fix logistic-science-pack: duplicate EC/inserter factories with dangling belts, and excess iron-gear-wheels"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/direct-insertion-groups
issue: -
completed: 2026-10-18
---

## Objective

The user reported `#/2.5/logistic-science-pack/CFSXbmr/1/1`: the 1:1 direct feeds
(cable→EC, gear→inserter) are right, but (a) there is an *additional* EC and
inserter factory, each with an output belt that leads nowhere, and (b) the
iron-gear row is far too big — one more gear assembler should suffice, and it
could feed the foundry.

## Acceptance criteria

- [x] The logistic-science-pack plan builds no belt-fed EC/inserter factory whose
      output belt is unconnected (the extra factory disappears; the band's own
      output belt stays connected).
- [x] Iron-gear-wheel machines match the plan's rate need: 1 in the inserter band
      + 1 belt-fed for the foundry (not 4).
- [x] Sizing is by the plan's *rate* demand (`consumption × scale`, like
      `PlannedSource.rate`), not by whole consumer machines; `MAX_COPIES = 9` still
      caps a row.
- [x] Every belted allocation that is placed is the belt source of its material
      (no product belt that no delivery uses) — asserted as a plan invariant, see
      `realBlueprintsPutEveryMaterialOnAtMostOneBelt`.
- [x] Tests for the sizing rule (`aBeltedAllocationCoversThePlansRateNotAWholeConsumerMachine`)
      + the reported URL fixture.
- [x] `./gradlew spotlessApply check` green; CLI-verified on the reported URL.
- [x] Direct feed into the foundry (a band mixing a 3×3 and a 5×5 machine) is
      **not** built: reported to the user as the follow-up question instead.

## Outcome

`DirectFeed.directFeed` now sizes the one belt-fed allocation per recipe by the
plan's own rate:

`max(ceil(Σ consumer.consumption(material) × scale ÷ outputPerMachine) −
bandOutputMachines, need − built)`, capped at `MAX_COPIES`, and only when a consumer
is not direct-fed. `bandOutputMachines` counts the machines a band builds whose
product leaves it on a belt (its output, never an inserted link).

Reported URL after the fix (CLI `--json`): copper-cable 1, electronic-circuit 1,
iron-gear-wheel 2, inserter 1, foundry 1, logistic-science-pack 9 — 220 belts and a
34×53 layout, down from 322 belts and 34×69. The belt tracer shows every chain
picked up by inserters; before the fix exactly two chains were dead
(`electronic-circuit head=(40,24)`, `inserter head=(40,45)` — the two extra
factories' product belts).

## Verified findings

- Reported URL before the fix, `--json`: 9 science, 5 gear, 2 EC, 2 inserter, 1
  cable, 1 foundry. Plan numbers (`--listing`, scale 1; the URL's `2.5` is
  `normalizeTarget`): science 4.8, inserter 0.4, gear 0.533, EC 0.167, cable 0.167,
  transport-belt 0.042 → `need = ceil(amount × 2.5)` = 9 (clamped from 12), 1, 2, 1,
  1, 1.
- Rates (prototype dump + `Factory`): electromagnetic-plant speed 2 with
  `effect_receiver.base_effect.productivity 0.5`; foundry speed 4 + 0.5; AM3 1.25.
  Cable output 12/s, EC output 6/s (input 12 cable/s → ratio 1, the 1:1 band), gear
  and inserter 2.5/s (ratio 1), inserter input 2.5 EC/s (ratio 0.4167 → 1:2, but the
  4×4/3×3 footprints differ so `arrange` refuses), foundry gear input 8/s (ratio 3.2
  → 4:1, refused by the equal-footprint rule).
- The greedy grouping explains the bands: `EC→inserter` is dropped when `arrange`
  fails on the footprint; `gear→inserter` forms its own group; `cable→EC` too;
  `gear→transport-belt` is offered to the gear+inserter group, would make 4 recipes,
  is removed and *never retried*, so it stays belted.
- Old belted sizing `demand += ceil(machines × ratio)` with `machines =
  built[consumer] ?: need[consumer]` gave `ceil(1 × 3.2) = 4` gear machines for the
  foundry and a second inserter row for the 1.875/s the nine science machines take.
- `producerOf` (in `RowLayout.init`) is the single belt source per material, keyed
  by the first allocation producing it; a redundant belt-fed row therefore keeps its
  `placeProduct` belt with no delivery on it — the unconnected belt.
- Belt tracer (`/tmp/opencode/belts.py`, not committed): follows belt chains from
  the exported JSON plus the `--layout` grid, honours loaders (2 tiles wide), long
  inserters (reach 2), underground pairs and inserter pickups/drops. Before: 2 dead
  chains in this plan; after: none (only the science output ends in its chest).
- `realBlueprintsPutEveryMaterialOnAtMostOneBelt` passes for advanced-circuit,
  electronic-circuit, logistic-science-pack, processing-unit, utility-science-pack
  and lubricant, so no fixture has two belt sources per material.

## Decisions

- The foundry's direct feed stays out of scope: `arrange` requires equal footprints
  in a band, and mixing a 3×3 with a 5×5 is a layout change of its own. The user is
  asked whether they want it (todo: their answer).
- `Electromagnetic-plant`/`foundry` productivity (0.5) explains the 1:1 cable→EC
  band; the ratio rule itself was already right.

## Attempts and outcomes

- CLI first run straight from `build/classes/kotlin/jvm/main` (no Gradle) to make
  progress while the build was broken.
- Gradle failed four times on `:buildSrc:compileKotlin` / `:buildSrc:jar`
  (incremental-cache `NoSuchFileException`, "cannot read directory", "failed to
  clean up output files"). A source `touch` produced no rebuild, ruling a host
  `--continuous` build out. Cause: a Kotlin compile daemon from 20:02 outlived its
  Gradle daemon and raced with the cache directory. Cure: `./gradlew --stop`,
  `pkill -f '[K]otlinCompileDaemon'`, `rm -rf buildSrc/build`, retry (the first
  retry then hit the known stale-script-classpath `Unresolved reference 'de'`; the
  next run succeeded). Promoted to `knowledge/pitfalls.md`.

## Failed approaches (do not repeat without new evidence)

- Do not `rm -rf buildSrc/build/kotlin` while a Kotlin daemon from an earlier run
  may be alive.
- Reading belt connectivity from the ASCII layout alone is unreliable; use the JSON
  entity list (areas, directions, loader width, long-inserter reach).

## Open questions

- Answered: the foundry stays belt-fed for now. The user agreed mixed-footprint
  bands are not straightforward and gave the geometry: since the smaller machine
  feeds the bigger one it needs no output belt at all, and if it does need belt
  access it can stand in the middle with long inserters reaching both lanes.
  Recorded in `knowledge/decisions.md` for the future mixed-footprint band work.

## Remaining tasks

- (none) — mixed-footprint bands / direct feed into the foundry are a future task,
  not a defect of this fix.

## Tests and verification

- `aBeltedAllocationCoversThePlansRateNotAWholeConsumerMachine` (commonTest) — the
  rate rule and no second inserter row.
- `theReportedLogisticSciencePackUrlBuildsNoRedundantFactory` +
  `realBlueprintsPutEveryMaterialOnAtMostOneBelt` + `assertOneBeltSourcePerMaterial`
  (jvmTest) — machine counts and the single-belt-source invariant.
- `./gradlew spotlessApply check` green (jvmTest + jsBrowserTest).
- CLI rerun of the reported URL: `--json` counts above, `--layout` fully connected.

## Next action

- Complete the task (the foundry-band question is answered; docs committed).

## Completion report

- Outcome: belt-fed allocations are sized by the plan's rate demand; the reported
  plan loses the two dangling factories and four of five gear machines.
- Verification: unit + integration tests above, `./gradlew spotlessApply check`,
  CLI `--json`/`--layout` on the reported URL, belt tracer with no dead chain.
- Lessons: `producerOf` allows one belt source per material, so any redundant
  belt-fed allocation is a dangling belt; size belt rows by the plan's rate, never
  by whole consumer machines. Promoted the Gradle/Kotlin-daemon cache trap to
  `knowledge/pitfalls.md` and the rule to `knowledge/decisions.md`.
- Follow-up: mixed-footprint bands (so a gear machine can insert straight into the
  foundry) — the user described the geometry but agreed it is a separate change.
