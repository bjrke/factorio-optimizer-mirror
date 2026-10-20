# Conventions

How work is done in this repository. These rules are detailed enough that they do
not belong in the always-loaded `AGENTS.md`, but they are binding when they apply.
Read this file before writing or changing tests, and when a workflow question comes
up.

## Code size and file layout

- Detekt enforces the quality gates and is part of `./gradlew check`. The plain
  `detekt` task analyses all of `src` without type resolution; `check` additionally
  runs the type-resolved `detektMainJvm`/`detektTestJvm` (detekt's KMP support
  registers them but does not wire them into `check`, so `build.gradle.kts` does).
  `config/detekt/detekt.yml` raises `LargeClass.allowedLines` to **500** to keep a
  class within this project's budget. Detekt has no per-file line rule — `LargeClass`
  counts a class, so keep files small by responsibility rather than chasing a line
  count. `MagicNumber` is deliberately disabled: tile offsets, degrees, game data and
  test expectations read better in place; only opaque algorithm limits get a named
  constant. `ReturnCount` ignores guard clauses, a loop may use up to three
  `break`/`continue` statements, and destructuring may bind four names. Import the
  project's own packages with a wildcard (`common.model.*`, `business.*`, the
  fixtures, `de.bjrke.test.*`, `kotlin.test.*`), and the JSON code may use
  `kotlinx.serialization.json.*`; spell other third-party imports out.
  Current findings live in the committed `detekt-baseline.xml` (plain task) and
  `detekt-baseline-main.xml` (typed main task); a new finding fails the build. The
  typed test task is clean, so its `detekt-baseline-test.xml` is gone. After the
  phase-A cleanup (see `decisions.md`) the remaining entries are structural only:
  cyclomatic complexity, long methods/parameter lists, return/loop counts and
  too-many-functions classes in the router, seam, optimizer and parser layers.
  Regenerate with `./gradlew detektBaseline` / `detektBaselineMainJvm`; run
  `detektBaselineTestJvm` too if a test finding ever returns.
- `TooManyFunctions` allows 11 functions per file/class (Detekt default
  `allowedFunctionsPerFile`/`allowedFunctionsPerClass`). Extracting a long
  method's helpers into one file can replace its finding with a file-level one:
  split by responsibility into separate files or classes instead of collecting
  a dozen writers in one file.
- Split by responsibility, not by line count. The layout shows the target shape:
  `LayoutBoard` owns the mutable grid/placement kernel, `LayoutRouter` the A*,
  `UndergroundStretcher` the run growth, and `BlueprintLayout` the ordered
  pipeline. Components take the board (or plain data) instead of sharing a second
  copy of the state.
- Group test suites by scenario and keep shared fixtures in their own file. Split
  an over-long suite by scenario; when the resulting suites use **disjoint**
  fixtures, leave each suite's `private` helpers and values in place rather than
  forcing a shared file (`OptimizerTest` → `OptimizerTest` + `OptimizerSurfaceTest`).
  When they share helpers, export them instead: top-level `internal` values and
  functions in a `fixtures/` subpackage (`LogisticsFixtures.kt` holds
  `testLogistics`, `TOLERANCE`, `BeltCase` and `calculator`), or an abstract base
  class for helpers that capture per-test callbacks
  (`OptimizationResultTestBase`). A base class of a public test class must itself be
  public, or Kotlin reports "'public' subclass exposes its 'internal' supertype".
  Prove a split moved no test by diffing the sorted `fun <name>()` list before and
  after (`grep -oP 'fun \K[a-zA-Z]+(?=\()' | sort`); keep the suite's original name
  on one part.
  A fixtures file in the **same package** must live in a subpackage (e.g.
  `common/business/fixtures/`): an `internal` top-level fixture with the same name
  as another suite's `private` top-level value is a Kotlin `REDECLARATION`, and
  Kotlin does not allow star-importing an `object`. Use `import …fixtures.*`.
- Prefer data that can be tested alone: `LayoutCompaction` already takes a
  `List<LayoutEntity>` and `LayoutRouter` only reads the board, so neither needs a
  full `BlueprintPlan` to test.
- Test-only code lives in the test source set, not in `commonMain`. A stage's test
  accessors go in a `fixtures/` file built on a production entry point: the layout
  suites read entities and failures back through `blueprintLayoutDump` (the CLI's
  `--layout` also uses it), so no production type had to widen its visibility
  (`RowLayout` stays `private`) or keep a wrapper only tests call.

## Behavior-preserving refactors

- Before splitting a layout stage, pin its output with a characterization test.
  `BlueprintLayoutSnapshotTest` hashes the canonical entity/wire list (each
  `BlueprintEntity.toString()` plus the wires, FNV-1a) for the base, chest, fluid
  and band plans. Prove the split is neutral by running the same test in a clean
  worktree at the pre-change commit (`git worktree add /tmp/opencode/<name> HEAD`),
  copying the test in and comparing: identical hashes mean unchanged geometry and
  entity order. Remove the worktree when done.
- Keep the ordered pipeline explicit. Stages are classes that take the board (or
  plain data) and the shared state they own in their constructor; there is no
  stage interface and no DI container, because the `build()` order is
  load-bearing. A stage owns its state and exposes it to later stages
  (`RowPlacer.rows`, `RowPlacer.placedRows`). State that several stages must mutate goes
  in one small holder passed to each (`ImportExtension`), not a lambda or a
  copied value; a scalar several stages update (`poleWestBound`) is a property of
  that holder, not a `var` on `RowLayout`.

## Testing

- Every behavior change or new logic gets tests **without being asked**, in the
  matching source set (`commonMain`/`jsMain`/`jvmMain` →
  `commonTest`/`jsTest`/`jvmTest`); a missing test means the change is incomplete.
  Exception: pure formatting/docs. Use `kotlin.test`.
- Floating-point `assertEquals` uses the shared `TOLERANCE` (`1e-9`) from
  `de.bjrke.test`, not a per-assert literal. Keep a coarser local delta only when
  the expectation is deliberately rounded or measured: display/UI pixels, a value
  already rounded to three decimals, or the URL round-trip's 12-digit precision.
- Multiple independent assertions MUST use the `de.bjrke.test` helpers —
  `assertAll`, and `assertTable`/`assertTableOf` for table-driven cases.
  Descriptive names stating expected behavior (e.g.
  `vulcanusSteelUsesLavaNotChestOre`).
- Never express a collection check with `assertTrue`; it hides the failing values:
  - membership → `assertContains(collection, element, message)` (works for
    `Iterable`, `Map` keys, arrays, sequences, strings and ranges). For strings the
    message is the fourth parameter, so pass it by name:
    `assertContains(text, part, message = text)`.
  - absence → `assertContainsNot(collection, element, message)` from
    `de.bjrke.test` (`kotlin.test` has no `assertNotContains`).
  - emptiness → `assertEquals(emptyList(), actual)`, `emptySet()`/`emptyMap()`,
    `assertEquals("", string)`, `assertContentEquals(emptyArray(), array)`, or
    `assertEquals(emptyList(), sequence.toList())`. Never `assertTrue(actual.isEmpty())`.
- `assertTrue`/`assertFalse` are only for genuine predicates (comparisons, flags,
  `any`/`none` domain rules); use `assertIs`, `assertNotNull`, `assertNull`,
  `assertEquals` when they express the check.
- `assertAll` is only for independent assertions about one subject. Repeated
  assertions over cases MUST use `assertTable`/`assertTableOf` with the case as the
  element (`String`, `Pair`, `Triple`, local data class). `assertTable` labels each
  failure with `case: <value>`, so no manual counting is needed; do not collect case
  lambdas in `assertAll`.
- Do not append the actual value's `.toString()` to a message; equality assertions
  already print it. Keep only messages that add context.
- `commonTest` runs on JVM **and** JS: keep it platform-neutral; target specifics
  go in `src/jvmTest` / `src/jsTest`.
- Run Gradle only when the change reaches the build. `./gradlew check` runs
  `spotlessCheck`, Detekt, `jvmTest` and `jsBrowserTest` (headless Chrome), so it is
  needed only when the branch touches `src/**` (code or resources),
  `build.gradle.kts`, `buildSrc/**`, `gradle/**` or `settings.gradle.kts`: iterate
  with `./gradlew jvmTest`, finish with `./gradlew spotlessApply check` — a single
  invocation, one Gradle process.
- A docs-only or tooling-only branch runs **no** Gradle task: `knowledge/**`,
  `AGENTS.md` and other `*.md` are not build inputs, and `.opencode/**` is agent
  tooling. `spotlessApply` would be a no-op there anyway. A branch that mixes code
  and docs follows the code rule.
- Build logic (`buildSrc/**`) has **no** test suite; the root `check` drives buildSrc
  only up to `jar`, so a buildSrc test would not run from `check` — wire it in
  separately (`./gradlew -p buildSrc test`) if one is ever added. Such tests are plain
  `kotlin.test` — the `de.bjrke.test` helpers are app-only. `tasks.test {
  useJUnitPlatform() }` makes `kotlin("test")` resolve to `kotlin-test-junit5`,
  which already brings `junit-jupiter-engine`; never declare the engine explicitly.
- **Never test agent tooling from the app suites.** Everything outside the
  application — `AGENTS.md`, `knowledge/`, `.opencode/`, `tmp/` — is not part of
  the build; `./gradlew check` covers the app only. Plugins can be syntax-checked
  with `node --check`.

## Component tests (jsTest)

- React components are tested in `src/jsTest` with
  [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/),
  running in the real headless-Chrome karma browser used by `jsBrowserTest`.
- Facade + `ComponentTest` base live in
  `src/jsTest/kotlin/de/bjrke/test/ReactTestingLibrary.kt`: `render(...)`, `screen`,
  `fireEvent`, `within(...)`, `changeTo(...)` / `key(...)`, per-test
  `IS_REACT_ACT_ENVIRONMENT` setup + RTL `cleanup()`. Extend `ComponentTest`.
- Inner component `FC`s are `internal` so tests render them via
  `component.create { ... }`, not the `url...` wrappers (which drag in
  router/recipe context).
- npm test deps: `implementation(npm("<pkg>", "<version>"))` in `jsTest`, refresh
  with `./gradlew kotlinUpgradeYarnLock`; run
  `./gradlew jsBrowserTest --tests "*MaterialSelectorTest*"`.

## Git

- Commit after the work is done (no explicit request needed). Headline ≤ 50 chars;
  body wrapped at 72 chars, explaining why and recording findings that do not
  belong in `knowledge/`. No `!<MR>` suffix: GitLab appends the MR number when it
  merges.
- **A squash request means exactly one thing**: one commit on top of the current
  upstream, with a rewritten message. Choose the upstream that is ahead:
  `base=main; git merge-base --is-ancestor main origin/main && base=origin/main`
  — rebase the branch onto `$base`, then fold the branch's own commits with
  `git reset --soft "$base"` plus a single `git commit`. The squash commit must
  contain only the branch's changes; commits already in `$base` are never
  re-added, and nothing is pushed. Do nothing else — no content edits, no extra
  commits, no rebase past `$base`.
- Write the squash message from the squashed content as the source of truth, not
  from the work log: state the branch's final state and drop anything that only
  applied to an intermediate state (superseded wording, abandoned attempts).

## Knowledge promotion

Promote a finding into `knowledge/` only when it is reusable, stable and verified:

- reusable across changes, likely to prevent repeated investigation, a stable
  architectural/behavioral fact, a verified convention, or a consequential decision.
- Put per-change detail in the commit message, not in `knowledge/`.
- Mark uncertain items as hypotheses until verified. Cite the evidence (file path,
  test name, command) where practical.
- Prefer updating the existing authoritative file over adding a new one; check for
  duplication and contradictions first; remove guidance that is demonstrably
  outdated.
- Keep `knowledge/index.md` a short navigation aid — it should help decide what to
  load, not require loading everything.
