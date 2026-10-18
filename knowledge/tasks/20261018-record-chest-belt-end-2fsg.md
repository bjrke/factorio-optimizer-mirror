---
task: 20261018-record-chest-belt-end-2fsg
title: "Record the chest belt-end marker knowledge"
created: 2026-10-18
updated: 2026-10-18
status: completed
branch: bjrke/belt-end-knowledge
issue:
completed: 2026-10-18
---

## Objective

Capture what the session that added selectable solid-belt end markers ("chest
connectors") learned, so a fresh session reads the design, the blueprint JSON
shape and the traps instead of replaying the conversation. The feature was built
before `knowledge/` existed and is already merged into `main` as `9560a5d`
(`!145`), wire fix included. This task adds documentation only; it changes no
application code.

Follow-up (task reopened): the session that recorded this ran `./gradlew check` on
the docs-only branch "to be safe" — pure waste, because Markdown is not a build
input. Make the Gradle rules in `AGENTS.md` and `knowledge/conventions.md`
conditional on what the branch actually changes, so docs-only and tooling-only
branches run no Gradle task.

## Acceptance criteria

- [x] Task checkpoint written from the session's facts.
- [x] Reusable findings promoted into `knowledge/decisions.md`,
      `knowledge/pitfalls.md` and `knowledge/architecture.md`.
- [x] Findings re-verified against the merged code on `main`.
- [x] Application suites still pass (`./gradlew check`), so the docs change is
      inert.
- [x] `AGENTS.md` and `knowledge/conventions.md` say when Gradle is needed
      (`src/**`, build scripts) and when it is not (docs, `.opencode/**`).
- [x] The `check`/`spotlessApply` descriptions match the real task graph and
      Spotless targets.
- [x] Finish with `./gradlew spotlessApply check` — one invocation, one Gradle
      process — and use `src/**` as the change filter, not `src/**/*.kt`.
- [x] Record the squash expectation in `knowledge/conventions.md` (and the
      `AGENTS.md` pointer): one commit on the current upstream, message rewritten
      from the squashed content, no `!<MR>`, nothing else.

## Current state

Merged on `main`. `src/commonMain/.../model/BeltEnd.kt` holds the six markers;
`.../model/Selection.kt` lost the `CHEATS` flag and threads a `beltEnd`; the
connector methods live in `.../business/BlueprintLayout.kt`; the JSON fields in
`.../model/Blueprint.kt`; the icon radio in
`src/jsMain/.../js/component/LogisticsSelector.kt`; the CLI flag in
`src/jvmMain/.../jvm/CliOptimizer.kt`. `knowledge/` already had the task/knowledge
system (added by `f13fc06`, `!147`); this task is the first application feature
recorded in it.

## Verified findings

Re-read on `main` (`9560a5d`), not copied from the summary:

- `BeltEnd` (`model/BeltEnd.kt`) has six values `(code, identifier)`:
  `constant-combinator` (`null`, default), `infinity-chest` (`X`), `buffer-chest`
  (`H`), `storage-chest` (`G`), `wooden-chest` (`W`), `steel-chest` (`D`).
  `allowedOn(surface)` refuses every chest on `Surface.SPACE`.
- `SelectionFlag` now has only `SPACE_AGE`/`FULL_BELT`; `selectionString(...,
  beltEnd)` appends `beltEnd.code`, and `BeltEnd.fromSelection(selection,
  surface)` clamps a chest code on a space platform to the label
  (`model/Selection.kt`).
- The solid connector is parameterised by `connectorWidth` (3 for the label or
  the infinity chest, 5 for a feeding chest) and `connectorLeft` (2 for the
  infinity chest, else 0) in `business/BlueprintLayout.kt`.
- Shapes (`placeSolidConnector`, `placeChestConnector`):
  - label: `UG-in · constant-combinator · UG-out`, belt tunnels under the label;
  - infinity chest: chest+loader for an input, loader+chest for the output;
  - chest: `UG-in(left) · inserter(left+1) · chest(left+2) · inserter(left+3) ·
    UG-out(left+4)`, belt tunnel span 4 (within every belt tier's range).
- Chest settings match the pasted blueprints: buffer input
  `RequestFilter(item, 100, trash=true)` with inserters `EAST` and `WEST(mirror)`;
  buffer output `RequestFilter(item, 200, trash=true)` with
  `circuitConditionEnabled=false` and both inserters facing the chest carrying
  `CircuitCondition(item, "<", 200)`; storage uses count 1; wooden/steel carry
  `bar=4` with both inserters `WEST`. `import_from` is deliberately omitted (a
  space-platform field).
- The output inserters read the chest through their circuit condition, so
  `chestWires()` wires each to the chest in the blueprint's top-level `wires`
  array (`BlueprintWire(a, 1, b, 1)`), exactly as pole wires are stored. Input,
  wooden/steel and infinity markers stay unwired. Verified by generating and
  decoding: `wires: [[29,1,31,1],[31,1,30,1]]` for `buffer-chest`.
- Chest-connector inserters are powered: `poleWestBound` is *not* raised for a
  chest `beltEnd` (`importSource`, `trimImportPrefixes`, `fluidMarker` and the
  compact shift all guard on `!beltEnd.isChest`), so the pole pass may enter the
  import extension.
- `validateLayout` accepts a `ChestLikeEntity` as an inserter source/drop;
  `LayoutCompaction.shifted` moves a `ChestEntity` with its corridor;
  `isConnectorTunnel` skips a chest tunnel by detecting the adjacent inserter.
- The real task graph (`./gradlew check --dry-run`): `check` → `spotlessCheck`
  (`spotlessKotlinCheck` + `spotlessKotlinGradleCheck`) and `allTests` →
  `jvmTest` + `jsBrowserTest`. So `jsBrowserTest` is part of a plain local `check`
  (not a CI-only addition), and the change filter is all of `src/**` plus the
  build scripts (Spotless's own narrower target, `src/**/*.kt`, is an
  implementation detail, not the trigger) — the previous `AGENTS.md` wording
  ("all tests", "`build.gradle.kts` only", "CI adds `jsBrowserTest`") was
  inaccurate.
- `spotlessApply` and `check` are separate tasks, so `./gradlew spotlessApply
  check` runs both in one Gradle process instead of two (`spotlessCheck` is
  already part of `check`).
- A local branch has no MR number to put in a commit: `git for-each-ref
  refs/merge-requests` is empty and `bjrke/belt-end-knowledge` has no upstream, so
  an agent-made commit must not carry `!<MR>` — GitLab appends it when it squashes
  on merge.

## Hypotheses / unverified

- none

## Decisions

- Six markers, not three: the infinity chest *is* the old cheats mode, so its `X`
  code and the `--cheats` CLI alias stay for compatibility. Promoted to
  `knowledge/decisions.md`.
- Full fidelity to the pasted blueprints — `mirror`, `bar`, `request_filters` and
  the circuit `control_behavior`; `import_from` omitted. Promoted to
  `knowledge/decisions.md`.
- Chests need ground: the UI disables them, and both the URL and CLI entry points
  clamp a chest choice on the space platform. Promoted to
  `knowledge/decisions.md`.
- Gradle is conditional on the change set, not on "finishing a task": code,
  resources and build scripts get `jvmTest` → `spotlessApply check` (one
  invocation); docs and `.opencode/` tooling get none. The filter is `src/**`
  plus the build scripts, not `src/**/*.kt`. Recorded in `AGENTS.md` +
  `knowledge/conventions.md`. The waste happened inside this task, so it was
  reopened instead of starting a new one.
- A squash is a local, message-only operation: one commit on the current
  upstream, its message rewritten from the squashed content with superseded
  statements dropped; GitLab adds the MR number and squashes on merge. Recorded
  in `knowledge/conventions.md` (Git section) with a pointer from `AGENTS.md`.

## Attempts and outcomes

- Modelled the connector as one `placeSolidConnector` switch keyed on `BeltEnd`,
  reusing the existing marker and infinity-chest geometry; the chest case is the
  new five-tile shape.
- Derived the circuit wires at build time from the final `placed` list instead of
  recording them at placement.

## Failed approaches (do not repeat without new evidence)

- Recording a wire as a pair of `LayoutEntity` instances or `placed` indices at
  placement time: `trimImportPrefixes` re-places the import connector and
  compaction `copy()`s entities, so identity and indices do not survive. Derive
  from the final `placed` list instead.
- Trusting `poleWestBound` unconditionally: it deliberately excludes the import
  extension, which would leave a chest marker's inserters unpowered. Skip raising
  it when `beltEnd.isChest`.
- Amending a commit that was already pushed. The branch had an upstream
  (`origin/bjrke/belt-end-markers`), so `git commit --amend` diverged it; the fix
  was rebuilt as a new commit on top of the published tip.

## Open questions

- none

## Remaining tasks

- [x] Re-verify the merged code on `main`.
- [x] Promote reusable findings into `knowledge/`.
- [x] Run `./gradlew check` on this docs-only branch — unnecessary; that is the
      trigger for this follow-up.
- [x] Make the Gradle rules conditional (`AGENTS.md`,
      `knowledge/conventions.md`).
- [x] Verify the real task graph and Spotless targets (see Verified findings).
- [x] Refine: one-invocation `./gradlew spotlessApply check` and the `src/**`
      filter (user feedback).
- [x] Record the squash expectation in `knowledge/conventions.md`; re-squash the
      branch into one commit (user feedback).

## Tests and verification

- Rule change only: verified by reading the dry-run task graph
  (`./gradlew check --dry-run`) and the `spotless { ... }` block in
  `build.gradle.kts`. No Gradle task was re-run, on purpose — this branch changes
  no build input, which is exactly the rule being fixed.
- The one-invocation form `./gradlew spotlessApply check` is plain Gradle task
  chaining (both are root tasks; `check` depends on `spotlessCheck`), so it needs
  no new task or config.
- The squash expectation is documentation only, verified against the live repo:
  `git for-each-ref refs/merge-requests` empty, no upstream on
  `bjrke/belt-end-knowledge`, and the squash done with
  `git reset --soft origin/main` + one commit. No Gradle task run.
- `./gradlew check` on this docs-only branch → `BUILD SUCCESSFUL`; every app task
  is `UP-TO-DATE` (the change touches no `src/` file, so it is inert).
- On the feature branch: `./gradlew check` and
  `./gradlew jsBrowserDistribution` passed; `LogisticsSelectorTest` ran 15/15 in
  real headless Chrome.
- Tests merged on `main`: `BlueprintLayoutTest` (`eachFilteredChestMarkerKeepsItsRequest`,
  `plainChestMarkersAreBarred`, `aChestMarkerFeedsTheBeltOutOfTheChest`,
  `theTargetOutputUsesTheChestOutputVariant`,
  `fluidImportsKeepTheCombinatorLabelEvenWithAChestMarker`,
  `theChestOutputWiresBothInsertersToTheChest`, `theChestInputNeedsNoCircuitWire`,
  `plainAndCheatMarkersNeedNoCircuitWire`), `BlueprintJsonTest`
  (`circuitWiresSerializeAsFlatQuadruples`, `aBlueprintWithoutWiresOmitsThem`, …),
  `SelectionTest` (`theBeltEndSelectionRoundTrips`,
  `aChestMarkerFallsBackToTheLabelInSpace`,
  `theOldCheatCodeStillSelectsTheInfinityChest`), `CliOptimizerTest`
  (`beltEndIsParsedByIdentifierOrCode`), `LogisticsSelectorTest`
  (`selectingABeltEndReportsIt`, `chestBeltEndsAreDisabledInSpace`) and
  `BlueprintIntegrationTest` (chest no-overlap / connected-underground / power
  tables plus `theRealChestOutputWiresItsInsertersToTheChest`).
- A generated chest blueprint round-trips through the bundled decoder
  (`.opencode/skills/factorio-blueprints/scripts/decode.py`).

## Next action

- none — rules updated; commit and complete the task.

## Completion report

- Outcome: the belt-end marker feature is documented in the knowledge base — one
  task file plus promoted entries in `decisions.md`, `pitfalls.md` and
  `architecture.md`. No application code changed. Follow-up: the Gradle rules in
  `AGENTS.md` and `knowledge/conventions.md` are now conditional on the change
  set, so a docs-only branch runs no Gradle task; a code branch finishes with the
  one-invocation `./gradlew spotlessApply check` and uses `src/**` as the filter.
  The squash expectation (one commit, message from the squashed content, no
  `!<MR>`, nothing else) is recorded in `knowledge/conventions.md`.
- Verification: findings re-read from the merged `main` (`9560a5d`) and the test
  suite is listed above; the `check` task graph and Spotless targets were read
  from `./gradlew check --dry-run` and `build.gradle.kts`, and the squash facts
  from `git for-each-ref` / the absence of an upstream. No Gradle task was run
  for the rule change itself.
- Lessons: `knowledge/` is for reusable facts; the connector geometry and the
  circuit-`wires` requirement are the two facts most likely to prevent a repeat
  investigation. Amending a published commit is a trap (see failed approaches).
  Running `./gradlew check` on a Markdown-only branch proves nothing, and a stated
  expectation that is not written into `knowledge/` is not recorded — both rules
  now say so.
- Follow-up: none required.
